package com.github.recoleta.neoforge.mixin.common;

import com.github.recoleta.memory.SlackTrimmer;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.world.level.entity.PersistentEntitySectionManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Registers {@link PersistentEntitySectionManager}'s long-lived
 * fastutil collections with {@link SlackTrimmer}.
 *
 * <p>The three tracked maps grow with player render distance and
 * never compact:</p>
 *
 * <ul>
 *   <li>{@code chunkVisibility} &mdash; per-chunk visibility level
 *       seen by entity tracking.</li>
 *   <li>{@code chunkLoadStatuses} &mdash; per-chunk load lifecycle
 *       state.</li>
 *   <li>{@code chunksToUnload} &mdash; pending chunk-unload set.</li>
 * </ul>
 *
 * <p>{@code loadingInbox} is a {@code ConcurrentLinkedQueue}, which
 * has no slack capacity to reclaim. {@code sectionStorage}'s internal
 * map is registered separately in
 * {@code EntitySectionStorageTrimmableMixin}.</p>
 */
@Mixin(PersistentEntitySectionManager.class)
public abstract class PersistentEntitySectionManagerTrimmableMixin {

    @Shadow @Final private Long2ObjectMap<?> chunkVisibility;
    @Shadow @Final private Long2ObjectMap<?> chunkLoadStatuses;
    @Shadow @Final private LongSet chunksToUnload;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void recoleta$registerEntitySectionTrimmables(final CallbackInfo ci) {
        if (this.chunkVisibility instanceof Long2ObjectOpenHashMap<?> vis) {
            SlackTrimmer.trackTrimmable(vis, (Long2ObjectOpenHashMap<?> m) -> m.trim());
        }
        if (this.chunkLoadStatuses instanceof Long2ObjectOpenHashMap<?> stat) {
            SlackTrimmer.trackTrimmable(stat, (Long2ObjectOpenHashMap<?> m) -> m.trim());
        }
        if (this.chunksToUnload instanceof LongOpenHashSet set) {
            SlackTrimmer.trackTrimmable(set, (LongOpenHashSet s) -> s.trim());
        }
    }
}
