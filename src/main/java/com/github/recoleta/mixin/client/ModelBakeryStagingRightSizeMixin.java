package com.github.recoleta.mixin.client;

import com.github.recoleta.config.MemoryConfig;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.HashMap;

/**
 * Right-sizes the four staging {@link HashMap} field initialisers
 * inside {@link ModelBakery}.
 *
 * <p>Vanilla declares these four maps inline:</p>
 *
 * <pre>
 *     private final Map&lt;ResourceLocation, UnbakedModel&gt;       unbakedCache         = Maps.newHashMap();
 *     final     Map&lt;BakedCacheKey, BakedModel&gt;                  bakedCache           = Maps.newHashMap();
 *     private final Map&lt;ResourceLocation, UnbakedModel&gt;        topLevelModels       = Maps.newHashMap();
 *     private final Map&lt;ResourceLocation, BakedModel&gt;          bakedTopLevelModels  = Maps.newHashMap();
 * </pre>
 *
 * <p>Each grows to thousands of entries during a single reload (the
 * three "top level / unbaked" maps mirror the per-blockstate model
 * count: ~3000 vanilla, 8000+ modded). The default 16-bucket
 * {@link HashMap} resizes 8 times to reach that final layout, each
 * resize copying every existing {@code Node} into a freshly
 * allocated bucket array. Across the four maps that is roughly 32
 * intermediate {@code Node[]} allocations per reload, peaking at
 * 5-15 MiB of transient garbage all on the main thread during the
 * highest-pressure phase of startup.</p>
 *
 * <p>This mixin redirects all four field initialisers to a single
 * right-sized variant. Final steady-state heap is unchanged &mdash;
 * the table size after reload is still determined by content count
 * &mdash; but reload pauses get smoother and the young-gen burst
 * during model bake is reduced to one allocation per map.</p>
 *
 * <p>{@code modelGroups} (an {@code Object2IntOpenHashMap}) is
 * intentionally not touched: its size is bounded by the unique
 * model-group count (~hundreds) regardless of model count, and
 * fastutil's resize cost is lower than {@link HashMap}'s.</p>
 */
@OnlyIn(Dist.CLIENT)
@Mixin(ModelBakery.class)
public abstract class ModelBakeryStagingRightSizeMixin {

    /**
     * Catches all four {@code Maps.newHashMap()} field initialisers
     * with a single redirect; {@code expect = 4} fails the mixin
     * loudly if a vanilla refactor moves one of them, signalling
     * that the optimisation needs review.
     *
     * @param <K> key type
     * @param <V> value type
     * @return a right-sized {@link HashMap}
     */
    @Redirect(
            method = "<init>",
            at = @At(value = "INVOKE", target = "Lcom/google/common/collect/Maps;newHashMap()Ljava/util/HashMap;"),
            require = 0, expect = 4,
            remap = false
    )
    private <K, V> HashMap<K, V> recoleta$rightSizeBakeryStaging() {
        if (!MemoryConfig.getBooleanOrDefault(MemoryConfig.ENABLE_RELOAD_LISTENER_RIGHT_SIZE, true)) {
            return new HashMap<>();
        }
        return new HashMap<>(readBakeryCapacity());
    }

    private static int readBakeryCapacity() {
        try {
            return MemoryConfig.MODEL_BAKERY_STAGING_CAPACITY.get();
        } catch (final IllegalStateException ignored) {
            return 8192;
        }
    }
}
