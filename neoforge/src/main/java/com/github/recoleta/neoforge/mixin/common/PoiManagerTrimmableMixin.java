package com.github.recoleta.neoforge.mixin.common;

import com.github.recoleta.memory.SlackTrimmer;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Registers {@link PoiManager}'s {@code loadedChunks} fastutil set
 * with {@link SlackTrimmer}'s server-side trim cadence.
 *
 * <p>{@code loadedChunks} ({@link LongOpenHashSet}) accumulates chunk
 * positions for currently-loaded POI sections. On a long-lived
 * dedicated server it grows during render-distance spikes (player
 * elytra-flying, /tp around) and never shrinks &mdash; fastutil hash
 * tables only compact on explicit {@code trim()}.</p>
 *
 * <p>The {@code DistanceTracker} inner class carries its own
 * {@code Long2ByteOpenHashMap} that needs registering separately;
 * see {@code PoiManagerDistanceTrackerTrimmableMixin}.</p>
 */
@Mixin(PoiManager.class)
public abstract class PoiManagerTrimmableMixin {

    @Shadow @Final private LongSet loadedChunks;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void recoleta$registerLoadedChunks(final CallbackInfo ci) {
        if (this.loadedChunks instanceof LongOpenHashSet set) {
            SlackTrimmer.trackTrimmable(set, (LongOpenHashSet s) -> s.trim());
        }
    }
}
