package com.github.recoleta.mixin.common;

import com.github.recoleta.config.MemoryConfig;
import com.github.recoleta.memory.RecoletaCounters;
import com.google.common.collect.Lists;
import net.minecraft.network.protocol.game.ClientboundLightUpdatePacketData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.ArrayList;

/**
 * Right-sizes the {@code skyUpdates} and {@code blockUpdates} lists built
 * in the server-side
 * {@link ClientboundLightUpdatePacketData#ClientboundLightUpdatePacketData(net.minecraft.world.level.ChunkPos, net.minecraft.world.level.lighting.LevelLightEngine, java.util.BitSet, java.util.BitSet)}
 * constructor.
 *
 * <p>Both lists are seeded with {@code Lists.newArrayList()} (10 slots)
 * and then receive at most one {@code byte[]} per lit section. A column
 * spans roughly a couple dozen sections but only the changed ones are
 * appended, so capacity 8 fits the common case while still absorbing a
 * full-column relight without resizing more than once. One such packet
 * accompanies every chunk that streams to a player.</p>
 *
 * <p>The single redirect handler covers both {@code newArrayList()} call
 * sites in the constructor.</p>
 */
@Mixin(ClientboundLightUpdatePacketData.class)
public abstract class ClientboundLightUpdatePacketDataSmallListMixin {

    @Redirect(
            method = "<init>(Lnet/minecraft/world/level/ChunkPos;Lnet/minecraft/world/level/lighting/LevelLightEngine;Ljava/util/BitSet;Ljava/util/BitSet;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/google/common/collect/Lists;newArrayList()Ljava/util/ArrayList;"),
            remap = false)
    private ArrayList<Object> recoleta$smallUpdateList() {
        if (!MemoryConfig.cachedPacketRightSize()) {
            return Lists.newArrayList();
        }

        RecoletaCounters.PACKET_LIST_RESIZE.increment();
        return new ArrayList<>(8);
    }
}
