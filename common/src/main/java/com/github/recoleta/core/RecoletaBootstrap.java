package com.github.recoleta.core;

import com.github.recoleta.Recoleta;
import com.github.recoleta.config.MemoryConfig;
import com.github.recoleta.memory.MemoryEvents;
import com.github.recoleta.memory.SlackTrimmer;
import com.github.recoleta.memory.gc.IncrementalCleaner;
import com.github.recoleta.memory.gc.LowPauseScheduler;
import com.github.recoleta.memory.pool.PoolRegistry;
import com.github.recoleta.platform.Services;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Loader-agnostic one-shot init for Recoleta.
 *
 * <p>Each loader's mod entry calls {@link #init()} exactly once after
 * its config has been registered with the platform-specific spec
 * system. The function is idempotent and does no Forge / NeoForge /
 * Fabric API calls.</p>
 */
public final class RecoletaBootstrap {

    /** Shared logger for all Recoleta subsystems. */
    public static final Logger LOG = LoggerFactory.getLogger(Recoleta.MODID);

    private static volatile boolean booted;

    private RecoletaBootstrap() {
        /* utility class - never instantiated */
    }

    public static synchronized void init() {
        if (booted) return;

        PoolRegistry.bootstrap();
        registerPressureCallbacks();

        booted = true;
    }

    /**
     * Called by loader bridges <em>after</em> their config has been
     * loaded so the JVM pressure listener picks up the user-supplied
     * threshold rather than the default.
     */
    public static synchronized void onConfigLoaded() {
        MemoryEvents.install();
        logRuntimeBanner();
    }

    private static void registerPressureCallbacks() {
        LowPauseScheduler.onPressure(SlackTrimmer::trimAllNow);
        LowPauseScheduler.onPressure(IncrementalCleaner::drainAll);
    }

    private static void logRuntimeBanner() {
        final String vmName = System.getProperty("java.vm.name", "?");
        final String vmVersion = System.getProperty("java.vm.version", "?");
        final String gc = java.lang.management.ManagementFactory
                .getGarbageCollectorMXBeans().stream()
                .map(java.lang.management.GarbageCollectorMXBean::getName)
                .reduce((a, b) -> a + "+" + b).orElse("?");

        LOG.info("Recoleta loaded - platform = {}, JVM = {} {}",
                Services.PLATFORM.getPlatformName(), vmName, vmVersion);
        LOG.info("  Active GC                       : {}", gc);
        LOG.info("  Userland packed value encodings : memory.header (JEP 519 port)");
        LOG.info("  Generational pool + cleaner     : memory.gc      (JEP 521 port)");
        LOG.info("  Slack trimmer / pressure evict  : {} / {}",
                MemoryConfig.ENABLE_SLACK_TRIMMER.get(),
                MemoryConfig.ENABLE_PRESSURE_EVICTION.get());
    }
}
