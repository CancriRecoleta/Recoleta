package com.github.recoleta.mixin.common;

import com.github.recoleta.config.MemoryConfig;
import com.github.recoleta.memory.RecoletaCounters;
import com.google.common.collect.Lists;
import net.minecraft.world.entity.ai.sensing.PiglinSpecificSensor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.ArrayList;

/**
 * Right-sizes the two adult-piglin lists {@link PiglinSpecificSensor}
 * rebuilds on every scan.
 *
 * <p>{@code doTick} seeds two lists with {@code Lists.newArrayList()}
 * (10 slots each) &mdash; one for visible adult piglins / brutes, one for
 * nearby adult piglins &mdash; and stores both as
 * {@code NEAREST_VISIBLE_ADULT_PIGLINS} / {@code NEARBY_ADULT_PIGLINS}
 * brain memory, retained until the next scan. In all but the densest
 * bastion crowds these hold a handful of entries, so the default slack is
 * paid per piglin per scan and held between scans.</p>
 *
 * <p>The single redirect handler covers both {@code newArrayList()} call
 * sites; capacity 8 still absorbs a busy bastion without an early resize.</p>
 */
@Mixin(PiglinSpecificSensor.class)
public abstract class PiglinSpecificSensorSmallListMixin {

    @Redirect(
            method = "doTick(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/LivingEntity;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/google/common/collect/Lists;newArrayList()Ljava/util/ArrayList;"),
            remap = false)
    private ArrayList<Object> recoleta$smallAdultPiglinList() {
        if (!MemoryConfig.cachedAiSensorRightSize()) {
            return Lists.newArrayList();
        }

        RecoletaCounters.AI_SENSOR_LIST_RESIZE.increment();
        return new ArrayList<>(8);
    }
}
