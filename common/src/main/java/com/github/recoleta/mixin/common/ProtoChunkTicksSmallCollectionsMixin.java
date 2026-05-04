package com.github.recoleta.mixin.common;

import it.unimi.dsi.fastutil.objects.ObjectOpenCustomHashSet;
import net.minecraft.world.ticks.ProtoChunkTicks;
import net.minecraft.world.ticks.SavedTick;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Right-sizes the empty scheduled-tick storage used by proto chunks.
 *
 * <p>World generation creates many {@link ProtoChunkTicks} instances before
 * most chunks ever receive a scheduled block or fluid tick. Starting the list
 * and duplicate-filter set at one slot avoids the default backing arrays while
 * preserving normal growth when generation or mods schedule work.</p>
 *
 * @param <T> tick payload type
 */
@Mixin(ProtoChunkTicks.class)
public abstract class ProtoChunkTicksSmallCollectionsMixin<T> {

    @Mutable
    @Final
    @Shadow
    private List<SavedTick<T>> ticks;

    @Mutable
    @Final
    @Shadow
    private Set<SavedTick<?>> ticksPerPosition;

    /**
     * Replaces the empty default collections with minimum-capacity variants.
     *
     * @param ci callback info
     */
    @Inject(method = "<init>", at = @At("RETURN"))
    private void recoleta$smallEmptyProtoTickCollections(final CallbackInfo ci) {
        this.ticks = new ArrayList<>(1);
        this.ticksPerPosition = new ObjectOpenCustomHashSet<>(1, SavedTick.UNIQUE_TICK_HASH);
    }
}
