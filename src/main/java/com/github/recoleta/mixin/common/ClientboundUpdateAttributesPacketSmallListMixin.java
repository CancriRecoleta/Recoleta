package com.github.recoleta.mixin.common;

import com.github.recoleta.config.MemoryConfig;
import com.github.recoleta.memory.RecoletaCounters;
import com.google.common.collect.Lists;
import net.minecraft.network.protocol.game.ClientboundUpdateAttributesPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.ArrayList;

/**
 * Right-sizes the {@code attributes} snapshot list built in the
 * server-side
 * {@link ClientboundUpdateAttributesPacket#ClientboundUpdateAttributesPacket(int, java.util.Collection)}
 * constructor.
 *
 * <p>Vanilla seeds the list with {@code Lists.newArrayList()} (10 slots)
 * then copies one {@code AttributeSnapshot} per dirty attribute. Living
 * entities expose roughly a dozen attributes but only a handful change
 * at once (health, movement speed, attack damage during combat or potion
 * effects), so capacity 12 covers the realistic worst case without the
 * default slack. These packets fire on every combat hit and effect
 * change.</p>
 */
@Mixin(ClientboundUpdateAttributesPacket.class)
public abstract class ClientboundUpdateAttributesPacketSmallListMixin {

    @Redirect(
            method = "<init>(ILjava/util/Collection;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/google/common/collect/Lists;newArrayList()Ljava/util/ArrayList;"),
            remap = false)
    private ArrayList<Object> recoleta$smallAttributeList() {
        if (!MemoryConfig.cachedPacketRightSize()) {
            return Lists.newArrayList();
        }

        RecoletaCounters.PACKET_LIST_RESIZE.increment();
        return new ArrayList<>(12);
    }
}
