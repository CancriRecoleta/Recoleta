package com.github.recoleta.neoforge.mixin.common;

import com.github.recoleta.config.MemoryConfig;
import net.minecraft.tags.TagLoader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.HashMap;

/**
 * Right-sizes the two staging {@link HashMap}s built inside
 * {@link TagLoader#load} and {@link TagLoader#build}.
 *
 * <p>Both methods start with {@code Maps.newHashMap()} and populate
 * the result map with one entry per discovered tag. Vanilla 1.20.1
 * ships ~500 tags in the {@code tags/blocks} and {@code tags/items}
 * registries, modpacks routinely double that. The default 16-bucket
 * map resizes 6 times to reach the final 2048-bucket layout, each
 * resize churning a fresh {@code HashMap.Node[]} pair through the
 * young generation.</p>
 *
 * <p>{@code TagLoader} is invoked once per registry per reload, so a
 * vanilla server does ~10 of these operations on startup and again
 * on every {@code /reload}. The reduction in resize churn is
 * therefore multiplicative.</p>
 *
 * <p>Like {@code SimpleJsonResourceReloadListenerRightSizeMixin}, this
 * is a peak-heap and allocation-rate optimisation, not a steady-state
 * heap reduction &mdash; the final table size is determined by tag
 * content count and unchanged.</p>
 */
@Mixin(TagLoader.class)
public abstract class TagLoaderRightSizeMixin {

    /**
     * Catches both {@code Maps.newHashMap()} call sites (one in
     * {@code load}, one in {@code build}) with a wildcard method
     * matcher and supplies a right-sized map.
     *
     * @param <K> key type
     * @param <V> value type
     * @return a right-sized {@link HashMap}
     */
    @Redirect(
            method = "*",
            at = @At(value = "INVOKE", target = "Lcom/google/common/collect/Maps;newHashMap()Ljava/util/HashMap;"),
            require = 0, expect = 2,
            remap = false
    )
    private <K, V> HashMap<K, V> recoleta$rightSizeTagStaging() {
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
