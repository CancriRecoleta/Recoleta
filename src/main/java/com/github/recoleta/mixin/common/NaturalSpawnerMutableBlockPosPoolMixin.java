package com.github.recoleta.mixin.common;

import com.github.recoleta.memory.RecoletaCounters;
import com.github.recoleta.memory.pool.MutableBlockPosPool;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.chunk.ChunkAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayDeque;

/**
 * Reuses the temporary {@code MutableBlockPos} allocated inside
 * {@code NaturalSpawner#spawnCategoryForPosition}.
 *
 * <p>Uses a per-thread {@link ArrayDeque} instead of a single-slot
 * {@code ThreadLocal} so reentrant invocations (mod hooks that spawn
 * additional mobs while a spawn frame is on the stack) cannot lose
 * track of the outer frame's lease.</p>
 */
@Mixin(NaturalSpawner.class)
public abstract class NaturalSpawnerMutableBlockPosPoolMixin {

    /** Per-thread lease stack supporting reentrant spawn calls. */
    private static final ThreadLocal<ArrayDeque<BlockPos.MutableBlockPos>> RECOLETA$LEASES =
            ThreadLocal.withInitial(ArrayDeque::new);

    @Redirect(
            method = "spawnCategoryForPosition(Lnet/minecraft/world/entity/MobCategory;Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/level/chunk/ChunkAccess;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/NaturalSpawner$SpawnPredicate;Lnet/minecraft/world/level/NaturalSpawner$AfterSpawnCallback;)V",
            at = @At(value = "NEW", target = "net/minecraft/core/BlockPos$MutableBlockPos")
    )
    private static BlockPos.MutableBlockPos recoleta$acquireMutablePos() {
        final BlockPos.MutableBlockPos pos = MutableBlockPosPool.acquire();
        RECOLETA$LEASES.get().push(pos);
        RecoletaCounters.SPAWNER_POS_LEASE.increment();
        return pos;
    }

    @Inject(
            method = "spawnCategoryForPosition(Lnet/minecraft/world/entity/MobCategory;Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/level/chunk/ChunkAccess;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/NaturalSpawner$SpawnPredicate;Lnet/minecraft/world/level/NaturalSpawner$AfterSpawnCallback;)V",
            at = @At("RETURN")
    )
    private static void recoleta$releaseMutablePos(final MobCategory category,
                                                    final ServerLevel level,
                                                    final ChunkAccess chunk,
                                                    final BlockPos pos,
                                                    final NaturalSpawner.SpawnPredicate predicate,
                                                    final NaturalSpawner.AfterSpawnCallback callback,
                                                    final CallbackInfo ci) {
        final ArrayDeque<BlockPos.MutableBlockPos> stack = RECOLETA$LEASES.get();
        final BlockPos.MutableBlockPos leased = stack.pollFirst();
        if (leased != null) {
            MutableBlockPosPool.release(leased);
            if (stack.isEmpty()) {
                RECOLETA$LEASES.remove();
            }
        }
    }

    /**
     * Reclaim any lease orphaned by an exceptional unwind whose
     * stack frame did not match a paired RETURN injection. Triggered
     * only when re-entering with a non-empty stack from a different
     * top-level call (the typical happy path leaves the stack empty).
     */
    @Inject(
            method = "spawnCategoryForPosition(Lnet/minecraft/world/entity/MobCategory;Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/level/chunk/ChunkAccess;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/NaturalSpawner$SpawnPredicate;Lnet/minecraft/world/level/NaturalSpawner$AfterSpawnCallback;)V",
            at = @At("HEAD")
    )
    private static void recoleta$reclaimOrphans(final MobCategory category,
                                                 final ServerLevel level,
                                                 final ChunkAccess chunk,
                                                 final BlockPos pos,
                                                 final NaturalSpawner.SpawnPredicate predicate,
                                                 final NaturalSpawner.AfterSpawnCallback callback,
                                                 final CallbackInfo ci) {
        // Nothing to do on entry under normal flow; reclamation runs in
        // the RETURN injection. The HEAD hook exists as a safety net to
        // bound stack growth: if some mod throws past our RETURN we
        // drain anything older than a small reentrancy depth (8) here.
        final ArrayDeque<BlockPos.MutableBlockPos> stack = RECOLETA$LEASES.get();
        while (stack.size() > 8) {
            final BlockPos.MutableBlockPos stale = stack.pollLast();
            if (stale == null) break;
            MutableBlockPosPool.release(stale);
            RecoletaCounters.SPAWNER_POS_RECLAIM.increment();
        }
    }
}
