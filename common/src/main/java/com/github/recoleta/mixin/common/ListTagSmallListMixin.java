package com.github.recoleta.mixin.common;

import com.github.recoleta.config.MemoryConfig;
import com.github.recoleta.memory.RecoletaCounters;
import com.google.common.collect.Lists;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.ArrayList;

/**
 * Right-sizes the backing list of an empty {@link ListTag}.
 *
 * <p>Vanilla's no-arg constructor calls {@code Lists.newArrayList()},
 * which allocates a 10-slot {@code Object[]}. The vast majority of
 * {@code ListTag} instances built at runtime (e.g. enchantments,
 * item lore, attribute modifiers, custom modifier lists) hold a
 * handful of entries, so most of the table is permanently wasted.</p>
 *
 * <p>This mixin redirects the constructor's {@code Lists.newArrayList()}
 * call to a 4-slot {@link ArrayList} when the small-map toggle is
 * enabled. The list grows naturally on demand for the rare ListTag
 * that exceeds that size; the only behavioural change is reduced
 * young-gen pressure.</p>
 *
 * <p>The package-private {@code ListTag(List, byte)} constructor used
 * by {@code TagType.load(...)} is intentionally left untouched: it
 * already receives a right-sized {@code Lists.newArrayListWithCapacity(i)}
 * argument that matches the decoded entry count.</p>
 */
@Mixin(ListTag.class)
public abstract class ListTagSmallListMixin {

    @Redirect(
            method = "<init>()V",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/google/common/collect/Lists;newArrayList()Ljava/util/ArrayList;"),
            remap = false)
    private static ArrayList<Tag> recoleta$smallEmptyList() {
        if (!MemoryConfig.enableCompoundTagSmallMaps()) {
            return Lists.newArrayList();
        }
        RecoletaCounters.LIST_TAG_SMALL_LIST.increment();
        return new ArrayList<>(4);
    }
}


