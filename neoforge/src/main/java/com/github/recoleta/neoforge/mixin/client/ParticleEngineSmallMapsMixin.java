package com.github.recoleta.neoforge.mixin.client;

import com.google.common.collect.Maps;
import net.minecraft.client.particle.ParticleEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.HashMap;

/**
 * Right-sizes the two long-lived {@link HashMap} fields that
 * {@link ParticleEngine} initialises before populating in
 * {@code registerProviders()} and during sprite set loading.
 *
 * <p>Vanilla 1.20.1 registers ~50 particle types and ~150 sprite sets.
 * Both maps start at the JDK default of 16 buckets and resize three to
 * four times during initialisation, churning intermediate
 * {@code HashMap.Node[]} arrays in the young generation. Modded
 * profiles regularly push 200+ providers, doubling that resize cost.</p>
 *
 * <p>The redirect approach is used (rather than swapping the field
 * after the constructor returns) because vanilla's
 * {@code ParticleEngine.<init>} calls {@code registerProviders()}
 * before returning &mdash; replacing the field at {@code RETURN}
 * would silently discard every entry that the constructor just
 * inserted.</p>
 */
@Mixin(ParticleEngine.class)
public abstract class ParticleEngineSmallMapsMixin {

    /**
     * Catches the field initialiser
     * {@code providers = new java.util.HashMap<>()} and supplies a
     * 64-slot variant.
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
    private <K, V> HashMap<K, V> recoleta$providersMap() {
        return new HashMap<>(64);
    }

    /**
     * Catches the field initialiser
     * {@code spriteSets = Maps.newHashMap()} and supplies a 128-slot
     * variant.
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
    private <K, V> HashMap<K, V> recoleta$spriteSetsMap() {
        return new HashMap<>(128);
    }
}
