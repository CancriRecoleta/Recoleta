package com.github.recoleta.memory.cache;

import com.github.recoleta.config.MemoryConfig;
import net.minecraft.network.chat.contents.KeybindContents;
import net.minecraft.network.chat.contents.LiteralContents;
import net.minecraft.network.chat.contents.NbtContents;
import net.minecraft.network.chat.contents.ScoreContents;
import net.minecraft.network.chat.contents.SelectorContents;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;

/**
 * Singleton holder for the cross-mod {@link SoftLruCache} instances
 * consumed by Recoleta mixins.
 *
 * <p>Mirrors {@link RecoletaInterns}'s pattern: every cache lives in
 * one statically-reachable place so vanilla constructors that fire
 * extremely early (some during JVM bootstrap before any mod has been
 * registered) can ask for them without further wiring.</p>
 */
public final class RecoletaCaches {

    /**
     * Cache for {@link ResourceLocation#toString()} results. Each
     * vanilla call rebuilds the {@code "namespace:path"} string from
     * scratch; with the existing {@link RecoletaInterns#STRINGS}
     * mixin canonicalising the components, the only remaining waste
     * is the freshly-allocated {@link String} object itself. This
     * cache canonicalises the <em>output</em> as well, so repeated
     * {@code toString()} calls on the same {@code ResourceLocation}
     * return the same {@code String} reference.
     *
     * <p>Sized for the working set: 4096 entries comfortably covers
     * vanilla's most-logged registry keys plus the namespaces and
     * paths used in the inner game loop. Soft references mean the
     * JVM evicts under heap pressure; bounded LRU means cold
     * entries fall out before that.</p>
     */
    public static final SoftLruCache<ResourceLocation, String> RL_TO_STRING =
            new SoftLruCache<>(toStringCacheSize());

    /**
     * Cache for {@link LiteralContents} instances keyed by their text.
     *
     * <p>{@code Component.literal(text)} allocates a fresh
     * {@code LiteralContents} record every call, even though the
     * record is immutable and equal records are interchangeable. By
     * canonicalising the inner record we let many
     * {@code MutableComponent}s share the same {@code LiteralContents}
     * reference; the {@code MutableComponent} wrapper itself stays
     * fresh per call because it is mutable
     * ({@code .append()} / {@code .withStyle()} would corrupt
     * shared instances).</p>
     *
     * <p>Bounded LRU + soft-referenced. The default 1024 entries
     * comfortably covers vanilla's well-known constant labels
     * (button text, narration phrases, common formatters) plus the
     * leading working-set of mod-supplied strings.</p>
     */
    public static final SoftLruCache<String, LiteralContents> LITERAL_CONTENTS =
            new SoftLruCache<>(literalContentsCacheSize());

    /**
     * Cache for {@link TranslatableContents} instances keyed by their
     * translation key, used only for the no-fallback / no-args form
     * ({@code Component.translatable("menu.options")} and friends).
     *
     * <p>Sharing a {@code TranslatableContents} instance across
     * callers also shares its lazily-decomposed render parts, which
     * removes a noticeable per-key reformat cost for UI labels that
     * appear many times per frame.</p>
     */
    public static final SoftLruCache<String, TranslatableContents> TRANSLATABLE_CONTENTS =
            new SoftLruCache<>(literalContentsCacheSize());

    /**
     * Cache for {@link KeybindContents} instances keyed by their
     * keybind name. Sharing the instance also shares the lazy
     * {@code nameResolver} {@code Supplier}, which the resolver
     * pipeline rebuilds on every keymap change.
     */
    public static final SoftLruCache<String, KeybindContents> KEYBIND_CONTENTS =
            new SoftLruCache<>(256);

    /**
     * Self-keyed cache for {@link ScoreContents} (cache key is the
     * record itself; equality is name+objective). Sharing collapses
     * duplicate {@code (name, objective)} pairs and lets multiple
     * call sites share the parsed {@code EntitySelector} that the
     * constructor builds from {@code name}.
     *
     * <p>Vanilla use-cases are sparse (mostly {@code /tellraw}); 256
     * entries is plenty.</p>
     */
    public static final SoftLruCache<ScoreContents, ScoreContents> SCORE_CONTENTS =
            new SoftLruCache<>(256);

    /**
     * Self-keyed cache for {@link SelectorContents}. Only used when
     * {@code separator} is {@code Optional.empty()} &mdash; the
     * separator is a {@link net.minecraft.network.chat.Component}
     * which is mutable, so caching by content equality on a mutable
     * value would be unsafe.
     */
    public static final SoftLruCache<SelectorContents, SelectorContents> SELECTOR_CONTENTS =
            new SoftLruCache<>(256);

    /**
     * Self-keyed cache for {@link NbtContents}. Only cached when
     * {@code separator} is {@code Optional.empty()} &mdash; same
     * mutable-Component caveat as {@link #SELECTOR_CONTENTS}.
     */
    public static final SoftLruCache<NbtContents, NbtContents> NBT_CONTENTS =
            new SoftLruCache<>(128);

    private RecoletaCaches() {
        /* singleton holder - never instantiated */
    }

    private static int toStringCacheSize() {
        try {
            return MemoryConfig.RESOURCELOCATION_TOSTRING_CACHE_SIZE.get();
        } catch (final IllegalStateException ignored) {
            return 4096;
        }
    }

    private static int literalContentsCacheSize() {
        try {
            return MemoryConfig.LITERAL_CONTENTS_CACHE_SIZE.get();
        } catch (final IllegalStateException ignored) {
            return 1024;
        }
    }
}
