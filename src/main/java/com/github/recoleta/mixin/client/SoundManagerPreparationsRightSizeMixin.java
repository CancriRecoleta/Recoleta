package com.github.recoleta.mixin.client;

import com.github.recoleta.config.MemoryConfig;
import com.google.common.collect.Maps;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.HashMap;

/**
 * Right-sizes the staging {@link HashMap} field on
 * {@code SoundManager.Preparations}, the inner class that holds the
 * sound registry being built during reload.
 *
 * <p>Vanilla initialises {@code Preparations.registry = Maps.newHashMap()}
 * &mdash; default 16 buckets &mdash; and then populates it with one
 * entry per sound event (~1500 in vanilla, more in modded packs).
 * The seven successive resize copies during reload are exactly the
 * pattern targeted by
 * {@link com.github.recoleta.mixin.common.SimpleJsonResourceReloadListenerRightSizeMixin}
 * for JSON-resource listeners; this mixin handles the bespoke
 * {@code SoundManager} code path that bypasses
 * {@code SimpleJsonResourceReloadListener}.</p>
 *
 * <p>{@code Preparations} is a package-private static inner class,
 * so the mixin uses the {@code targets = "..."} string form to
 * reach it.</p>
 *
 * <p>The outer {@code SoundManager}'s {@code registry} and
 * {@code soundCache} fields are right-sized separately by
 * {@link SoundManagerSmallMapsMixin}; this mixin is the
 * complementary inner-class half.</p>
 *
 * <p>{@code Preparations.soundCache} is initialised to
 * {@code Map.of()} (empty literal) and only re-assigned in
 * {@code listResources(...)}, so it has no slack to right-size.</p>
 */
@OnlyIn(Dist.CLIENT)
@Mixin(targets = "net.minecraft.client.sounds.SoundManager$Preparations")
public abstract class SoundManagerPreparationsRightSizeMixin {

    @Redirect(
            method = "<init>",
            at = @At(value = "INVOKE", target = "Lcom/google/common/collect/Maps;newHashMap()Ljava/util/HashMap;"),
            require = 0, expect = 1,
            remap = false
    )
    private <K, V> HashMap<K, V> recoleta$rightSizeRegistry() {
        if (!MemoryConfig.getBooleanOrDefault(MemoryConfig.ENABLE_RELOAD_LISTENER_RIGHT_SIZE, true)) {
            return Maps.newHashMap();
        }
        return new HashMap<>(2048);
    }
}
