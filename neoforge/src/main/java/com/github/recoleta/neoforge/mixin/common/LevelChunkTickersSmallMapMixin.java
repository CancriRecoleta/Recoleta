package com.github.recoleta.neoforge.mixin.common;

import com.github.recoleta.memory.header.PackedBlockPosMap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;

/**
 * Stores {@link LevelChunk}'s {@code tickersInLevel} map with packed
 * {@link BlockPos} keys.
 *
 * <p>Vanilla initialises {@code tickersInLevel = Maps.newHashMap()}
 * &mdash; a default-capacity {@link java.util.HashMap} keyed by the
 * 24-byte {@code BlockPos} object. Most chunks have zero or one
 * ticking block entity, so the table itself plus the retained
 * {@code BlockPos} keys are pure overhead.</p>
 *
 * <p>This mixin replaces the field with a {@link PackedBlockPosMap}
 * sized for two entries: the public {@code Map<BlockPos, V>} contract
 * is preserved (vanilla never re-exposes this field outside
 * {@code LevelChunk}), but the keys are stored as the {@code long}
 * returned by {@link BlockPos#asLong()}, eliminating the per-entry
 * {@code BlockPos} retention.</p>
 *
 * <p>Replacement happens at the end of every {@code LevelChunk}
 * constructor instead of redirecting the {@code Maps#newHashMap()}
 * call so it does not depend on field-initializer bytecode placement.</p>
 */
@Mixin(LevelChunk.class)
public abstract class LevelChunkTickersSmallMapMixin {

    @Mutable
    @Final
    @Shadow
    private Map<BlockPos, Object> tickersInLevel;

    /**
     * Swaps the default-capacity {@link java.util.HashMap} for a
     * {@link PackedBlockPosMap} sized for the typical ticker count.
     *
     * @param ci callback info
     */
    @Inject(method = "<init>", at = @At("RETURN"))
    private void recoleta$packTickerMap(final CallbackInfo ci) {
        this.tickersInLevel = new PackedBlockPosMap<>(2);
    }
}
