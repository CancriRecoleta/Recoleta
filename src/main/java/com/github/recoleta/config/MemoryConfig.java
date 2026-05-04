package com.github.recoleta.config;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.common.ForgeConfigSpec.BooleanValue;
import net.minecraftforge.common.ForgeConfigSpec.DoubleValue;
import net.minecraftforge.common.ForgeConfigSpec.IntValue;

/**
 * Central configuration holder for Recoleta.
 *
 * <p>The spec is built once at class-load time and registered by
 * {@code ModInit}. Subsystems consume the {@link ForgeConfigSpec.ConfigValue}
 * accessors directly, so a user editing the file will see the change on
 * the next read without restarting the game.</p>
 */
public final class MemoryConfig {

    /** Backing spec built once at class-load time. */
    public static final ForgeConfigSpec SPEC;

    /** Maximum particles kept per render-style (vanilla constant is 16384). */
    public static final IntValue PARTICLE_PER_TYPE_CAP;

    /** Whether the slack trimmer is allowed to compact long-lived collections. */
    public static final BooleanValue ENABLE_SLACK_TRIMMER;

    /** Whether {@code MemoryEvents} should evict soft caches under heap pressure. */
    public static final BooleanValue ENABLE_PRESSURE_EVICTION;

    /** Heap-occupancy ratio at which {@code MemoryEvents} fires (0.50 - 0.99). */
    public static final DoubleValue PRESSURE_RATIO;

    /** Maximum {@link java.lang.ref.Reference} entries drained per tick by {@code IncrementalCleaner}. */
    public static final IntValue REFERENCE_DRAIN_BUDGET;

    /** Soft cap on {@code memory.gc.GenerationalPool} young-generation size. */
    public static final IntValue YOUNG_POOL_CAPACITY;

    /** Soft cap on {@code memory.gc.GenerationalPool} old-generation size. */
    public static final IntValue OLD_POOL_CAPACITY;

    /** Enables packed-long collision cache keys in pathfinding (common). */
    public static final BooleanValue ENABLE_PACKED_AABB_PATH_CACHE;

    /** Enables allocation-free spawn-distance checks in natural spawning. */
    public static final BooleanValue ENABLE_SPAWNER_DISTANCE_ALLOCATION_PATCH;

    /** Enables small initial maps for {@code CompoundTag} creation/loading. */
    public static final BooleanValue ENABLE_COMPOUNDTAG_SMALL_MAPS;

    /** Enables entry-by-entry CapabilityDispatcher comparison (no full CompoundTag build). */
    public static final BooleanValue ENABLE_CAPABILITY_FAST_COMPARE;

    /** Right-sizes block-entity list in chunk packet data on construction. */
    public static final BooleanValue ENABLE_CHUNK_PACKET_RIGHT_SIZE;

    /** Caches {@code ResourceLocation.toString()} output via a {@code SoftLruCache}. */
    public static final BooleanValue ENABLE_RESOURCELOCATION_TOSTRING_CACHE;

    /** Bounded entry count for the {@code ResourceLocation.toString()} cache. */
    public static final IntValue RESOURCELOCATION_TOSTRING_CACHE_SIZE;

    /** Caches {@code LiteralContents} instances by text via a {@code SoftLruCache}. */
    public static final BooleanValue ENABLE_LITERAL_CONTENTS_CACHE;

    /** Bounded entry count for the {@code LiteralContents} cache. */
    public static final IntValue LITERAL_CONTENTS_CACHE_SIZE;

    /** Right-sizes the staging map in {@code SimpleJsonResourceReloadListener.prepare}. */
    public static final BooleanValue ENABLE_RELOAD_LISTENER_RIGHT_SIZE;

    /** Initial capacity for the reload-listener staging map. */
    public static final IntValue RELOAD_LISTENER_STAGING_CAPACITY;

    /** Initial capacity for {@code ModelBakery}'s four staging maps. */
    public static final IntValue MODEL_BAKERY_STAGING_CAPACITY;

