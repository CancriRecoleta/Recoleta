package com.github.recoleta.mixin.common;

import it.unimi.dsi.fastutil.objects.ObjectOpenCustomHashSet;
import net.minecraft.world.ticks.LevelChunkTicks;
import net.minecraft.world.ticks.ScheduledTick;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.PriorityQueue;
import java.util.Queue;
import java.util.Set;

/**
 * Right-sizes the empty tick containers allocated for every full chunk.
 *
 * <p>Each {@link LevelChunkTicks} owns a priority queue and a custom
 * hash set. {@link net.minecraft.world.level.chunk.LevelChunk} creates two
 * fresh empty instances per chunk, one for block ticks and one for fluid ticks.
 * Vanilla's default constructors reserve capacity for far more entries than
 * the common no-scheduled-ticks case needs, so empty chunks retain avoidable
 * backing arrays.</p>
 *
 * <p>This mixin only changes the no-arg constructor path. The constructor used
 * when loading pending ticks from disk is intentionally left alone because it
 * pre-populates {@code ticksPerPosition} from saved data.</p>
 *
 * @param <T> tick payload type
 */
@Mixin(LevelChunkTicks.class)
public abstract class LevelChunkTicksSmallCollectionsMixin<T> {

    @Mutable
    @Final
    @Shadow
    private Queue<ScheduledTick<T>> tickQueue;

    @Mutable
    @Final
    @Shadow
    private Set<ScheduledTick<?>> ticksPerPosition;

    /**
     * Replaces the empty default collections with minimum-capacity variants.
     *
     * @param ci callback info
     */
    @Inject(method = "<init>()V", at = @At("RETURN"))
    private void recoleta$smallEmptyTickCollections(final CallbackInfo ci) {
        this.tickQueue = new PriorityQueue<>(1, ScheduledTick.DRAIN_ORDER);
        this.ticksPerPosition = new ObjectOpenCustomHashSet<>(1, ScheduledTick.UNIQUE_TICK_HASH);
    }
}
