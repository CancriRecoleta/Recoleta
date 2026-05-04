package com.github.recoleta.forge.mixin.common;

import com.github.recoleta.memory.pool.Vec3Pool;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Reuses pooled mutable vectors in WalkNodeEvaluator collision stepping.
 */
@Mixin(WalkNodeEvaluator.class)
public abstract class WalkNodeEvaluatorVec3PoolMixin {

    @Invoker("hasCollisions")
    protected abstract boolean recoleta$invokeHasCollisions(AABB box);

    /**
     * Vanilla-equivalent stepping check implemented with pooled mutable vector math.
     *
     * @author recoleta
     * @reason Avoid transient Vec3 allocations in a hot pathfinding loop.
     */
    @Overwrite
    private boolean canReachWithoutCollision(final Node node) {
        final Mob mob = ((NodeEvaluatorAccessor) (Object) this).recoleta$getMob();
        if (mob == null) {
            return true;
        }

        AABB aabb = mob.getBoundingBox();
        final Vec3Pool.Slot delta = Vec3Pool.acquire();
        try {
            delta.set(
                    (double) node.x - mob.getX() + aabb.getXsize() / 2.0D,
                    (double) node.y - mob.getY() + aabb.getYsize() / 2.0D,
                    (double) node.z - mob.getZ() + aabb.getZsize() / 2.0D
            );

            final int steps = Mth.ceil(Math.sqrt(delta.x * delta.x + delta.y * delta.y + delta.z * delta.z) / aabb.getSize());
            if (steps <= 0) {
                return true;
            }

            final double invSteps = 1.0D / (double) steps;
            delta.x *= invSteps;
            delta.y *= invSteps;
            delta.z *= invSteps;

            for (int j = 1; j <= steps; ++j) {
                aabb = aabb.move(delta.x, delta.y, delta.z);
                if (this.recoleta$invokeHasCollisions(aabb)) {
                    return false;
                }
            }
            return true;
        } finally {
            Vec3Pool.release(delta);
        }
    }
}


