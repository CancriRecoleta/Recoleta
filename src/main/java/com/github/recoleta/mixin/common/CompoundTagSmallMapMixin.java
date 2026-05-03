package com.github.recoleta.mixin.common;

import com.github.recoleta.config.MemoryConfig;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashMap;
import java.util.Map;

/**
 * Right-sizes the no-arg CompoundTag map for the common tiny-NBT case.
 */
@Mixin(CompoundTag.class)
public abstract class CompoundTagSmallMapMixin {

    @Mutable
    @Final
    @Shadow
    private Map<String, Tag> tags;

    @Inject(method = "<init>()V", at = @At("RETURN"))
    private void recoleta$smallDefaultMap(final CallbackInfo ci) {
        if (MemoryConfig.enableCompoundTagSmallMaps()) {
            this.tags = new HashMap<>(4);
        }
    }
}

