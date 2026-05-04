package com.github.recoleta.neoforge.mixin.common;

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

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Right-sizes the {@code attributes} and {@code dirtyAttributes}
 * collections on every {@link AttributeMap}.
 *
 * <p>Vanilla initialises both via {@code Maps.newHashMap()} /
 * {@code Sets.newHashSet()} &mdash; default capacity 16. Each
 * {@code LivingEntity} gets one {@link AttributeMap}; even a
 * fully-modded player rarely exceeds 12 attribute entries, while
 * {@code dirtyAttributes} usually holds 0-1 entries between sync
 * passes. With 1000 active living entities the wasted bucket arrays
 * cost roughly 256 KB of always-resident heap.</p>
 *
 * <p>The replacement happens at constructor return so that field
 * initializers run first and we can simply overwrite the references.</p>
 */
@Mixin(AttributeMap.class)
public abstract class AttributeMapSmallMapsMixin {

    @Mutable
    @Final
    @Shadow
    private Map<Attribute, AttributeInstance> attributes;

    @Mutable
    @Final
    @Shadow
    private Set<AttributeInstance> dirtyAttributes;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void recoleta$smallAttributeMaps(final CallbackInfo ci) {
        this.attributes = new HashMap<>(8);
        this.dirtyAttributes = new HashSet<>(2);
    }
}
