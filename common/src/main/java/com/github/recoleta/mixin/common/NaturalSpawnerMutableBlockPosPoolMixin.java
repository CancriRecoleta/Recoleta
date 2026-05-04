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

/**
 * Reuses the temporary {@code MutableBlockPos} allocated inside
 * {@code NaturalSpawner#spawnCategoryForPosition}.
 *
 * <p>Uses a per-thread single-slot pattern with a depth counter rather
 * than a stack so that:</p>
 *
 * <ul>
 *   <li><b>Reentrant calls</b> (a mod hook spawning another mob during a
 *       spawn frame) get a fresh, unpooled {@code MutableBlockPos} and
 *       cannot alias the outer frame's slot.</li>
 *   <li><b>Exception unwinds</b> past the {@code @Inject(at=RETURN)} hook
 *       only orphan the current outermost slot &mdash; bounded to one
 *       {@code MutableBlockPos} per thread, versus the previous
 *       stack-based design that could orphan up to eight.</li>
 * </ul>
 */
@Mixin(NaturalSpawner.class)
public abstract class NaturalSpawnerMutableBlockPosPoolMixin {

    /** Reentrancy depth on the current thread; non-volatile because state is thread-confined. */
    private static final ThreadLocal<int[]> RECOLETA$DEPTH =
            ThreadLocal.withInitial(() -> new int[1]);

    /** Pooled instance owned by the outermost frame on the current thread. */
    private static final ThreadLocal<BlockPos.MutableBlockPos> RECOLETA$SLOT =
            new ThreadLocal<>();

    @Inject(
            method = "spawnCategoryForPosition(Lnet/minecraft/world/entity/MobCategory;Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/level/chunk/ChunkAccess;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/NaturalSpawner$SpawnPredicate;Lnet/minecraft/world/level/NaturalSpawner$AfterSpawnCallback;)V",
            at = @At("HEAD")
    )
    private static void recoleta$enter(final MobCategory category,
                                        final ServerLevel level,
                                        final ChunkAccess chunk,
                                        final BlockPos pos,
                                        final NaturalSpawner.SpawnPredicate predicate,
                                        final NaturalSpawner.AfterSpawnCallback callback,
                                        final CallbackInfo ci) {
        final int[] depth = RECOLETA$DEPTH.get();
        if (depth[0] == 0) {
            // Top-level entry. A non-null slot here is an orphan from a
            // previous invocation that unwound past the RETURN injection.
            final BlockPos.MutableBlockPos orphan = RECOLETA$SLOT.get();
            if (orphan != null) {
                MutableBlockPosPool.release(orphan);
                RECOLETA$SLOT.remove();
                RecoletaCounters.SPAWNER_POS_RECLAIM.increment();
            }
        }
        depth[0]++;
    }

    @Redirect(
            method = "spawnCategoryForPosition(Lnet/minecraft/world/entity/MobCategory;Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/level/chunk/ChunkAccess;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/NaturalSpawner$SpawnPredicate;Lnet/minecraft/world/level/NaturalSpawner$AfterSpawnCallback;)V",
            at = @At(value = "NEW", target = "net/minecraft/core/BlockPos$MutableBlockPos")
    )
    private static BlockPos.MutableBlockPos recoleta$acquireMutablePos() {
        // Only the outermost frame (depth == 1, set in HEAD just above) is
        // allowed to take the pooled slot. Reentrant frames receive a fresh
        // instance so they cannot alias the slot the outer frame is still
        // mutating.
        if (RECOLETA$DEPTH.get()[0] != 1) {
            return new BlockPos.MutableBlockPos();
        }
        final BlockPos.MutableBlockPos pos = MutableBlockPosPool.acquire();
        RECOLETA$SLOT.set(pos);
        RecoletaCounters.SPAWNER_POS_LEASE.increment();
        return pos;
    }

    @Inject(
            method = "spawnCategoryForPosition(Lnet/minecraft/world/entity/MobCategory;Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/level/chunk/ChunkAccess;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/NaturalSpawner$SpawnPredicate;Lnet/minecraft/world/level/NaturalSpawner$AfterSpawnCallback;)V",
            at = @At("RETURN")
    )
    private static void recoleta$exit(final MobCategory category,
                                       final ServerLevel level,
                                       final ChunkAccess chunk,
                                       final BlockPos pos,
                                       final NaturalSpawner.SpawnPredicate predicate,
                                       final NaturalSpawner.AfterSpawnCallback callback,
                                       final CallbackInfo ci) {
        final int[] depth = RECOLETA$DEPTH.get();
        if (depth[0] <= 0) {
            // Defensive: HEAD never ran for this frame, or an earlier exit
            // already balanced. Don't underflow.
            return;
        }
        depth[0]--;
        if (depth[0] == 0) {
            // Outermost frame exiting normally. Release the pooled slot.
            final BlockPos.MutableBlockPos slot = RECOLETA$SLOT.get();
            if (slot != null) {
                MutableBlockPosPool.release(slot);
                RECOLETA$SLOT.remove();
            }
        }
    }
}