    static {
        final ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.comment("Recoleta - memory reduction settings").push("memory");

        PARTICLE_PER_TYPE_CAP = b
                .comment("Maximum particles kept alive per render-style. Vanilla = 16384.",
                        "Lowering this value shrinks the bounded EvictingQueue used by",
                        "ParticleEngine and reclaims a large block of client-side heap.")
                .defineInRange("particlePerTypeCap", 4096, 256, 16384);

        ENABLE_SLACK_TRIMMER = b
                .comment("Periodically call ArrayList#trimToSize() and StringBuilder#trimToSize()",
                        "on registered long-lived containers during low-tick periods.")
                .define("enableSlackTrimmer", true);

        ENABLE_PRESSURE_EVICTION = b
                .comment("Evict soft caches when heap occupancy crosses pressureRatio.")
                .define("enablePressureEviction", true);

        PRESSURE_RATIO = b
                .comment("Heap-occupancy ratio above which soft caches are forcefully evicted.")
                .defineInRange("pressureRatio", 0.85D, 0.50D, 0.99D);

        REFERENCE_DRAIN_BUDGET = b
                .comment("Maximum number of stale Reference entries drained from internal",
                        "ReferenceQueues per game tick. Bounded to keep tick pause low,",
                        "mimicking Shenandoah's concurrent-marking philosophy.")
                .defineInRange("referenceDrainBudgetPerTick", 64, 1, 4096);

        YOUNG_POOL_CAPACITY = b
                .comment("Soft cap on the young generation of GenerationalPool instances.")
                .defineInRange("youngPoolCapacity", 256, 16, 65536);

        OLD_POOL_CAPACITY = b
                .comment("Soft cap on the old generation of GenerationalPool instances.")
                .defineInRange("oldPoolCapacity", 64, 4, 65536);

        ENABLE_PACKED_AABB_PATH_CACHE = b
                .comment("Use PackedAabb long keys for WalkNodeEvaluator collision cache",
                        "to avoid retaining many short-lived AABB instances as map keys.")
                .define("enablePackedAabbPathCache", true);

        ENABLE_SPAWNER_DISTANCE_ALLOCATION_PATCH = b
                .comment("Replace allocation-heavy spawn-distance checks in NaturalSpawner",
                        "with equivalent scalar math (no Vec3/ChunkPos temporaries).")
                .define("enableSpawnerDistanceAllocationPatch", true);

        ENABLE_COMPOUNDTAG_SMALL_MAPS = b
                .comment("Right-size CompoundTag backing maps for common small-NBT workloads.")
                .define("enableCompoundTagSmallMaps", true);

        ENABLE_CAPABILITY_FAST_COMPARE = b
                .comment("Compare CapabilityDispatcher writers entry-by-entry to avoid",
                        "building two full CompoundTag snapshots on every ItemStack",
                        "equality check (used by inventory/container sync paths).")
                .define("enableCapabilityFastCompare", true);

        ENABLE_CHUNK_PACKET_RIGHT_SIZE = b
                .comment("Right-size the block-entity list inside ClientboundLevelChunkPacketData",
                        "so it starts at a small capacity instead of the default 10 slots.")
                .define("enableChunkPacketRightSize", true);

        ENABLE_RESOURCELOCATION_TOSTRING_CACHE = b
                .comment("Cache ResourceLocation.toString() output through a bounded SoftLruCache.",
                        "Vanilla rebuilds the 'namespace:path' string on every call; with this on,",
                        "repeated toString() invocations on the same ResourceLocation return the",
                        "same String instance. Soft references let the JVM evict under heap pressure.")
                .define("enableResourceLocationToStringCache", true);

        RESOURCELOCATION_TOSTRING_CACHE_SIZE = b
                .comment("Maximum entries kept in the ResourceLocation.toString() cache.",
                        "The cache is bounded LRU + soft-referenced; entries beyond this count",
                        "are dropped in least-recently-used order regardless of memory pressure.")
                .defineInRange("resourceLocationToStringCacheSize", 4096, 256, 65536);

        ENABLE_LITERAL_CONTENTS_CACHE = b
                .comment("Canonicalise LiteralContents instances inside Component.literal(text).",
                        "Vanilla allocates a fresh LiteralContents record on every call; with this",
                        "on, repeated calls with the same text share the same LiteralContents object.",
                        "MutableComponent itself is still allocated per call (it is mutable, so",
                        "sharing is unsafe). Soft references let the JVM evict under heap pressure.")
                .define("enableLiteralContentsCache", true);

        LITERAL_CONTENTS_CACHE_SIZE = b
                .comment("Maximum entries kept in the LiteralContents cache.",
                        "1024 covers vanilla's well-known constant labels plus the leading working",
                        "set of mod-supplied strings; raise if your modpack creates many distinct",
                        "literal Components.")
                .defineInRange("literalContentsCacheSize", 1024, 64, 16384);

        ENABLE_RELOAD_LISTENER_RIGHT_SIZE = b
                .comment("Right-size the staging HashMap built inside",
                        "SimpleJsonResourceReloadListener.prepare(). Vanilla starts at the JDK",
                        "default of 16 buckets even though datapack directories typically hold",
                        "hundreds to thousands of entries (recipes, advancements, loot tables,",
                        "tags), forcing 5-8 successive resize copies during every reload. The",
                        "right-sized map avoids that resize churn entirely.")
                .define("enableReloadListenerRightSize", true);

        RELOAD_LISTENER_STAGING_CAPACITY = b
                .comment("Initial capacity for the reload-listener staging HashMap.",
                        "Vanilla recipes alone are ~500; a modpack with 200+ mods can push",
                        "individual reload listeners past 5000. 512 is a reasonable middle",
                        "ground that still covers single-arg listeners (tags/fluids) without",
                        "wasting much when they only hold a few dozen entries.")
                .defineInRange("reloadListenerStagingCapacity", 512, 16, 16384);

        MODEL_BAKERY_STAGING_CAPACITY = b
                .comment("Initial capacity for ModelBakery's four staging maps",
                        "(unbakedCache, bakedCache, topLevelModels, bakedTopLevelModels).",
                        "Vanilla 1.20.1 ships ~3000 baked models; modded packs frequently",
                        "exceed 8000. Default 8192 covers most modded sessions in one",
                        "allocation; raise if your pack pushes past that and you see",
                        "long reload pauses.")
                .defineInRange("modelBakeryStagingCapacity", 8192, 256, 65536);

        b.pop();
        SPEC = b.build();
    }

    private MemoryConfig() {
        /* config holder - never instantiated */
    }

    /**
     * Safe accessor for very-early bootstrap call sites where Forge config
     * has not been loaded yet. Falls back to caller-provided default.
     */
    public static boolean getBooleanOrDefault(final BooleanValue value, final boolean defaultValue) {
        try {
            return value.get();
        } catch (final IllegalStateException ignored) {
            return defaultValue;
        }
    }

    /**
     * CompoundTag paths execute during MC bootstrap before config loading.
     */
    public static boolean enableCompoundTagSmallMaps() {
        return getBooleanOrDefault(ENABLE_COMPOUNDTAG_SMALL_MAPS, true);
    }
}

