package com.github.recoleta.mixin.common;

import com.github.recoleta.config.MemoryConfig;
import com.github.recoleta.memory.RecoletaCounters;
import com.google.common.collect.Lists;
import net.minecraft.network.chat.MutableComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.ArrayList;

/**
 * Right-sizes the sibling list every {@link MutableComponent} is born
 * with.
 *
 * <p>{@link MutableComponent#create(net.minecraft.network.chat.ComponentContents)}
 * &mdash; the single factory behind {@code Component.literal},
 * {@code Component.translatable}, {@code Component.keybind} and friends
 * &mdash; seeds the sibling list with {@code Lists.newArrayList()}, a
 * 10-slot backing array. The overwhelming majority of components (chat
 * lines, tooltips, button labels, scoreboard names) carry zero to two
 * siblings, so eight of those ten references are pure resident slack on
 * the millions of components a session allocates.</p>
 *
 * <p>Capacity 2 covers the common {@code append}-once / {@code append}-
 * twice patterns without a resize; deeply nested components simply grow
 * the list on demand exactly as before.</p>
 *
 * <p>This is the only place vanilla constructs the sibling list, so a
 * single method-scoped redirect canonicalises the size for every
 * component.</p>
 */
@Mixin(MutableComponent.class)
public abstract class MutableComponentSiblingsSmallListMixin {

    @Redirect(
            method = "create(Lnet/minecraft/network/chat/ComponentContents;)Lnet/minecraft/network/chat/MutableComponent;",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/google/common/collect/Lists;newArrayList()Ljava/util/ArrayList;"),
            remap = false)
    private static ArrayList<Object> recoleta$smallSiblingList() {
        if (!MemoryConfig.cachedComponentSiblingsRightSize()) {
            return Lists.newArrayList();
        }

        RecoletaCounters.COMPONENT_SIBLINGS_RESIZE.increment();
        return new ArrayList<>(2);
    }
}
