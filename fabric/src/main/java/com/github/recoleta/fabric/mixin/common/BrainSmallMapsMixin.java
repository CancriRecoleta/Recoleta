package com.github.recoleta.fabric.mixin.common;

import com.google.common.collect.Maps;
import net.minecraft.world.entity.ai.Brain;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.HashMap;
import java.util.LinkedHashMap;

/**
 * Right-sizes the {@link HashMap} / {@link LinkedHashMap} fields that
 * {@link Brain} allocates for every villager, piglin, axolotl and
 * other AI-driven mob.
 *
 * <p>Vanilla initialises {@code memories}, {@code sensors},
 * {@code activityRequirements} and {@code activityMemoriesToEraseWhenStopped}
 * via {@code Maps.newHashMap()} / {@code Maps.newLinkedHashMap()},
 * each of which lazily allocates a 16-bucket backing array on first
 * insertion. Profiling typical villager/piglin {@code Brain}s shows
 * {@code memories} settles around 8-12 entries while {@code sensors}
 * and the two {@code activity*} maps stay around 3-5. With ~200 active
 * AI mobs the always-resident bucket arrays add up to several hundred
 * KB of pure overhead.</p>
 *
 * <p><b>Important:</b> the {@code Brain} constructor body populates
 * {@code memories} and {@code sensors} by iterating its argument
 * collections, so swapping the field with {@code @Inject(at=RETURN)}
 * would silently drop every entry the constructor just inserted &mdash;
 * a {@code getMemory()} on a "registered" key would then throw
 * {@code IllegalStateException}. The redirect approach used here
 * intercepts the {@code Maps.newXxx()} call <i>during</i> field
 * initialisation, before the constructor body runs, so populating
 * proceeds into the right-sized container.</p>
 *
 * <p>The {@code availableBehaviorsByPriority} field is a
 * {@link java.util.TreeMap} (no init-capacity argument); intentionally
 * left untouched.</p>
 */
@Mixin(Brain.class)
public abstract class BrainSmallMapsMixin {

    /**
     * Catches all three {@code Maps.newHashMap()} field initialisers
     * ({@code memories}, {@code activityRequirements},
     * {@code activityMemoriesToEraseWhenStopped}) and supplies an
     * 8-slot variant. Eight slots gives a 6-entry resize threshold
     * which comfortably covers {@code activityRequirements} and
     * {@code activityMemoriesToEraseWhenStopped}; for the larger
     * {@code memories} map a single later resize to 16 is acceptable
     * and still cheaper than starting at 16.
     *
     * @param <K> key type
     * @param <V> value type
     * @return a small {@link HashMap}
     */
    @Redirect(
            method = "<init>*",
            at = @At(value = "INVOKE", target = "Lcom/google/common/collect/Maps;newHashMap()Ljava/util/HashMap;"),
            require = 0, expect = 3,
            remap = false
    )
    private <K, V> HashMap<K, V> recoleta$smallHashMap() {
        return new HashMap<>(8);
    }

    /**
     * Catches the {@code Maps.newLinkedHashMap()} initialiser for
     * {@code sensors} and supplies a 4-slot variant.
     *
     * @param <K> key type
     * @param <V> value type
     * @return a small {@link LinkedHashMap}
     */
    @Redirect(
            method = "<init>*",
            at = @At(value = "INVOKE", target = "Lcom/google/common/collect/Maps;newLinkedHashMap()Ljava/util/LinkedHashMap;"),
            require = 0, expect = 1,
            remap = false
    )
    private <K, V> LinkedHashMap<K, V> recoleta$smallLinkedHashMap() {
        return new LinkedHashMap<>(4);
    }
}
