package com.github.recoleta.fabric.mixin.common;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.pathfinder.NodeEvaluator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Accessor for NodeEvaluator protected state used by common pathfinding mixins.
 */
@Mixin(NodeEvaluator.class)
public interface NodeEvaluatorAccessor {

    @Accessor("mob")
    Mob recoleta$getMob();
}

