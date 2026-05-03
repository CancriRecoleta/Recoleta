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

        b.pop();
        SPEC = b.build();
    }

    private MemoryConfig() {
        /* config holder - never instantiated */
    }
}

