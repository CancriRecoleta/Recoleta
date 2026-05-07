package com.github.recoleta.mixin.common;

import com.github.recoleta.config.MemoryConfig;
import com.github.recoleta.memory.cache.RecoletaInterns;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Canonicalises {@link Style} instances assigned to a
 * {@link MutableComponent} via the shared
 * {@link RecoletaInterns#STYLES} weak intern table.
 *
 * <p>Vanilla {@code Component#withStyle(...)},
 * {@code applyStyle(...)} and friends all funnel through
 * {@link MutableComponent#setStyle(Style)} after deriving a fresh
 * {@code Style} record. Many of those derivations end up at content
 * already produced by another component &mdash; the same "white +
 * italic", "gray + click event X", "minecraft default font" pairs
 * recur thousands of times across chat history, button labels,
 * tooltips and tellraw. Interning collapses the duplicates so a
 * long-lived component tree retains one canonical {@code Style} per
 * unique field combination.</p>
 *
 * <p>{@code Style} provides correct content-based
 * {@code equals}/{@code hashCode} across its ten fields, including
 * the nested {@code ClickEvent} / {@code HoverEvent} / {@code TextColor}
 * which themselves have proper equality. {@code WeakInternTable} can
 * therefore use content equality unmodified.</p>
 *
 * <p>{@code MutableComponent} itself is intentionally not interned:
 * it is mutable and holds a sibling list and content reference that
 * the caller may subsequently mutate; sharing instances would cause
 * cross-caller bleed. Only the {@code Style} reference is collapsed.</p>
 *
 * <p>{@code Style.EMPTY} is checked by reference equality before
 * intern lookup: every empty-style call returns the same canonical
 * vanilla constant without taking the table's lock.</p>
 */
@Mixin(MutableComponent.class)
public abstract class MutableComponentStyleInternMixin {

    /**
     * Replaces the {@code Style} argument with the canonical instance
     * from the intern table.
     *
     * @param input the style as passed by the caller
     * @return the canonical instance for the same content
     */
    @ModifyVariable(
            method = "setStyle(Lnet/minecraft/network/chat/Style;)Lnet/minecraft/network/chat/MutableComponent;",
            at = @At("HEAD"),
            argsOnly = true
    )
    private Style recoleta$internStyle(final Style input) {
        if (input == null || input == Style.EMPTY) {
            return input;
        }
        if (!MemoryConfig.cachedStyleIntern()) {
            return input;
        }
        return RecoletaInterns.STYLES.intern(input);
    }
}
