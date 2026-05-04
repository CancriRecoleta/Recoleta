package com.github.recoleta.neoforge.mixin.common;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeMap;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;
import java.util.Set;

/**
 * Right-sizes the three backing collections on every {@link AttributeMap}.
 *
 * <p>Vanilla initialises {@code attributes}, {@code attributesToSync}
 * and {@code attributesToUpdate} via fastutil default capacities (16).
 * Each {@link net.minecraft.world.entity.LivingEntity} owns one
 * AttributeMap; even a fully-modded player rarely exceeds 12 attribute
 * entries and the two dirty sets usually hold 0-1 entries between
 * sync passes. With 1000 active living entities the wasted bucket
 * arrays cost roughly 256 KB of always-resident heap.</p>
 *
 * <p>1.21 split the original {@code dirtyAttributes} into two fields:
 * {@code attributesToSync} (network sync candidates) and
 * {@code attributesToUpdate} (this-tick value-change candidates).</p>
 */
@Mixin(AttributeMap.class)
public abstract class AttributeMapSmallMapsMixin {

    @Mutable
    @Final
    @Shadow
    private Map<Holder<Attribute>, AttributeInstance> attributes;

    @Mutable
    @Final
    @Shadow
    private Set<AttributeInstance> attributesToSync;

    @Mutable
    @Final
    @Shadow
    private Set<AttributeInstance> attributesToUpdate;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void recoleta$smallAttributeMaps(final CallbackInfo ci) {
        this.attributes = new Object2ObjectOpenHashMap<>(8);
        this.attributesToSync = new ObjectOpenHashSet<>(2);
        this.attributesToUpdate = new ObjectOpenHashSet<>(2);
    }
}