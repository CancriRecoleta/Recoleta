package com.github.recoleta.forge.mixin.client;

import net.minecraft.client.sounds.ChannelAccess;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.client.resources.sounds.SoundInstance;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashMap;
import java.util.Map;

/**
 * Right-sizes the three {@link HashMap} fields on {@link SoundEngine}
 * that drive active-sound bookkeeping.
 *
 * <p>{@code instanceToChannel} is bounded by the OpenAL channel limit
 * (~256). The two {@code Integer}-valued maps ({@code queuedSounds},
 * {@code soundDeleteTime}) typically hold zero to a few dozen entries.
 * Vanilla initialises all three with default-capacity
 * {@code Maps.newHashMap()}.</p>
 *
 * <p>Field replacement at constructor return is safe here:
 * {@code SoundEngine.<init>} only assigns {@code soundManager},
 * {@code options} and {@code soundBuffers} &mdash; it does not put
 * anything into the three maps before returning, so swapping their
 * references leaves no entries behind.</p>
 */
@Mixin(SoundEngine.class)
public abstract class SoundEngineSmallMapsMixin {

    @Mutable
    @Final
    @Shadow
    private Map<SoundInstance, ChannelAccess.ChannelHandle> instanceToChannel;

    @Mutable
    @Final
    @Shadow
    private Map<SoundInstance, Integer> queuedSounds;

    @Mutable
    @Final
    @Shadow
    private Map<SoundInstance, Integer> soundDeleteTime;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void recoleta$smallSoundMaps(final CallbackInfo ci) {
        this.instanceToChannel = new HashMap<>(256);
        this.queuedSounds = new HashMap<>(16);
        this.soundDeleteTime = new HashMap<>(16);
    }
}
