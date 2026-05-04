package com.github.recoleta.fabric.mixin.common;

import com.github.recoleta.config.MemoryConfig;
import com.github.recoleta.memory.RecoletaCounters;
import com.github.recoleta.memory.cache.RecoletaCaches;
import com.github.recoleta.memory.cache.SoftLruCache;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Caches the output of {@link ResourceLocation#toString()} through a
 * shared {@link SoftLruCache}.
 *
 * <p>Vanilla rebuilds the {@code "namespace:path"} string on every
 * invocation, allocating a fresh {@link String} each time. The
 * existing {@code ResourceLocationInternMixin} already canonicalises
 * the {@code namespace} and {@code path} components via
 * {@code RecoletaInterns.STRINGS}; this mixin extends the
 * canonicalisation to the joined output, so repeated
 * {@code toString()} calls on the same {@code ResourceLocation}
 * return the same {@code String} reference and avoid the
 * concatenation allocation.</p>
 *
 * <p>The cache key is the {@code ResourceLocation} itself, leaning
 * on its already-fast {@link ResourceLocation#equals(Object)} /
 * {@link ResourceLocation#hashCode()} (interned components reduce
 * those to reference equality and primitive arithmetic).</p>
 *
 * <p>The cache is bounded (default 4096 entries, configurable) and
 * soft-referenced, so the JVM is free to discard entries under heap
 * pressure. Cold paths simply pay the recompute cost; the LRU layer
 * preserves the working set even when memory is tight.</p>
 *
 * <p>Hit/miss counts are exposed through
 * {@link RecoletaCounters#RL_TOSTRING_CACHE_HIT} and
 * {@link RecoletaCounters#RL_TOSTRING_CACHE_MISS} for the
 * {@code /recoleta status} readout.</p>
 */
@Mixin(ResourceLocation.class)
public abstract class ResourceLocationToStringCacheMixin {

    /**
     * Fast path: if a cached output already exists for this
     * {@code ResourceLocation}, short-circuit the original method
     * body via {@link CallbackInfoReturnable#setReturnValue(Object)}.
     *
     * @param cir callback info carrying the would-be return value
     */
    @Inject(method = "toString", at = @At("HEAD"), cancellable = true)
    private void recoleta$toStringCacheHit(final CallbackInfoReturnable<String> cir) {
        if (!MemoryConfig.getBooleanOrDefault(MemoryConfig.ENABLE_RESOURCELOCATION_TOSTRING_CACHE, true)) {
            return;
        }
        final ResourceLocation self = (ResourceLocation) (Object) this;
        final String cached = RecoletaCaches.RL_TO_STRING.get(self);
        if (cached != null) {
            RecoletaCounters.RL_TOSTRING_CACHE_HIT.increment();
            cir.setReturnValue(cached);
        }
    }

    /**
     * Slow path: store the freshly-computed result for next time.
     * Only fires when the HEAD inject did not cancel, i.e. on a
     * cache miss.
     *
     * @param cir callback info with the computed return value
     */
    @Inject(method = "toString", at = @At("RETURN"))
    private void recoleta$toStringCacheFill(final CallbackInfoReturnable<String> cir) {
        if (!MemoryConfig.getBooleanOrDefault(MemoryConfig.ENABLE_RESOURCELOCATION_TOSTRING_CACHE, true)) {
            return;
        }
        final ResourceLocation self = (ResourceLocation) (Object) this;
        final String result = cir.getReturnValue();
        if (result != null) {
            RecoletaCaches.RL_TO_STRING.put(self, result);
            RecoletaCounters.RL_TOSTRING_CACHE_MISS.increment();
        }
    }
}
