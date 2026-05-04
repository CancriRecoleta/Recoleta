package com.github.recoleta.neoforge.mixin.common;

import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Set;

/**
 * Right-sizes {@link GoalSelector}'s {@code availableGoals} set.
 *
 * <p>1.21 changed the backing type from Guava {@code LinkedHashSet}
 * to fastutil {@code ObjectLinkedOpenHashSet} (declared inline as a
 * field initializer rather than via a {@code Sets.newLinkedHashSet()}
 * factory call). The previous Guava-call-redirect mixin no longer has
 * a target, so this mixin instead re-assigns the field to a smaller
 * fastutil set immediately after the constructor runs.</p>
 *
 * <p>Pre-sizing to {@code 8} covers mobs with 6 or fewer goals
 * (skeletons, zombies, item entities) at half the original footprint.
 * Mobs with more goals incur a single resize back to 16 - same final
 * state as vanilla.</p>
 */
@Mixin(GoalSelector.class)
public abstract class GoalSelectorSmallSetMixin {

    @Mutable
    @Final
    @Shadow
    private Set<WrappedGoal> availableGoals;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void recoleta$smallAvailableGoals(final CallbackInfo ci) {
        if (this.availableGoals.isEmpty()) {
            this.availableGoals = new ObjectLinkedOpenHashSet<>(8);
        }
    }
}