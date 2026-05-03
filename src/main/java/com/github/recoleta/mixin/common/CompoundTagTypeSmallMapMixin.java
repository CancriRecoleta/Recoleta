package com.github.recoleta.mixin.common;

import com.github.recoleta.config.MemoryConfig;
import com.google.common.collect.Maps;
import net.minecraft.nbt.Tag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.HashMap;

/**
 * Right-sizes the temporary map used while loading CompoundTag from NBT.
 */
@Mixin(targets = "net.minecraft.nbt.CompoundTag$1")
public abstract class CompoundTagTypeSmallMapMixin {

    @Redirect(
            method = "load(Ljava/io/DataInput;ILnet/minecraft/nbt/NbtAccounter;)Lnet/minecraft/nbt/CompoundTag;",
            at = @At(value = "INVOKE", target = "Lcom/google/common/collect/Maps;newHashMap()Ljava/util/HashMap;"),
            expect = 1,
            remap = false
    )
    private HashMap<String, Tag> recoleta$smallLoadMap() {
        return MemoryConfig.enableCompoundTagSmallMaps() ? new HashMap<>(8) : Maps.newHashMap();
    }
}

