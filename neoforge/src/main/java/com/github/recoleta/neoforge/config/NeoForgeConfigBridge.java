package com.github.recoleta.neoforge.config;

import com.github.recoleta.Recoleta;
import com.github.recoleta.config.MemoryConfig;
import com.github.recoleta.core.RecoletaBootstrap;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.common.ModConfigSpec.BooleanValue;
import net.neoforged.neoforge.common.ModConfigSpec.DoubleValue;
import net.neoforged.neoforge.common.ModConfigSpec.IntValue;

/**
 * NeoForge-side config spec. Pushes values into the loader-agnostic
 * {@link MemoryConfig} holder on every load / reload.
 */
public final class NeoForgeConfigBridge {

    public static final ModConfigSpec SPEC;

    private static final IntValue PARTICLE_PER_TYPE_CAP;
    private static final BooleanValue ENABLE_SLACK_TRIMMER;
    private static final BooleanValue ENABLE_PRESSURE_EVICTION;
    private static final DoubleValue PRESSURE_RATIO;
    private static final IntValue REFERENCE_DRAIN_BUDGET;
    private static final IntValue YOUNG_POOL_CAPACITY;
    private static final IntValue OLD_POOL_CAPACITY;
    private static final BooleanValue ENABLE_PACKED_AABB_PATH_CACHE;
    private static final BooleanValue ENABLE_SPAWNER_DISTANCE_ALLOCATION_PATCH;
    private static final BooleanValue ENABLE_COMPOUNDTAG_SMALL_MAPS;
    private static final BooleanValue ENABLE_CHUNK_PACKET_RIGHT_SIZE;
    private static final BooleanValue ENABLE_RESOURCELOCATION_TOSTRING_CACHE;
    private static final IntValue RESOURCELOCATION_TOSTRING_CACHE_SIZE;
    private static final BooleanValue ENABLE_LITERAL_CONTENTS_CACHE;
    private static final IntValue LITERAL_CONTENTS_CACHE_SIZE;
    private static final BooleanValue ENABLE_RELOAD_LISTENER_RIGHT_SIZE;
    private static final IntValue RELOAD_LISTENER_STAGING_CAPACITY;
    private static final IntValue MODEL_BAKERY_STAGING_CAPACITY;
    private static final BooleanValue ENABLE_STYLE_INTERN;

    static {
        final ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.comment("Recoleta - memory reduction settings").push("memory");

        PARTICLE_PER_TYPE_CAP = b
                .comment("Maximum particles kept alive per render-style. Vanilla = 16384.")
                .defineInRange("particlePerTypeCap", 4096, 256, 16384);

        ENABLE_SLACK_TRIMMER = b
                .comment("Periodically trim long-lived collections during low-tick periods.")
                .define("enableSlackTrimmer", true);

        ENABLE_PRESSURE_EVICTION = b
                .comment("Evict soft caches when heap occupancy crosses pressureRatio.")
                .define("enablePressureEviction", true);

        PRESSURE_RATIO = b
                .comment("Heap-occupancy ratio above which soft caches are forcefully evicted.")
                .defineInRange("pressureRatio", 0.85D, 0.50D, 0.99D);

        REFERENCE_DRAIN_BUDGET = b
                .comment("Maximum stale Reference entries drained per tick from internal queues.")
                .defineInRange("referenceDrainBudgetPerTick", 64, 1, 4096);

        YOUNG_POOL_CAPACITY = b
                .comment("Soft cap on the young generation of GenerationalPool instances.")
                .defineInRange("youngPoolCapacity", 256, 16, 65536);

        OLD_POOL_CAPACITY = b
                .comment("Soft cap on the old generation of GenerationalPool instances.")
                .defineInRange("oldPoolCapacity", 64, 4, 65536);

        ENABLE_PACKED_AABB_PATH_CACHE = b
                .comment("Use PackedAabb long keys for WalkNodeEvaluator collision cache.")
                .define("enablePackedAabbPathCache", true);

        ENABLE_SPAWNER_DISTANCE_ALLOCATION_PATCH = b
                .comment("Replace allocation-heavy spawn-distance checks with scalar math.")
                .define("enableSpawnerDistanceAllocationPatch", true);

        ENABLE_COMPOUNDTAG_SMALL_MAPS = b
                .comment("Right-size CompoundTag backing maps for common small-NBT workloads.")
                .define("enableCompoundTagSmallMaps", true);

        ENABLE_CHUNK_PACKET_RIGHT_SIZE = b
                .comment("Right-size the block-entity list inside ClientboundLevelChunkPacketData.")
                .define("enableChunkPacketRightSize", true);

        ENABLE_RESOURCELOCATION_TOSTRING_CACHE = b
                .comment("Cache ResourceLocation.toString() output through a bounded SoftLruCache.")
                .define("enableResourceLocationToStringCache", true);

        RESOURCELOCATION_TOSTRING_CACHE_SIZE = b
                .comment("Maximum entries kept in the ResourceLocation.toString() cache.")
                .defineInRange("resourceLocationToStringCacheSize", 4096, 256, 65536);

        ENABLE_LITERAL_CONTENTS_CACHE = b
                .comment("Canonicalise LiteralContents instances inside Component.literal(text).")
                .define("enableLiteralContentsCache", true);

        LITERAL_CONTENTS_CACHE_SIZE = b
                .comment("Maximum entries kept in the LiteralContents cache.")
                .defineInRange("literalContentsCacheSize", 1024, 64, 16384);

        ENABLE_RELOAD_LISTENER_RIGHT_SIZE = b
                .comment("Right-size staging HashMaps in datapack reload listeners.")
                .define("enableReloadListenerRightSize", true);

        RELOAD_LISTENER_STAGING_CAPACITY = b
                .comment("Initial capacity for the reload-listener staging HashMap.")
                .defineInRange("reloadListenerStagingCapacity", 512, 16, 16384);

        MODEL_BAKERY_STAGING_CAPACITY = b
                .comment("Initial capacity for ModelBakery's staging maps.")
                .defineInRange("modelBakeryStagingCapacity", 8192, 256, 65536);

        ENABLE_STYLE_INTERN = b
                .comment("Canonicalise Style instances attached to MutableComponent.")
                .define("enableStyleIntern", true);

        b.pop();
        SPEC = b.build();
    }

