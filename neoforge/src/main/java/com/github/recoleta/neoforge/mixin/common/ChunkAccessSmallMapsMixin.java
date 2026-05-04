package com.github.recoleta.neoforge.mixin.common;

import com.google.common.collect.Maps;
import net.minecraft.world.level.chunk.ChunkAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.HashMap;

/**
 * Right-sizes the four {@link HashMap}s that {@link ChunkAccess}
 * allocates for every loaded chunk.
 *
 * <p>{@code ChunkAccess} initialises {@code structureStarts},
 * {@code structuresRefences} (sic), {@code pendingBlockEntities} and
 * {@code blockEntities} with {@link Maps#newHashMap()}, which yields a
 * default {@code HashMap} of capacity 16 and load-factor 0.75 &mdash; an
 * 80-byte {@code Node[]} table per map, 320 B per chunk, regardless of
 * how few entries the chunk actually contains. Most chunks have zero
 * structures and zero or one block entity, so 95% of those 320 bytes
 * are pure overhead.</p>
 *
 * <p>This mixin redirects every such call inside
 * {@code ChunkAccess.<init>} to construct a {@code HashMap} with
 * initial capacity {@code 4}, shrinking the resident table to 16 bytes
 * per map (64 B per chunk). At the typical 1024 loaded chunks per
 * dimension this returns roughly 250 KB of always-resident heap;
 * scaled across all dimensions, all chunks loaded over a long session
 * the cumulative saving is substantial.</p>
 *
 * <p>{@code expect = 4} ensures the mixin will gracefully degrade if a
 * future Forge build adds or removes one of the four call sites.</p>
 */
@Mixin(ChunkAccess.class)
public abstract class ChunkAccessSmallMapsMixin {

    /**
     * Replaces the default-capacity {@link HashMap} construction with
     * a right-sized variant.
     *
     * <p>Must be a non-static instance method even though
     * {@link Maps#newHashMap()} is itself static: mixin requires the
     * {@code @Redirect} handler's {@code static} modifier to match the
     * <strong>enclosing target method</strong>, and {@code <init>} is
     * a non-static method.</p>
     *
     * @param <K> key type
     * @param <V> value type
     * @return a small {@link HashMap} sized for the typical chunk content
     */
    @Redirect(
            method = "<init>*",
            at = @At(value = "INVOKE", target = "Lcom/google/common/collect/Maps;newHashMap()Ljava/util/HashMap;"),
            require = 0, expect = 4,
            remap = false
    )
    private <K, V> HashMap<K, V> recoleta$smallHashMap() {
        return new HashMap<>(4);
    }
}

