package com.github.recoleta.fabric.mixin.common;

import com.github.recoleta.memory.SlackTrimmer;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ByteMap;
import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2LongMap;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Registers {@link ChunkMap}'s long-lived fastutil collections with
 * {@link SlackTrimmer}'s server-side {@code Trimmable} hook.
 *
 * <p>fastutil's open-addressed hash containers grow geometrically (the
 * {@code n} field doubles whenever load exceeds the configured factor)
 * and never shrink unless the caller invokes {@code trim()}. Vanilla
 * never does. On a long-lived dedicated server these maps easily reach
 * 16384-bucket tables during a render-distance spike (e.g. a player
 * elytra-flying through new chunks) and stay at that footprint forever
 * even when only a few hundred chunks remain loaded.</p>
 *
 * <p>{@code visibleChunkMap} is intentionally <em>not</em> registered:
 * vanilla periodically replaces the field with a fresh
 * {@code updatingChunkMap.clone()}, so trimming the current snapshot
 * is wasted work; the next clone is sized to current population.</p>
 *
 * <p>{@code unloadQueue} is a {@code ConcurrentLinkedQueue} which has
 * no slack capacity to reclaim.</p>
 */
@Mixin(ChunkMap.class)
public abstract class ChunkMapTrimmableMixin {

    @Shadow @Final private Long2ObjectLinkedOpenHashMap<ChunkHolder> updatingChunkMap;
    @Shadow @Final private Long2ObjectLinkedOpenHashMap<ChunkHolder> pendingUnloads;
    @Shadow @Final LongSet toDrop;
    @Shadow @Final private Int2ObjectMap<?> entityMap;
    @Shadow @Final private Long2ByteMap chunkTypeCache;
    @Shadow @Final private Long2LongMap chunkSaveCooldowns;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void recoleta$registerTrimmables(final CallbackInfo ci) {
        SlackTrimmer.trackTrimmable(this.updatingChunkMap,
                (Long2ObjectLinkedOpenHashMap<ChunkHolder> m) -> m.trim());
        SlackTrimmer.trackTrimmable(this.pendingUnloads,
                (Long2ObjectLinkedOpenHashMap<ChunkHolder> m) -> m.trim());

        // toDrop is LongSet-typed but constructed as LongOpenHashSet;
        // the runtime check protects against a mod swapping the implementation out.
        if (this.toDrop instanceof LongOpenHashSet drop) {
            SlackTrimmer.trackTrimmable(drop, (LongOpenHashSet s) -> s.trim());
        }

        if (this.entityMap instanceof Int2ObjectOpenHashMap<?> em) {
            SlackTrimmer.trackTrimmable(em, (Int2ObjectOpenHashMap<?> m) -> m.trim());
        }
        if (this.chunkTypeCache instanceof Long2ByteOpenHashMap typeCache) {
            SlackTrimmer.trackTrimmable(typeCache, (Long2ByteOpenHashMap m) -> m.trim());
        }
        if (this.chunkSaveCooldowns instanceof Long2LongOpenHashMap cooldowns) {
            SlackTrimmer.trackTrimmable(cooldowns, (Long2LongOpenHashMap m) -> m.trim());
        }
    }
}
