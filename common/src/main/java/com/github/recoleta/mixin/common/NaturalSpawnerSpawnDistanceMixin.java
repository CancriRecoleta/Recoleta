package com.github.recoleta.mixin.common;

import com.github.recoleta.config.MemoryConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.phys.Vec3;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

import java.util.Objects;

/**
 * Eliminates temporary allocations in NaturalSpawner's distance gate.
 */
@Mixin(NaturalSpawner.class)
public abstract class NaturalSpawnerSpawnDistanceMixin {

    /**
     * Vanilla-equivalent spawn distance check with an allocation-free fast path.
     *
     * @author recoleta
     * @reason Avoid Vec3/ChunkPos temporary allocations in a hot spawning loop.
     */
    @Overwrite
    private static boolean isRightDistanceToPlayerAndSpawnPoint(final ServerLevel level,
                                                                 final ChunkAccess chunk,
                                                                 final BlockPos.MutableBlockPos pos,
                                                                 final double playerDistanceSqr) {
        if (playerDistanceSqr <= 576.0D) {
            return false;
        }

        if (!MemoryConfig.ENABLE_SPAWNER_DISTANCE_ALLOCATION_PATCH.get()) {
            if (level.getSharedSpawnPos().closerToCenterThan(new Vec3((double) pos.getX() + 0.5D, (double) pos.getY(), (double) pos.getZ() + 0.5D), 24.0D)) {
                return false;
            }
            return Objects.equals(new ChunkPos(pos), chunk.getPos()) || level.isNaturalSpawningAllowed(pos);
        }

        final BlockPos spawn = level.getSharedSpawnPos();
        final double dx = (double) spawn.getX() - (double) pos.getX();
        final double dy = (double) spawn.getY() + 0.5D - (double) pos.getY();
        final double dz = (double) spawn.getZ() - (double) pos.getZ();
        if (dx * dx + dy * dy + dz * dz < 576.0D) {
            return false;
        }

        final long spawnChunk = ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4);
        return spawnChunk == chunk.getPos().toLong() || level.isNaturalSpawningAllowed(pos);
    }
}

