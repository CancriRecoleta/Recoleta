package com.github.recoleta.forge.mixin.common;

import com.github.recoleta.config.MemoryConfig;
import net.minecraft.server.packs.resources.FallbackResourceManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.HashMap;

/**
 * Right-sizes the staging {@link HashMap}s built inside
 * {@link FallbackResourceManager}'s {@code listResources} and
 * {@code listResourceStacks}.
 *
 * <p>Both methods are called once <em>per reload listener
 * directory</em> (recipes, advancements, loot tables, tags, models,
 * blockstates, ...). Each invocation iterates every loaded
 * {@code PackResources} and accumulates matched resources into a
 * staging {@link HashMap} before transferring to the final
 * {@link java.util.TreeMap} returned to callers.</p>
 *
 * <p>The staging maps are short-lived (single-call lifetime), so
 * this is a pure peak-heap and allocation-rate optimisation. Vanilla
 * starts each call at 16 buckets and resizes 5-7 times for the
 * larger directories (recipes can hold 2000+ entries in modded packs,
 * tags/blocks ~500-1500, etc.). Across a single reload that compounds
 * into dozens of resize copies; on a {@code /reload} this happens
 * <em>every time</em>.</p>
 *
 * <p>Three call sites are covered:</p>
 *
 * <ul>
 *   <li>{@code listResources}: two {@code new HashMap<>()} (regular
 *       resources + .mcmeta sidecars).</li>
 *   <li>{@code listResourceStacks}: one {@code Maps.newHashMap()}
 *       for the per-pack stack of resources.</li>
 * </ul>
 *
 * <p>The result {@link java.util.TreeMap} returned to the caller is
 * left alone: {@code TreeMap} has no init-capacity argument and its
 * red-black tree has no resize cost analogous to {@code HashMap}.</p>
 */
@Mixin(FallbackResourceManager.class)
public abstract class FallbackResourceManagerRightSizeMixin {

    /**
     * Catches both {@code new HashMap<>()} sites in
     * {@code listResources}.
     */
    @Redirect(
            method = "listResources",
            at = @At(value = "NEW", target = "java/util/HashMap"),
            require = 0, expect = 2
    )
    private <K, V> HashMap<K, V> recoleta$rightSizeListResources() {
        if (!MemoryConfig.getBooleanOrDefault(MemoryConfig.ENABLE_RELOAD_LISTENER_RIGHT_SIZE, true)) {
            return new HashMap<>();
        }
        return new HashMap<>(readStagingCapacity());
    }

    /**
     * Catches the {@code Maps.newHashMap()} site in
     * {@code listResourceStacks}.
     */
    @Redirect(
            method = "listResourceStacks",
            at = @At(value = "INVOKE", target = "Lcom/google/common/collect/Maps;newHashMap()Ljava/util/HashMap;"),
            require = 0, expect = 1,
            remap = false
    )
    private <K, V> HashMap<K, V> recoleta$rightSizeListResourceStacks() {
        if (!MemoryConfig.getBooleanOrDefault(MemoryConfig.ENABLE_RELOAD_LISTENER_RIGHT_SIZE, true)) {
            return new HashMap<>();
        }
        return new HashMap<>(readStagingCapacity());
    }

    private static int readStagingCapacity() {
        try {
            return MemoryConfig.RELOAD_LISTENER_STAGING_CAPACITY.get();
        } catch (final IllegalStateException ignored) {
            return 512;
        }
    }
}
