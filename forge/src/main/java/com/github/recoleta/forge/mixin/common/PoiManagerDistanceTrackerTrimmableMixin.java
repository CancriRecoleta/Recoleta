package com.github.recoleta.forge.mixin.common;

import com.github.recoleta.memory.SlackTrimmer;
import it.unimi.dsi.fastutil.longs.Long2ByteMap;
import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Registers the {@code levels} {@link Long2ByteOpenHashMap} on
 * {@code PoiManager.DistanceTracker} with {@link SlackTrimmer}.
 *
 * <p>{@code DistanceTracker} maps every nearby POI section position
 * to its distance level (0-7). The map is added to as villagers /
 * iron golems walk into POI range and removed from when distance
 * exceeds 6, so there is genuine churn &mdash; and like every
 * fastutil hash container, the table doubles on growth and never
 * compacts on shrink. After a player flies through a village-dense
 * region, the table stays at its peak size forever.</p>
 *
 * <p>{@code DistanceTracker} is a package-private final inner class
 * so we target it via the {@code targets} string form.</p>
 */
@Mixin(targets = "net.minecraft.world.entity.ai.village.poi.PoiManager$DistanceTracker")
public abstract class PoiManagerDistanceTrackerTrimmableMixin {

    @Shadow @Final private Long2ByteMap levels;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void recoleta$registerLevels(final CallbackInfo ci) {
        if (this.levels instanceof Long2ByteOpenHashMap map) {
            SlackTrimmer.trackTrimmable(map, (Long2ByteOpenHashMap m) -> m.trim());
        }
    }
}