    private NeoForgeConfigBridge() {
        /* utility class - never instantiated */
    }

    public static void onConfigLoad(final ModConfigEvent event) {
        final ModConfig config = event.getConfig();
        if (!Recoleta.MODID.equals(config.getModId())) return;
        if (config.getType() != ModConfig.Type.COMMON) return;

        MemoryConfig.particlePerTypeCap = PARTICLE_PER_TYPE_CAP.get();
        MemoryConfig.enableSlackTrimmer = ENABLE_SLACK_TRIMMER.get();
        MemoryConfig.enablePressureEviction = ENABLE_PRESSURE_EVICTION.get();
        MemoryConfig.pressureRatio = PRESSURE_RATIO.get();
        MemoryConfig.referenceDrainBudget = REFERENCE_DRAIN_BUDGET.get();
        MemoryConfig.youngPoolCapacity = YOUNG_POOL_CAPACITY.get();
        MemoryConfig.oldPoolCapacity = OLD_POOL_CAPACITY.get();
        MemoryConfig.enablePackedAabbPathCache = ENABLE_PACKED_AABB_PATH_CACHE.get();
        MemoryConfig.enableSpawnerDistanceAllocationPatch = ENABLE_SPAWNER_DISTANCE_ALLOCATION_PATCH.get();
        MemoryConfig.enableCompoundTagSmallMaps = ENABLE_COMPOUNDTAG_SMALL_MAPS.get();
        MemoryConfig.enableChunkPacketRightSize = ENABLE_CHUNK_PACKET_RIGHT_SIZE.get();
        MemoryConfig.enableResourceLocationToStringCache = ENABLE_RESOURCELOCATION_TOSTRING_CACHE.get();
        MemoryConfig.resourceLocationToStringCacheSize = RESOURCELOCATION_TOSTRING_CACHE_SIZE.get();
        MemoryConfig.enableLiteralContentsCache = ENABLE_LITERAL_CONTENTS_CACHE.get();
        MemoryConfig.literalContentsCacheSize = LITERAL_CONTENTS_CACHE_SIZE.get();
        MemoryConfig.enableReloadListenerRightSize = ENABLE_RELOAD_LISTENER_RIGHT_SIZE.get();
        MemoryConfig.reloadListenerStagingCapacity = RELOAD_LISTENER_STAGING_CAPACITY.get();
        MemoryConfig.modelBakeryStagingCapacity = MODEL_BAKERY_STAGING_CAPACITY.get();
        MemoryConfig.enableStyleIntern = ENABLE_STYLE_INTERN.get();
        // capability fast-compare is forge-only; NeoForge replaced capabilities with data attachments
        MemoryConfig.enableCapabilityFastCompare = false;

        if (event instanceof ModConfigEvent.Loading) {
            RecoletaBootstrap.onConfigLoaded();
        }
    }
}
