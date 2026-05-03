package com.github.recoleta.mixin.common;

import com.github.recoleta.memory.cache.RecoletaInterns;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * De-duplicates the {@code namespace} and {@code path} {@link String}s
 * stored on every {@link ResourceLocation}.
 *
 * <p>A vanilla world load instantiates tens of thousands of
 * {@code ResourceLocation}s, the overwhelming majority of which share a
 * handful of namespaces ({@code "minecraft"}, {@code "forge"},
 * {@code "c"}) and a long tail of repeated paths ({@code "stone"},
 * {@code "air"}, {@code "id"}). The default JVM string table is sized
 * for global use and gives no help here. By interning both arguments
 * through Recoleta's mod-private {@link RecoletaInterns#STRINGS} weak
 * table, every duplicate vanishes; only one canonical character array
 * stays live per unique value.</p>
 *
 * <p>The mixin targets the canonical protected constructor that all
 * other {@code ResourceLocation} constructors chain into. Targeting
 * a single ctor keeps the bytecode footprint minimal while still
 * covering 100% of allocation paths.</p>
 *
 * <p>The interning is allocation-free for already-interned strings
 * (a single map probe), so the per-call overhead is negligible
 * compared to the heap saving over a multi-hour session.</p>
 */
@Mixin(ResourceLocation.class)
public abstract class ResourceLocationInternMixin {

    /**
     * Canonicalises the namespace argument before the constructor
     * body assigns it to {@code this.namespace}.
     *
     * <p>Static because this injection runs at constructor head, before
     * {@code super()} has initialized {@code this}.</p>
     *
     * @param ns the original namespace
     * @return the interned, canonical instance
     */
    @ModifyVariable(
            method = "<init>(Ljava/lang/String;Ljava/lang/String;Lnet/minecraft/resources/ResourceLocation$Dummy;)V",
            at = @At("HEAD"),
            ordinal = 0,
            argsOnly = true
    )
    private static String recoleta$internNamespace(final String ns) {
        return ns == null ? null : RecoletaInterns.STRINGS.intern(ns);
    }

    /**
     * Canonicalises the path argument before the constructor body
     * assigns it to {@code this.path}.
     *
     * <p>Static because this injection runs at constructor head, before
     * {@code super()} has initialized {@code this}.</p>
     *
     * @param path the original path
     * @return the interned, canonical instance
     */
    @ModifyVariable(
            method = "<init>(Ljava/lang/String;Ljava/lang/String;Lnet/minecraft/resources/ResourceLocation$Dummy;)V",
            at = @At("HEAD"),
            ordinal = 1,
            argsOnly = true
    )
    private static String recoleta$internPath(final String path) {
        return path == null ? null : RecoletaInterns.STRINGS.intern(path);
    }
}

