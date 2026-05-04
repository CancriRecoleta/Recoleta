package com.github.recoleta.mixin.common;

import com.google.common.collect.Sets;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.HashSet;

/**
 * Right-sizes the two universal {@link HashSet} fields that
 * {@link Entity} allocates for every entity in the world.
 *
 * <p>Vanilla initialises:</p>
 * <ul>
 *   <li>{@code fluidOnEyes = new HashSet<>()} &mdash; tracks which
 *       {@code TagKey<Fluid>} the entity's eyes are currently in.
 *       Typically empty.</li>
 *   <li>{@code tags = Sets.newHashSet()} &mdash; mirrors the NBT
 *       {@code Tags} array. Typically empty for non-command entities.</li>
 * </ul>
 *
 * <p>Both default-capacity {@link HashSet}s allocate a 16-slot
 * backing array on first insertion. Active worlds routinely host
 * 5000+ {@code Entity} instances (item entities, experience orbs,
 * projectiles, falling blocks) where both sets remain empty yet the
 * tables stay resident. Together that wastes roughly a megabyte.</p>
 *
 * <p>Pre-sizing both to {@code 2} produces a 2-slot table with a
 * 1-entry resize threshold. The first add triggers a single resize
 * to a 4-slot table; this is acceptable because the common case is
 * "never adds anything," and that path keeps the original 2-slot
 * footprint forever.</p>
 */
@Mixin(Entity.class)
public abstract class EntitySmallSetsMixin {

    /**
     * Catches {@code fluidOnEyes = new HashSet<>()} and supplies a
     * 2-slot variant.
     *
     * @param <T> element type
     * @return a right-sized {@link HashSet}
     */
    @Redirect(
            method = "<init>",
            at = @At(value = "NEW", target = "java/util/HashSet"),
            require = 0, expect = 1
    )
    private <T> HashSet<T> recoleta$smallFluidOnEyesSet() {
        return new HashSet<>(2);
    }

    /**
     * Catches {@code tags = Sets.newHashSet()} and supplies a 2-slot
     * variant.
     *
     * @param <T> element type
     * @return a right-sized {@link HashSet}
     */
    @Redirect(
            method = "<init>",
            at = @At(value = "INVOKE", target = "Lcom/google/common/collect/Sets;newHashSet()Ljava/util/HashSet;"),
            require = 0, expect = 1,
            remap = false
    )
    private <T> HashSet<T> recoleta$smallTagsSet() {
        return new HashSet<>(2);
    }
}
