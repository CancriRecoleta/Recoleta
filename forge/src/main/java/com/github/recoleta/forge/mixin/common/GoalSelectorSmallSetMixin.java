package com.github.recoleta.forge.mixin.common;

import com.google.common.collect.Sets;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.LinkedHashSet;

/**
 * Right-sizes {@link GoalSelector}'s {@code availableGoals} set.
 *
 * <p>Vanilla initialises {@code availableGoals = Sets.newLinkedHashSet()}
 * &mdash; a default-capacity {@link LinkedHashSet}. Each {@code Mob}
 * carries one {@code goalSelector} plus one {@code targetSelector}
 * (a second {@code GoalSelector}), and a typical mob registers
 * 5-15 goals total. The default 16-bucket table is therefore both
 * close to the right ballpark and slightly oversized for many mobs.</p>
 *
 * <p>Pre-sizing to {@code 8} produces an 8-slot table covering
 * mobs with 6 or fewer goals (skeletons, zombies, item entities) at
 * half the original footprint, while mobs with more goals incur a
 * single resize back to 16 &mdash; the same final state as vanilla.
 * The {@code lockedFlags} ({@code EnumMap}) and {@code disabledFlags}
 * ({@code EnumSet}) fields are intentionally left alone: both are
 * already minimum-footprint enum-backed structures.</p>
 */
@Mixin(GoalSelector.class)
public abstract class GoalSelectorSmallSetMixin {

    /**
     * Catches {@code availableGoals = Sets.newLinkedHashSet()} and
     * supplies an 8-slot variant.
     *
     * @param <T> element type
     * @return a right-sized {@link LinkedHashSet}
     */
    @Redirect(
            method = "<init>",
            at = @At(value = "INVOKE", target = "Lcom/google/common/collect/Sets;newLinkedHashSet()Ljava/util/LinkedHashSet;"),
            require = 0, expect = 1,
            remap = false
    )
    private <T> LinkedHashSet<T> recoleta$smallAvailableGoals() {
        return new LinkedHashSet<>(8);
    }
}
