package com.github.recoleta.neoforge.mixin.client;

import com.google.common.collect.Maps;
import net.minecraft.client.sounds.SoundManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.HashMap;

/**
 * Right-sizes the two long-lived {@link HashMap} fields on
 * {@link SoundManager}.
 *
 * <p>Vanilla 1.20.1 ships ~1500 distinct sound entries; mod-heavy
 * packs routinely double that. Both {@code registry}
 * ({@code Maps.newHashMap()}) and {@code soundCache}
 * ({@code new HashMap<>()}) start at the JDK default of 16 buckets
 * and resize roughly seven times during the first reload.</p>
 *
 * <p>This mixin pre-sizes both to 2048 slots, eliminating the
 * intermediate resize churn during reload without changing
 * steady-state heap occupancy. The {@code Preparations} inner class
 * uses its own short-lived maps that are intentionally left alone &mdash;
 * the inner class is not part of {@code @Mixin(SoundManager.class)}'s
 * target.</p>
 */
@Mixin(SoundManager.class)
public abstract class SoundManagerSmallMapsMixin {

    /**
     * Catches {@code registry = Maps.newHashMap()} and supplies a
     * 2048-slot variant.
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
    private <K, V> HashMap<K, V> recoleta$registryMap() {
        return new HashMap<>(2048);
    }

    /**
     * Catches {@code soundCache = new HashMap<>()} and supplies a
     * 2048-slot variant.
     *
     * @param <K> key type
     * @param <V> value type
     * @return a right-sized {@link HashMap}
     */
    @Redirect(
            method = "<init>",
            at = @At(value = "NEW", target = "java/util/HashMap"),
            require = 0, expect = 1
    )
    private <K, V> HashMap<K, V> recoleta$soundCacheMap() {
        return new HashMap<>(2048);
    }
}
