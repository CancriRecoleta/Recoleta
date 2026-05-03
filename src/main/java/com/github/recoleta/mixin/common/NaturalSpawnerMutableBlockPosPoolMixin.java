package com.github.recoleta.mixin.common;

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
 * Reuses the temporary MutableBlockPos allocated by NaturalSpawner's hot path.
 */
@Mixin(NaturalSpawner.class)
public abstract class NaturalSpawnerMutableBlockPosPoolMixin {

    private static final ThreadLocal<BlockPos.MutableBlockPos> RECOLETA$LEASED_POS = new ThreadLocal<>();

    @Redirect(
            method = "spawnCategoryForPosition(Lnet/minecraft/world/entity/MobCategory;Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/level/chunk/ChunkAccess;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/NaturalSpawner$SpawnPredicate;Lnet/minecraft/world/level/NaturalSpawner$AfterSpawnCallback;)V",
            at = @At(value = "NEW", target = "net/minecraft/core/BlockPos$MutableBlockPos")
    )
    private static BlockPos.MutableBlockPos recoleta$acquireMutablePos() {
        final BlockPos.MutableBlockPos pos = MutableBlockPosPool.acquire();
        RECOLETA$LEASED_POS.set(pos);
        return pos;
    }

    @Inject(
            method = "spawnCategoryForPosition(Lnet/minecraft/world/entity/MobCategory;Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/level/chunk/ChunkAccess;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/NaturalSpawner$SpawnPredicate;Lnet/minecraft/world/level/NaturalSpawner$AfterSpawnCallback;)V",
            at = @At("HEAD")
    )
    private static void recoleta$reclaimLeakedPos(final MobCategory category,
                                                   final ServerLevel level,
                                                   final ChunkAccess chunk,
                                                   final BlockPos pos,
                                                   final NaturalSpawner.SpawnPredicate predicate,
                                                   final NaturalSpawner.AfterSpawnCallback callback,
                                                   final CallbackInfo ci) {
        final BlockPos.MutableBlockPos stale = RECOLETA$LEASED_POS.get();
        if (stale != null) {
            RECOLETA$LEASED_POS.remove();
            MutableBlockPosPool.release(stale);
        }
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
        final BlockPos.MutableBlockPos leased = RECOLETA$LEASED_POS.get();
        if (leased != null) {
            RECOLETA$LEASED_POS.remove();
            MutableBlockPosPool.release(leased);
        }
    }
}

