package com.github.recoleta.memory;

import java.util.concurrent.atomic.LongAdder;

/**
 * Central registry of {@link LongAdder} counters used by Recoleta's
 * mixins to publish hit/miss/use statistics.
 *
 * <p>All counters live here (rather than on each mixin class) for two
 * reasons:</p>
 * <ul>
 *   <li>Mixin classes are not allowed to expose non-private static
 *       members without an explicit {@code @Unique} marker; a single
 *       holder sidesteps that limitation.</li>
 *   <li>{@code MemoryCommand} can render every counter from one
 *       location, so adding a new optimisation only requires touching
 *       this class plus the new mixin &mdash; not the command code.</li>
 * </ul>
 *
 * <p>Counters are deliberately <em>cheap</em>: an {@link LongAdder}
 * increment is a single relaxed write under low contention, so even
 * the hottest mixin call sites can safely tally every event.</p>
 */
public final class RecoletaCounters {

    /** Counts default {@code CompoundTag()} ctor right-sizes. */
    public static final LongAdder COMPOUND_TAG_DEFAULT_RESIZE = new LongAdder();

    /** Counts {@code CompoundTag(Map)} ctor (NBT-load path) repacks. */
    public static final LongAdder COMPOUND_TAG_LOAD_REPACK = new LongAdder();

    /** Counts {@code ListTag()} no-arg ctor right-sizes. */
    public static final LongAdder LIST_TAG_SMALL_LIST = new LongAdder();

    /** Counts {@code ClientboundLevelChunkPacketData} block-entity list right-sizes. */
    public static final LongAdder CHUNK_PACKET_LIST_RESIZE = new LongAdder();

    /** Counts packed AABB collision-cache hits in {@code WalkNodeEvaluator}. */
    public static final LongAdder PATH_PACKED_CACHE_HIT = new LongAdder();

    /** Counts packed AABB collision-cache misses in {@code WalkNodeEvaluator}. */
    public static final LongAdder PATH_PACKED_CACHE_MISS = new LongAdder();

    /** Counts AABB inputs that could not be packed and fell back to vanilla. */
    public static final LongAdder PATH_PACKED_CACHE_FALLBACK = new LongAdder();

    /** Counts mutable block-pos leases handed out by the {@code NaturalSpawner} hook. */
    public static final LongAdder SPAWNER_POS_LEASE = new LongAdder();

    /** Counts stale leases reclaimed at HEAD on re-entry / unwind. */
    public static final LongAdder SPAWNER_POS_RECLAIM = new LongAdder();

    /** Counts {@code CapabilityDispatcher#areCompatible} early returns due to mismatch. */
    public static final LongAdder CAP_FAST_COMPARE_SHORT_CIRCUIT = new LongAdder();

    /** Counts {@code ResourceLocation.toString()} cache hits. */
    public static final LongAdder RL_TOSTRING_CACHE_HIT = new LongAdder();

    /** Counts {@code ResourceLocation.toString()} cache misses (cold compute + cache fill). */
    public static final LongAdder RL_TOSTRING_CACHE_MISS = new LongAdder();

    /** Counts {@code LiteralContents} cache hits (allocation avoided). */
    public static final LongAdder LITERAL_CONTENTS_CACHE_HIT = new LongAdder();

    /** Counts {@code LiteralContents} cache misses (vanilla path + cache fill). */
    public static final LongAdder LITERAL_CONTENTS_CACHE_MISS = new LongAdder();

    /** Counts {@code TranslatableContents} (no-args/no-fallback form) cache hits. */
    public static final LongAdder TRANSLATABLE_CONTENTS_CACHE_HIT = new LongAdder();

    /** Counts {@code TranslatableContents} cache misses. */
    public static final LongAdder TRANSLATABLE_CONTENTS_CACHE_MISS = new LongAdder();

    /** Counts {@code KeybindContents} cache hits. */
    public static final LongAdder KEYBIND_CONTENTS_CACHE_HIT = new LongAdder();

    /** Counts {@code KeybindContents} cache misses. */
    public static final LongAdder KEYBIND_CONTENTS_CACHE_MISS = new LongAdder();

    private RecoletaCounters() {
        /* counter holder - never instantiated */
    }
}

