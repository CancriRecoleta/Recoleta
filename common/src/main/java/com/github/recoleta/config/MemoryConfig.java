package com.github.recoleta.config;

/**
 * Loader-agnostic settings holder for Recoleta.
 *
 * <p>Each value is exposed as one of {@link BoolVal}, {@link IntVal} or
 * {@link DoubleVal} so callsites read them as {@code MemoryConfig.X.get()}
 * exactly as they did under the previous {@code ForgeConfigSpec.*Value}
 * API. The backing storage is plain {@code volatile} primitives that
 * loader-specific config bridges push to once their format-specific spec
 * has been parsed (Forge / NeoForge TOML, Fabric properties, etc.).</p>
 *
 * <p>Every field has a sane built-in default, so mixins that fire during
 * Minecraft bootstrap before the config has been loaded still see usable
 * values rather than throwing.</p>
 */
public final class MemoryConfig {

    @FunctionalInterface public interface BoolVal { boolean get(); }
    @FunctionalInterface public interface IntVal { int get(); }
    @FunctionalInterface public interface DoubleVal { double get(); }

    public static volatile int particlePerTypeCap = 4096;
    public static volatile boolean enableSlackTrimmer = true;
    public static volatile boolean enablePressureEviction = true;
    public static volatile double pressureRatio = 0.85D;
    public static volatile int referenceDrainBudget = 64;
    public static volatile int youngPoolCapacity = 256;
    public static volatile int oldPoolCapacity = 64;
    public static volatile boolean enablePackedAabbPathCache = true;
    public static volatile boolean enableSpawnerDistanceAllocationPatch = true;
    public static volatile boolean enableCompoundTagSmallMaps = true;
    public static volatile boolean enableCapabilityFastCompare = true;
    public static volatile boolean enableChunkPacketRightSize = true;
    public static volatile boolean enableResourceLocationToStringCache = true;
    public static volatile int resourceLocationToStringCacheSize = 4096;
    public static volatile boolean enableLiteralContentsCache = true;
    public static volatile int literalContentsCacheSize = 1024;
    public static volatile boolean enableReloadListenerRightSize = true;
    public static volatile int reloadListenerStagingCapacity = 512;
    public static volatile int modelBakeryStagingCapacity = 8192;
    public static volatile boolean enableStyleIntern = true;

    public static final IntVal PARTICLE_PER_TYPE_CAP = () -> particlePerTypeCap;
    public static final BoolVal ENABLE_SLACK_TRIMMER = () -> enableSlackTrimmer;
    public static final BoolVal ENABLE_PRESSURE_EVICTION = () -> enablePressureEviction;
    public static final DoubleVal PRESSURE_RATIO = () -> pressureRatio;
    public static final IntVal REFERENCE_DRAIN_BUDGET = () -> referenceDrainBudget;
    public static final IntVal YOUNG_POOL_CAPACITY = () -> youngPoolCapacity;
    public static final IntVal OLD_POOL_CAPACITY = () -> oldPoolCapacity;
    public static final BoolVal ENABLE_PACKED_AABB_PATH_CACHE = () -> enablePackedAabbPathCache;
    public static final BoolVal ENABLE_SPAWNER_DISTANCE_ALLOCATION_PATCH = () -> enableSpawnerDistanceAllocationPatch;
    public static final BoolVal ENABLE_COMPOUNDTAG_SMALL_MAPS = () -> enableCompoundTagSmallMaps;
    public static final BoolVal ENABLE_CAPABILITY_FAST_COMPARE = () -> enableCapabilityFastCompare;
    public static final BoolVal ENABLE_CHUNK_PACKET_RIGHT_SIZE = () -> enableChunkPacketRightSize;
    public static final BoolVal ENABLE_RESOURCELOCATION_TOSTRING_CACHE = () -> enableResourceLocationToStringCache;
    public static final IntVal RESOURCELOCATION_TOSTRING_CACHE_SIZE = () -> resourceLocationToStringCacheSize;
    public static final BoolVal ENABLE_LITERAL_CONTENTS_CACHE = () -> enableLiteralContentsCache;
    public static final IntVal LITERAL_CONTENTS_CACHE_SIZE = () -> literalContentsCacheSize;
    public static final BoolVal ENABLE_RELOAD_LISTENER_RIGHT_SIZE = () -> enableReloadListenerRightSize;
    public static final IntVal RELOAD_LISTENER_STAGING_CAPACITY = () -> reloadListenerStagingCapacity;
    public static final IntVal MODEL_BAKERY_STAGING_CAPACITY = () -> modelBakeryStagingCapacity;
    public static final BoolVal ENABLE_STYLE_INTERN = () -> enableStyleIntern;

    private MemoryConfig() {
        /* settings holder - never instantiated */
    }

    /**
     * Backwards-compatible early-bootstrap accessor. Defaults are now
     * always materialised, so this just returns {@code value.get()}.
     */
    public static boolean getBooleanOrDefault(final BoolVal value, final boolean defaultValue) {
        try {
            return value.get();
        } catch (final RuntimeException ignored) {
            return defaultValue;
        }
    }

    public static boolean enableCompoundTagSmallMaps() {
        return enableCompoundTagSmallMaps;
    }
}
