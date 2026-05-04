package com.github.recoleta.mixin.common;

import com.google.common.collect.Maps;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.HashMap;

/**
 * Right-sizes {@link LivingEntity}'s {@code activeEffects} map.
 *
 * <p>Vanilla initialises {@code activeEffects = Maps.newHashMap()}
 * &mdash; a default-capacity {@link HashMap} keyed by
 * {@code MobEffect}. Every {@code LivingEntity} carries one (mobs,
 * players, projectile owners that derive from {@code LivingEntity})
 * yet the overwhelming majority of instances hold zero or one effect
 * at any moment. With ~1000 active living entities the empty
 * 16-bucket tables waste roughly a quarter of a megabyte of
 * always-resident heap.</p>
 *
 * <p>Pre-sizing to {@code 4} produces a 4-slot table with a 3-entry
 * resize threshold, which covers the typical 0-3 simultaneous
 * effects without resizing while still cutting the per-entity table
 * footprint roughly in half.</p>
 *
 * <p>Field-initialiser redirect (rather than {@code @Inject(at=RETURN)})
 * is used so the small map is in place before any constructor body
 * inserts into it. The same lesson applies here as for
 * {@code BrainSmallMapsMixin}.</p>
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityActiveEffectsSmallMapMixin {

    /**
     * Catches the field initialiser
     * {@code activeEffects = Maps.newHashMap()} and supplies a
     * 4-slot variant.
     *
     * @param <K> key type
     * @param <V> value type
     * @return a right-sized {@link HashMap}
     */
    @Redirect(
            method = "<init>",
            at = @At(value = "INVOKE", target = "Lcom/google/common/collect/Maps;newHashMap()Ljava/util/HashMap;"),
            require = 0, expect = 1,
            remap = false
    )
    private <K, V> HashMap<K, V> recoleta$smallActiveEffectsMap() {
        return new HashMap<>(4);
    }
}
