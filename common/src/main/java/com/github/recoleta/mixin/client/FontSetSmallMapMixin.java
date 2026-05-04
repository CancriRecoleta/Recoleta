package com.github.recoleta.mixin.client;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntList;
import net.minecraft.client.gui.font.FontSet;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Right-sizes the {@code glyphsByWidth} {@link Int2ObjectOpenHashMap}
 * on {@link FontSet}.
 *
 * <p>{@code FontSet.reload(...)} clears this map and refills it by
 * grouping every supported codepoint's advance width into a bucket.
 * The default-fastutil capacity of 16 grows by powers of two to
 * roughly 512 over the course of a font reload (each resize copies
 * the existing entries into a new {@code int[]}/{@code Object[]}
 * pair). Pre-sizing to 512 reproduces the final layout in one
 * allocation and skips the intermediate copies.</p>
 *
 * <p>Field replacement at constructor return is safe: the
 * {@code FontSet} constructor only stores {@code textureManager} and
 * {@code name}; the map is empty when {@code reload} is first called,
 * which begins with {@code glyphsByWidth.clear()} and then populates.
 * fastutil's {@code clear()} keeps the existing capacity, so the
 * 512-slot table persists across subsequent reloads.</p>
 */
@Mixin(FontSet.class)
public abstract class FontSetSmallMapMixin {

    @Mutable
    @Final
    @Shadow
    private Int2ObjectMap<IntList> glyphsByWidth;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void recoleta$smallGlyphsByWidth(final CallbackInfo ci) {
        this.glyphsByWidth = new Int2ObjectOpenHashMap<>(512);
    }
}
