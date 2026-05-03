package com.github.recoleta.mixin.common;

import com.google.common.collect.Maps;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.HashMap;

/**
 * Right-sizes the {@code tickersInLevel} {@link HashMap} on every
 * {@link LevelChunk}.
 *
 * <p>Vanilla initialises {@code tickersInLevel = Maps.newHashMap()}
 * with the JDK default of 16 buckets &mdash; an 80-byte table on
 * 64-bit JVMs &mdash; even though the vast majority of chunks have
 * zero or one ticking block entity. Replacing the call with an
 * initial capacity of {@code 2} reclaims roughly 64 bytes per chunk;
 * across ~1024 loaded chunks that adds up.</p>
 *
 * <p>The redirect only fires on the field initialiser inside
 * {@code <init>}; mixin's {@code expect = 1} is set so the mod loads
 * cleanly even if a future Forge build renames or moves the call.</p>
 */
@Mixin(LevelChunk.class)
public abstract class LevelChunkTickersSmallMapMixin {

    /**
     * Replaces the default-capacity {@link HashMap} with a 2-slot
     * variant tuned for the typical chunk's ticker count.
     *
     * <p>Non-static because the enclosing target {@code <init>} is
     * non-static; mixin requires the handler modifier to match.</p>
     *
     * @param <K> key type
     * @param <V> value type
     * @return a small {@link HashMap}
     */
    @Redirect(
            method = "<init>*",
            at = @At(value = "INVOKE", target = "Lcom/google/common/collect/Maps;newHashMap()Ljava/util/HashMap;"),
            require = 0, expect = 1
    )
    private <K, V> HashMap<K, V> recoleta$smallTickerMap() {
        return new HashMap<>(2);
    }
}

