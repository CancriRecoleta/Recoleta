package com.github.recoleta.mixin.common;

import com.github.recoleta.config.MemoryConfig;
import com.github.recoleta.memory.RecoletaCounters;
import com.google.common.collect.Lists;
import net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.ArrayList;

/**
 * Right-sizes the {@code slots} list decoded in
 * {@link ClientboundSetEquipmentPacket#ClientboundSetEquipmentPacket(net.minecraft.network.FriendlyByteBuf)}.
 *
 * <p>Vanilla calls {@code Lists.newArrayList()} (a 10-slot backing
 * array), but an entity can never carry more than the six
 * {@code EquipmentSlot} values, so the list is sized for exactly that.
 * Equipment packets stream for every armoured mob a player sees, so the
 * slack is paid continuously during normal play.</p>
 */
@Mixin(ClientboundSetEquipmentPacket.class)
public abstract class ClientboundSetEquipmentPacketSmallListMixin {

    @Redirect(
            method = "<init>(Lnet/minecraft/network/FriendlyByteBuf;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/google/common/collect/Lists;newArrayList()Ljava/util/ArrayList;"),
            remap = false)
    private ArrayList<Object> recoleta$smallSlotList() {
        if (!MemoryConfig.cachedPacketRightSize()) {
            return Lists.newArrayList();
        }

        RecoletaCounters.PACKET_LIST_RESIZE.increment();
        return new ArrayList<>(6);
    }
}
