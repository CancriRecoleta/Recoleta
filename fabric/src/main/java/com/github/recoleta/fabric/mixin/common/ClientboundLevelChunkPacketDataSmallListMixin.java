package com.github.recoleta.fabric.mixin.common;

import com.github.recoleta.config.MemoryConfig;
import com.github.recoleta.memory.RecoletaCounters;
import com.google.common.collect.Lists;
import net.minecraft.network.protocol.game.ClientboundLevelChunkPacketData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.ArrayList;

/**
 * Right-sizes the {@code blockEntitiesData} list created in
 * {@link ClientboundLevelChunkPacketData#ClientboundLevelChunkPacketData(net.minecraft.world.level.chunk.LevelChunk)}.
 *
 * <p>Vanilla calls {@code Lists.newArrayList()} which allocates a 10-slot
 * backing array. Most chunks contain 0–2 block entities, so the slack is
 * pure waste at the rate of dozens of packets per player per second
 * during chunk streaming.</p>
 */
@Mixin(ClientboundLevelChunkPacketData.class)
public abstract class ClientboundLevelChunkPacketDataSmallListMixin {

    @Redirect(
            method = "<init>(Lnet/minecraft/world/level/chunk/LevelChunk;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/google/common/collect/Lists;newArrayList()Ljava/util/ArrayList;"),
            remap = false)
    private ArrayList<Object> recoleta$smallBlockEntitiesList() {
        if (!MemoryConfig.getBooleanOrDefault(MemoryConfig.ENABLE_CHUNK_PACKET_RIGHT_SIZE, true)) {
            return Lists.newArrayList();
        }

        RecoletaCounters.CHUNK_PACKET_LIST_RESIZE.increment();
        return new ArrayList<>(2);
    }
}

