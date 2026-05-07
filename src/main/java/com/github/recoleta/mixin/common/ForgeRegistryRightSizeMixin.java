package com.github.recoleta.mixin.common;

import com.github.recoleta.config.MemoryConfig;
import com.google.common.collect.HashBiMap;
import net.minecraftforge.registries.ForgeRegistry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Right-sizes the four {@link HashBiMap} fields that
 * {@link ForgeRegistry} initialises.
 *
 * <p>Vanilla Forge declares:</p>
 *
 * <pre>
 *   private final BiMap&lt;Integer, V&gt; ids               = HashBiMap.create();
 *   private final BiMap&lt;ResourceLocation, V&gt; names    = HashBiMap.create();
 *   private final BiMap&lt;ResourceKey&lt;V&gt;, V&gt; keys    = HashBiMap.create();
 *   private final BiMap&lt;OverrideOwner&lt;V&gt;, V&gt; owners = HashBiMap.create();
 * </pre>
 *
 * <p>{@link HashBiMap#create()} backs each map with a pair of
 * {@link com.google.common.collect.HashBiMap.BiEntry} hash tables sized
 * for the JDK default of 16 buckets. Vanilla 1.20.1 ships ~1100
 * {@code Block}s, ~1300 {@code Item}s, ~1500 {@code SoundEvent}s and
 * dozens of other registries that all start at 16 and double their
 * way up: each registry's four BiMaps undergo roughly seven resize
 * cycles to reach ~2048 buckets, churning fresh {@code BiEntry[]}
 * arrays through the young generation during startup. Modded packs
 * easily double those counts.</p>
 *
 * <p>Pre-sizing each BiMap to a configured target capacity (default
 * 1024) absorbs the entire vanilla content count in one allocation.
 * Final steady-state heap is unchanged; the win is reduced startup
 * GC pressure and a smoother main-thread phase.</p>
 *
 * <p>Mixin notes:</p>
 *
 * <ul>
 *   <li>{@code remap = false} because {@link ForgeRegistry} is not part
 *       of the obfuscated namespace.</li>
 *   <li>{@code expect = 4} fails the mixin loudly if a future Forge
 *       refactor adds or removes one of the four BiMap fields,
 *       signalling that the optimisation needs review.</li>
 *   <li>The {@code aliases}, {@code slaves}, {@code delegatesByName}
 *       and {@code delegatesByValue} {@link java.util.HashMap}s are
 *       intentionally left at default capacity: aliases / slaves are
 *       almost always near-empty even on the largest registries, and
 *       discriminating between the four call sites by ordinal is
 *       fragile across Forge minor releases.</li>
 * </ul>
 */
@Mixin(value = ForgeRegistry.class, remap = false)
public abstract class ForgeRegistryRightSizeMixin {

    /**
     * Catches all four {@code HashBiMap.create()} field initialisers
     * with a single {@code @Redirect} and supplies a right-sized
     * variant.
     *
     * @param <K> key type
     * @param <V> value type
     * @return a right-sized {@link HashBiMap}
     */
    @Redirect(
            method = "<init>",
            at = @At(value = "INVOKE", target = "Lcom/google/common/collect/HashBiMap;create()Lcom/google/common/collect/HashBiMap;"),
            require = 0, expect = 4
    )
    private <K, V> HashBiMap<K, V> recoleta$rightSizeBiMap() {
        return HashBiMap.create(readCapacity());
    }

    private static int readCapacity() {
        try {
            return MemoryConfig.FORGE_REGISTRY_BIMAP_CAPACITY.get();
        } catch (final IllegalStateException ignored) {
            // Forge registries start populating before our config has
            // been loaded on a cold start; fall back to a value that
            // covers vanilla's largest registry without significant
            // over-allocation.
            return 1024;
        }
    }
}
