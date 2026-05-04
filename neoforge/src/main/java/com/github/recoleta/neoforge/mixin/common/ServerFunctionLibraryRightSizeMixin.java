package com.github.recoleta.neoforge.mixin.common;

import com.github.recoleta.config.MemoryConfig;
import net.minecraft.server.ServerFunctionLibrary;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.HashMap;

/**
 * Right-sizes the staging {@link HashMap} built inside
 * {@link ServerFunctionLibrary}'s reload pipeline.
 *
 * <p>Vanilla {@code ServerFunctionLibrary.reload} contains an
 * inlined {@code Maps.newHashMap()} call inside a
 * {@link java.util.concurrent.CompletableFuture} composition step.
 * Each entry maps a function {@link net.minecraft.resources.ResourceLocation}
 * to its in-flight compilation future. Vanilla ships only a handful
 * of {@code .mcfunction} files but command-driven mod servers can
 * accumulate hundreds to thousands &mdash; enough that the default
 * 16-bucket map resizes through the same 5-7-step ladder as the
 * {@link net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener}
 * staging path.</p>
 *
 * <p>The {@code Maps.newHashMap} call lives inside an anonymous
 * lambda body, which the Java compiler emits as a synthetic
 * {@code lambda$reload$N} method. Targeting that method by exact
 * name would break across vanilla refactors, so we use the wildcard
 * {@code method = "*"}. The {@code expect = 1} guard makes the
 * mixin fail loudly if the call vanishes from the class entirely
 * (signal that the optimisation needs a manual review).</p>
 */
@Mixin(ServerFunctionLibrary.class)
public abstract class ServerFunctionLibraryRightSizeMixin {

    @Redirect(
            method = "*",
            at = @At(value = "INVOKE", target = "Lcom/google/common/collect/Maps;newHashMap()Ljava/util/HashMap;"),
            require = 0, expect = 1,
            remap = false
    )
    private <K, V> HashMap<K, V> recoleta$rightSizeFunctionStaging() {
        if (!MemoryConfig.getBooleanOrDefault(MemoryConfig.ENABLE_RELOAD_LISTENER_RIGHT_SIZE, true)) {
            return new HashMap<>();
        }
        return new HashMap<>(readStagingCapacity());
    }

    private static int readStagingCapacity() {
        try {
            return MemoryConfig.RELOAD_LISTENER_STAGING_CAPACITY.get();
        } catch (final IllegalStateException ignored) {
            return 512;
        }
    }
}
