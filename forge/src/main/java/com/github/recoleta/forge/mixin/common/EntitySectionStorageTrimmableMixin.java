package com.github.recoleta.forge.mixin.common;

import com.github.recoleta.memory.SlackTrimmer;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.world.level.entity.EntitySectionStorage;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Registers {@link EntitySectionStorage}'s {@code sections} fastutil
 * map with {@link SlackTrimmer}.
 *
 * <p>The {@code sections} map ({@code Long2ObjectOpenHashMap}) holds
 * an {@code EntitySection} per loaded chunk-section. Capacity grows
 * with the render-distance / simulation-distance peak and never
 * shrinks back &mdash; even after players move away and the sections
 * are removed from the map, the bucket array stays allocated.</p>
 *
 * <p>The companion {@code sectionIds} field is a
 * {@code LongAVLTreeSet}: tree-based, has no spare capacity, no
 * {@code trim()} to call.</p>
 *
 * <p>The mixin uses raw types for the {@code @Shadow} field because
 * {@code EntitySectionStorage} is generic in {@code T} and the
 * inner {@code EntitySection<T>} type does not need to round-trip
 * through the trim hook.</p>
 */
@Mixin(EntitySectionStorage.class)
public abstract class EntitySectionStorageTrimmableMixin {

    @Shadow @Final private Long2ObjectMap<?> sections;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void recoleta$registerSections(final CallbackInfo ci) {
        if (this.sections instanceof Long2ObjectOpenHashMap<?> map) {
            SlackTrimmer.trackTrimmable(map, (Long2ObjectOpenHashMap<?> m) -> m.trim());
        }
    }
}
