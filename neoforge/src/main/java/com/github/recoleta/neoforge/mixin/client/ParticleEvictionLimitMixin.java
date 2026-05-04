package com.github.recoleta.neoforge.mixin.client;

import com.google.common.collect.EvictingQueue;
import com.github.recoleta.config.MemoryConfig;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.particle.ParticleRenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.Map;
import java.util.Queue;
import java.util.function.Function;

/**
 * Caps the per-render-style particle {@code EvictingQueue} from the
 * vanilla 16384 down to the user-configurable
 * {@link MemoryConfig#PARTICLE_PER_TYPE_CAP}.
 *
 * <p>The vanilla {@link ParticleEngine#tick()} method lazily creates a
 * Guava {@code EvictingQueue.create(16384)} for every render type the
 * client encounters. With ~12 render types in vanilla and ~50 with
 * common mods loaded, that translates into hundreds of thousands of
 * pre-allocated array slots even when no particles are visible.
 * Lowering the cap to {@code 4096} (default) reclaims tens of MB of
 * client heap with no perceptible visual difference outside of
 * worst-case redstone particle storms.</p>
 *
 * <p>The vanilla cap lives in the lambda passed to
 * {@link Map#computeIfAbsent(Object, Function)}. Redirecting the
 * source-visible map call avoids targeting javac's synthetic lambda
 * method directly.</p>
 */
@Mixin(ParticleEngine.class)
public abstract class ParticleEvictionLimitMixin {

    /**
     * Replaces the queue factory passed to {@code computeIfAbsent} with one
     * that uses the configured cap.
     *
     * @param particles      render-layer particle map
     * @param renderType     key being inserted
     * @param vanillaFactory ignored vanilla factory
     * @return existing or newly-created capped particle queue, erased to match
     *         {@link Map#computeIfAbsent(Object, Function)} bytecode
     */
    @Redirect(
            method = "tick",
            at = @At(value = "INVOKE", target = "Ljava/util/Map;computeIfAbsent(Ljava/lang/Object;Ljava/util/function/Function;)Ljava/lang/Object;")
    )
    private Object recoleta$cappedParticleQueue(
            final Map<ParticleRenderType, Queue<Particle>> particles,
            final Object renderType,
            final Function<?, ?> vanillaFactory
    ) {
        final int cap = MemoryConfig.PARTICLE_PER_TYPE_CAP.get();
        return particles.computeIfAbsent((ParticleRenderType) renderType, ignored -> EvictingQueue.create(Math.min(16384, cap)));
    }
}

