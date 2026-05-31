package com.github.recoleta.mixin.common;

import com.github.recoleta.config.MemoryConfig;
import com.github.recoleta.memory.RecoletaCounters;
import com.google.common.collect.Lists;
import net.minecraft.world.entity.ai.sensing.SecondaryPoiSensor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.ArrayList;

/**
 * Right-sizes the secondary-job-site list {@link SecondaryPoiSensor}
 * builds each scan.
 *
 * <p>The sensor sweeps a 9&times;5&times;9 block volume around a villager
 * and collects the {@code GlobalPos} of every block matching the
 * profession's secondary POI. It seeds the result with
 * {@code Lists.newArrayList()} (10 slots), but a workstation neighbourhood
 * realistically yields zero to a few matches. The list is then stored as
 * {@code SECONDARY_JOB_SITE} brain memory and kept until the next scan, so
 * the slack is retained per villager.</p>
 *
 * <p>Capacity 4 covers the common case; larger clusters grow on demand.</p>
 */
@Mixin(SecondaryPoiSensor.class)
public abstract class SecondaryPoiSensorSmallListMixin {

    @Redirect(
            method = "doTick(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/npc/Villager;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/google/common/collect/Lists;newArrayList()Ljava/util/ArrayList;"),
            remap = false)
    private ArrayList<Object> recoleta$smallSecondaryPoiList() {
        if (!MemoryConfig.cachedAiSensorRightSize()) {
            return Lists.newArrayList();
        }

        RecoletaCounters.AI_SENSOR_LIST_RESIZE.increment();
        return new ArrayList<>(4);
    }
}
