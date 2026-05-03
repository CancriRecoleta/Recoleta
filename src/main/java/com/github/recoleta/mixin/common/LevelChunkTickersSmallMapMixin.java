package com.github.recoleta.mixin.common;

import com.google.common.collect.Maps;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.chunk.LevelChunk;
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
 * Right-sizes the {@code tickersInLevel} {@link HashMap} on every
 * {@link LevelChunk}.
 *
 * <p>Vanilla initialises {@code tickersInLevel = Maps.newHashMap()}
 * with the JDK default of 16 buckets &mdash; an 80-byte table on
 * 64-bit JVMs &mdash; even though the vast majority of chunks have
 * zero or one ticking block entity. Replacing the call with an
 * initial capacity of {@code 2} reclaims roughly 64 bytes per chunk;
 * across ~1024 loaded chunks that adds up.</p>
 *
 * <p>The replacement happens after constructor field initialisation instead
 * of redirecting the exact {@link Maps#newHashMap()} call. That avoids
 * depending on the compiler's placement of field-initializer bytecode.</p>
 */
@Mixin(LevelChunk.class)
public abstract class LevelChunkTickersSmallMapMixin {

    @Mutable
    @Final
    @Shadow
    private Map<BlockPos, Object> tickersInLevel;

    /**
     * Replaces the default-capacity {@link HashMap} with a 2-slot variant
     * tuned for the typical chunk's ticker count.
     *
     * @param ci callback info
     */
    @Inject(method = "<init>", at = @At("RETURN"))
    private void recoleta$smallTickerMap(final CallbackInfo ci) {
        this.tickersInLevel = new HashMap<>(2);
    }
}

