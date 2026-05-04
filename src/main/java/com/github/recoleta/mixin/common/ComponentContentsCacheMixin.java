package com.github.recoleta.mixin.common;

import com.github.recoleta.config.MemoryConfig;
import com.github.recoleta.memory.RecoletaCounters;
import com.github.recoleta.memory.cache.RecoletaCaches;
import com.github.recoleta.memory.cache.SoftLruCache;
import net.minecraft.network.chat.ComponentContents;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.contents.KeybindContents;
import net.minecraft.network.chat.contents.LiteralContents;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Canonicalises immutable {@link ComponentContents} sub-records before
 * they are wrapped in a {@link MutableComponent}.
 *
 * <p>Vanilla {@code Component.literal(text)},
 * {@code Component.translatable(key)}, {@code Component.keybind(name)}
 * etc. all funnel into {@link MutableComponent#create(ComponentContents)}
 * after allocating a fresh content record. Profiling typical play
 * (chat, tooltips, narration, button labels, key prompts) shows this
 * call site fires thousands of times per minute with very high
 * text-locality &mdash; the same constants reappear over and over.</p>
 *
 * <p>This mixin intercepts the argument and swaps it for a cached
 * canonical instance whenever the input matches one of the cacheable
 * shapes:</p>
 *
 * <ul>
 *   <li>{@link LiteralContents} &mdash; cached unconditionally by text.</li>
 *   <li>{@link TranslatableContents} &mdash; cached only when
 *       {@code fallback == null} and {@code args.length == 0}, the
 *       overwhelmingly common single-arg form. Other shapes pass
 *       through unchanged because composite-key caching would have
 *       low hit rate against high lookup cost.</li>
 *   <li>{@link KeybindContents} &mdash; cached unconditionally by name.</li>
 * </ul>
 *
 * <p>{@link MutableComponent} itself is never cached: it is mutable,
 * and a shared instance would let one caller's
 * {@code .withStyle(BOLD)} bleed into another's component tree. The
 * inner content records are immutable (or have only thread-stale
 * lazy state) and safe to share.</p>
 *
 * <h2>Mixin shape note</h2>
 *
 * <p>{@code Component} is an interface and Mixin 0.8.5 forbids
 * injectors inside interface mixins. We instead intercept the
 * immediately-downstream {@code MutableComponent.create} entry. The
 * freshly-allocated content record passed in becomes garbage in the
 * same young-gen pass on a cache hit; the headline win is
 * <em>steady-state heap</em> from sharing across long-lived
 * {@code MutableComponent}s, plus shared lazy-decompose state on
 * {@code TranslatableContents}.</p>
 */
@Mixin(MutableComponent.class)
public abstract class ComponentContentsCacheMixin {

    /**
     * Single dispatch point that swaps the argument for a cached
     * canonical record when the input is a recognised cacheable
     * subtype.
     *
     * @param contents the contents passed by the caller
     * @return the canonical instance (cache hit) or the input (cache miss / non-cacheable)
     */
    @ModifyVariable(
            method = "create(Lnet/minecraft/network/chat/ComponentContents;)Lnet/minecraft/network/chat/MutableComponent;",
            at = @At("HEAD"),
            argsOnly = true
    )
    private static ComponentContents recoleta$internContents(final ComponentContents contents) {
        if (!MemoryConfig.getBooleanOrDefault(MemoryConfig.ENABLE_LITERAL_CONTENTS_CACHE, true)) {
            return contents;
        }
        if (contents instanceof LiteralContents lc) {
            return recoleta$cacheLiteral(lc);
        }
        if (contents instanceof TranslatableContents tc) {
            return recoleta$cacheTranslatable(tc);
        }
        if (contents instanceof KeybindContents kc) {
            return recoleta$cacheKeybind(kc);
        }
        return contents;
    }

    private static LiteralContents recoleta$cacheLiteral(final LiteralContents lc) {
        final String text = lc.text();
        if (text == null) return lc;
        final LiteralContents cached = RecoletaCaches.LITERAL_CONTENTS.get(text);
        if (cached != null) {
            RecoletaCounters.LITERAL_CONTENTS_CACHE_HIT.increment();
            return cached;
        }
        RecoletaCaches.LITERAL_CONTENTS.put(text, lc);
        RecoletaCounters.LITERAL_CONTENTS_CACHE_MISS.increment();
        return lc;
    }

    /**
     * Caches the single-arg / no-fallback form. Other shapes pass
     * through: composite caching by {@code (key, fallback, args)}
     * would need a hash-friendly key wrapper for the {@code args}
     * array and the hit rate would be low.
     */
    private static TranslatableContents recoleta$cacheTranslatable(final TranslatableContents tc) {
        if (tc.getFallback() != null) return tc;
        final Object[] args = tc.getArgs();
        if (args == null || args.length != 0) return tc;
        final String key = tc.getKey();
        if (key == null) return tc;
        final TranslatableContents cached = RecoletaCaches.TRANSLATABLE_CONTENTS.get(key);
        if (cached != null) {
            RecoletaCounters.TRANSLATABLE_CONTENTS_CACHE_HIT.increment();
            return cached;
        }
        RecoletaCaches.TRANSLATABLE_CONTENTS.put(key, tc);
        RecoletaCounters.TRANSLATABLE_CONTENTS_CACHE_MISS.increment();
        return tc;
    }

    private static KeybindContents recoleta$cacheKeybind(final KeybindContents kc) {
        final String name = kc.getName();
        if (name == null) return kc;
        final KeybindContents cached = RecoletaCaches.KEYBIND_CONTENTS.get(name);
        if (cached != null) {
            RecoletaCounters.KEYBIND_CONTENTS_CACHE_HIT.increment();
            return cached;
        }
        RecoletaCaches.KEYBIND_CONTENTS.put(name, kc);
        RecoletaCounters.KEYBIND_CONTENTS_CACHE_MISS.increment();
        return kc;
    }
}
