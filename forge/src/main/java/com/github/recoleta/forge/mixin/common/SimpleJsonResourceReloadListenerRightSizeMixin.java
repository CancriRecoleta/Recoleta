package com.github.recoleta.forge.mixin.common;

import com.github.recoleta.config.MemoryConfig;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.HashMap;

/**
 * Right-sizes the staging {@link HashMap} that
 * {@link SimpleJsonResourceReloadListener#prepare} builds during
 * datapack reload.
 *
 * <p>Vanilla {@code prepare} writes:</p>
 *
 * <pre>
 *     Map&lt;ResourceLocation, JsonElement&gt; map = new HashMap&lt;&gt;();
 *     scanDirectory(...);
 *     return map;
 * </pre>
 *
 * <p>The default {@code HashMap} starts at 16 buckets, but most
 * concrete listener directories hold hundreds to thousands of files
 * &mdash; recipes (~500-2000), advancements (~150-500), loot tables
 * (~200), per-namespace tags. Inserting a thousand entries through a
 * 16-slot table triggers seven successive resize copies, each of
 * which reallocates a new {@code HashMap.Node[]} pair and rehashes
 * every existing entry. The peak heap during reload therefore sees a
 * burst of intermediate {@code Node[]} arrays in the young generation
 * just to be discarded a few microseconds later.</p>
 *
 * <p>This mixin redirects the {@code new HashMap()} call inside the
 * shared base-class {@code prepare} to use a configured initial
 * capacity. The target receives one allocation right-sized for its
 * eventual content; seven {@code Node[]} arrays per listener vanish
 * from the reload allocation profile.</p>
 *
 * <p>This is a <strong>peak-heap and allocation-rate</strong> win
 * during reload, not a steady-state heap reduction &mdash; once
 * reload completes and the staging map is consumed, both vanilla and
 * the right-sized variant end up with the same final table size.</p>
 *
 * <p>Affected vanilla call sites (each via inheritance through
 * {@code SimpleJsonResourceReloadListener.prepare}):
 * {@code RecipeManager}, {@code ServerAdvancementManager},
 * {@code LootDataManager}, {@code FunctionManager},
 * {@code ResourcePackPredicateManager} and so on.</p>
 */
@Mixin(SimpleJsonResourceReloadListener.class)
public abstract class SimpleJsonResourceReloadListenerRightSizeMixin {

    /**
     * Catches the {@code new HashMap<>()} initialiser inside
     * {@code prepare} and supplies a right-sized variant.
     *
     * @param <K> key type
     * @param <V> value type
     * @return a right-sized {@link HashMap}
     */
    @Redirect(
            method = "prepare(Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/util/profiling/ProfilerFiller;)Ljava/util/Map;",
            at = @At(value = "NEW", target = "java/util/HashMap"),
            require = 0, expect = 1
    )
    private <K, V> HashMap<K, V> recoleta$rightSizeStagingMap() {
        if (!MemoryConfig.getBooleanOrDefault(MemoryConfig.ENABLE_RELOAD_LISTENER_RIGHT_SIZE, true)) {
            return new HashMap<>();
        }
        return new HashMap<>(readStagingCapacity());
    }

    private static int readStagingCapacity() {
        try {
            return MemoryConfig.RELOAD_LISTENER_STAGING_CAPACITY.get();
        } catch (final IllegalStateException ignored) {
            // Reload may run before config is loaded on a cold start.
            return 512;
        }
    }
}
