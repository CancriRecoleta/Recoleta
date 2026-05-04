package com.github.recoleta.neoforge.mixin.common;

import com.github.recoleta.config.MemoryConfig;
import com.github.recoleta.memory.RecoletaCounters;
import com.github.recoleta.memory.header.PackedAabb;
import it.unimi.dsi.fastutil.longs.Long2BooleanMap;
import it.unimi.dsi.fastutil.longs.Long2BooleanOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2BooleanMap;
import it.unimi.dsi.fastutil.objects.Object2BooleanFunction;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.PathNavigationRegion;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;


/**
 * Replaces the pathfinding collision-cache key from {@link AABB} object
 * identity/content to a packed {@code long} key.
 */
@Mixin(WalkNodeEvaluator.class)
public abstract class WalkNodeEvaluatorPackedCollisionCacheMixin {

    @Unique
    private final Long2BooleanMap recoleta$packedCollisionCache = new Long2BooleanOpenHashMap(256);

    @Unique private int recoleta$originX;
    @Unique private int recoleta$originY;
    @Unique private int recoleta$originZ;

    @Inject(method = "prepare", at = @At("TAIL"))
    private void recoleta$preparePackedCache(final PathNavigationRegion level, final Mob mob, final CallbackInfo ci) {
        final BlockPos p = mob.blockPosition();
        this.recoleta$originX = p.getX();
        this.recoleta$originY = p.getY();
        this.recoleta$originZ = p.getZ();
        this.recoleta$packedCollisionCache.clear();
    }

    @Inject(method = "done", at = @At("HEAD"))
    private void recoleta$clearPackedCache(final CallbackInfo ci) {
        this.recoleta$packedCollisionCache.clear();
    }

    @Redirect(
            method = "hasCollisions",
            at = @At(value = "INVOKE", target = "Lit/unimi/dsi/fastutil/objects/Object2BooleanMap;computeIfAbsent(Ljava/lang/Object;Lit/unimi/dsi/fastutil/objects/Object2BooleanFunction;)Z", remap = false),
            require = 0,
            expect = 1
    )
    private boolean recoleta$packedCollisionCacheLookup(final Object2BooleanMap<Object> map,
                                                         final Object key,
                                                         final Object2BooleanFunction<Object> predicate) {
        if (!(key instanceof AABB box) || !MemoryConfig.ENABLE_PACKED_AABB_PATH_CACHE.get() || !recoleta$canPack(box)) {
            RecoletaCounters.PATH_PACKED_CACHE_FALLBACK.increment();
            return map.computeIfAbsent(key, predicate);
        }

        final long packed = PackedAabb.pack(box, this.recoleta$originX, this.recoleta$originY, this.recoleta$originZ);
        if (this.recoleta$packedCollisionCache.containsKey(packed)) {
            RecoletaCounters.PATH_PACKED_CACHE_HIT.increment();
            return this.recoleta$packedCollisionCache.get(packed);
        }

        RecoletaCounters.PATH_PACKED_CACHE_MISS.increment();
        final boolean collides = predicate.getBoolean(box);
        this.recoleta$packedCollisionCache.put(packed, collides);
        return collides;
    }

    @Unique
    private boolean recoleta$canPack(final AABB box) {
        final double maxExtentXUnits = 63.0D;
        final double maxExtentYZUnits = 31.0D;
        final double maxDeltaUnits = (double) Short.MAX_VALUE;
        final double dMinX = Math.abs((box.minX - this.recoleta$originX) / PackedAabb.UNIT);
        final double dMinY = Math.abs((box.minY - this.recoleta$originY) / PackedAabb.UNIT);
        final double dMinZ = Math.abs((box.minZ - this.recoleta$originZ) / PackedAabb.UNIT);
        final double sizeX = Math.abs((box.maxX - box.minX) / PackedAabb.UNIT);
        final double sizeY = Math.abs((box.maxY - box.minY) / PackedAabb.UNIT);
        final double sizeZ = Math.abs((box.maxZ - box.minZ) / PackedAabb.UNIT);
        return dMinX <= maxDeltaUnits
                && dMinY <= maxDeltaUnits
                && dMinZ <= maxDeltaUnits
                && sizeX <= maxExtentXUnits
                && sizeY <= maxExtentYZUnits
                && sizeZ <= maxExtentYZUnits;
    }
}

