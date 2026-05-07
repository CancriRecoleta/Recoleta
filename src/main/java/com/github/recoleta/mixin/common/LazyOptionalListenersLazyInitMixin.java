package com.github.recoleta.mixin.common;

import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.common.util.NonNullConsumer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Lazy-initialises the {@code listeners} {@link HashSet} on every
 * {@link LazyOptional}.
 *
 * <p>Vanilla Forge declares {@code private final Set<NonNullConsumer<...>>
 * listeners = new HashSet<>();} as a field initialiser. Even though
 * default-constructed {@link HashSet} defers its bucket-array allocation
 * until the first {@code add}, the wrapper objects themselves
 * ({@code HashSet} + its inner {@code HashMap}) cost roughly 80 B of
 * always-resident heap. Every {@code ItemStack} capability dispatcher,
 * every block-entity capability, every entity capability holds at least
 * one {@code LazyOptional}; on a long-running server with thousands of
 * stacks and entities the cumulative footprint adds up to several MB
 * for state nobody ever reads.</p>
 *
 * <p>This mixin replaces the eager allocation with a lazy one:</p>
 *
 * <ul>
 *   <li>{@code listeners} is set to {@code null} at constructor return,
 *       discarding the unused {@code HashSet} pair allocated by the
 *       field initialiser.</li>
 *   <li>{@link LazyOptional#addListener(NonNullConsumer)} {@code Set.add}
 *       call is redirected through a handler that lazy-creates the
 *       backing set sized for two listeners (the typical max).</li>
 *   <li>{@link LazyOptional#removeListener(NonNullConsumer)} {@code Set.remove}
 *       is redirected to short-circuit when the field is still null.</li>
 *   <li>{@link LazyOptional#invalidate()}'s {@code forEach} and
 *       {@code clear} calls are redirected to no-op when the field is
 *       null.</li>
 * </ul>
 *
 * <p>The original {@code HashSet} allocated by the field initialiser
 * becomes garbage in the same young-gen pass as our null assignment,
 * so the only steady-state cost is the one-time eager allocation
 * itself; the resident savings persist for the entire lifetime of
 * every empty {@code LazyOptional}, which is the overwhelming
 * majority.</p>
 */
@Mixin(value = LazyOptional.class, remap = false)
public abstract class LazyOptionalListenersLazyInitMixin {

    @Mutable
    @Final
    @Shadow
    private Set<NonNullConsumer<LazyOptional<?>>> listeners;

    /**
     * Replaces the field-initialiser-allocated {@link HashSet} with
     * {@code null}. The original instance becomes garbage immediately
     * and the per-{@code LazyOptional} resident overhead drops to zero
     * for callers that never register a listener (the common case).
     *
     * @param ci callback info
     */
    @Inject(method = "<init>", at = @At("RETURN"))
    private void recoleta$nullListeners(final CallbackInfo ci) {
        this.listeners = null;
    }

    /**
     * Lazy-creates {@link #listeners} on the first {@code add}. The
     * argument {@code set} captured by mixin is the field's current
     * value at the call site &mdash; will be {@code null} the first
     * time, after which the lazily-created instance is reused.
     *
     * @param set      ignored; value of {@code this.listeners} at the redirect site
     * @param listener listener being added
     * @return {@code true} if the set did not already contain the listener
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    @Redirect(
            method = "addListener",
            at = @At(value = "INVOKE", target = "Ljava/util/Set;add(Ljava/lang/Object;)Z")
    )
    private boolean recoleta$lazyAdd(final Set set, final Object listener) {
        if (this.listeners == null) {
            this.listeners = new HashSet<>(2);
        }
        return this.listeners.add((NonNullConsumer<LazyOptional<?>>) listener);
    }

    /**
     * Short-circuits {@code Set.remove} when {@link #listeners} is
     * still {@code null}. A caller can legitimately reach
     * {@code removeListener} without first having called
     * {@code addListener} (e.g. defensive cleanup of a listener that
     * was never registered); the original code would NPE; ours
     * silently returns {@code false}.
     *
     * @param set      ignored; value of {@code this.listeners} at the redirect site
     * @param listener listener being removed
     * @return {@code true} if the listener was present and removed
     */
    @Redirect(
            method = "removeListener",
            at = @At(value = "INVOKE", target = "Ljava/util/Set;remove(Ljava/lang/Object;)Z")
    )
    private boolean recoleta$lazyRemove(final Set<NonNullConsumer<LazyOptional<?>>> set, final Object listener) {
        return this.listeners != null && this.listeners.remove(listener);
    }

    /**
     * No-op when {@link #listeners} is {@code null}; otherwise the
     * vanilla {@code forEach} runs unchanged.
     *
     * @param set    ignored; value of {@code this.listeners} at the redirect site
     * @param action consumer to apply to each listener
     */
    @Redirect(
            method = "invalidate",
            at = @At(value = "INVOKE", target = "Ljava/util/Set;forEach(Ljava/util/function/Consumer;)V")
    )
    private void recoleta$nullForEach(final Set<NonNullConsumer<LazyOptional<?>>> set,
                                       final Consumer<NonNullConsumer<LazyOptional<?>>> action) {
        if (set != null) {
            set.forEach(action);
        }
    }

    /**
     * No-op when {@link #listeners} is {@code null}; otherwise the
     * vanilla {@code clear} runs unchanged.
     *
     * @param set ignored; value of {@code this.listeners} at the redirect site
     */
    @Redirect(
            method = "invalidate",
            at = @At(value = "INVOKE", target = "Ljava/util/Set;clear()V")
    )
    private void recoleta$nullClear(final Set<NonNullConsumer<LazyOptional<?>>> set) {
        if (set != null) {
            set.clear();
        }
    }
}
