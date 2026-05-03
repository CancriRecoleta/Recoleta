package com.github.recoleta.memory;

import com.github.recoleta.config.MemoryConfig;
import com.github.recoleta.core.ModInit;
import com.github.recoleta.memory.gc.LowPauseScheduler;

import javax.management.NotificationEmitter;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryNotificationInfo;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryType;

/**
 * JVM-level heap-pressure watcher.
 *
 * <p>Subscribes to {@link MemoryMXBean} usage-threshold notifications on
 * every old-generation pool and forwards crossing events to
 * {@link LowPauseScheduler}, which then runs the registered eviction
 * callbacks (soft caches, generational pools, intern tables).</p>
 *
 * <p>Because the JVM emits these notifications natively (no polling
 * loop), the watcher imposes effectively zero steady-state overhead
 * and only fires when the heap actually approaches the configured
 * {@link MemoryConfig#PRESSURE_RATIO}.</p>
 */
public final class MemoryEvents {

    private static boolean installed;

    private MemoryEvents() {
        /* utility class - never instantiated */
    }

    /**
     * Installs the watcher on every eligible old-generation pool. Safe
     * to call once during mod bootstrap; subsequent calls are no-ops.
     */
    public static synchronized void install() {
        if (installed) {
            return;
        }
        if (!MemoryConfig.ENABLE_PRESSURE_EVICTION.get()) {
            return;
        }
        final MemoryMXBean mem = ManagementFactory.getMemoryMXBean();
        if (!(mem instanceof NotificationEmitter emitter)) {
            ModInit.LOG.warn("MemoryMXBean does not implement NotificationEmitter; pressure eviction disabled.");
            return;
        }

        for (final MemoryPoolMXBean pool : ManagementFactory.getMemoryPoolMXBeans()) {
            if (pool.getType() != MemoryType.HEAP) continue;
            if (!pool.isUsageThresholdSupported()) continue;
            final long max = pool.getUsage().getMax();
            if (max <= 0L) continue;
            final long threshold = (long) (max * MemoryConfig.PRESSURE_RATIO.get());
            pool.setUsageThreshold(threshold);
            ModInit.LOG.info("Recoleta pressure threshold on {} = {} bytes ({}%)",
                    pool.getName(), threshold, (int) (MemoryConfig.PRESSURE_RATIO.get() * 100));
        }

        emitter.addNotificationListener((notification, handback) -> {
            if (MemoryNotificationInfo.MEMORY_THRESHOLD_EXCEEDED.equals(notification.getType())
                    || MemoryNotificationInfo.MEMORY_COLLECTION_THRESHOLD_EXCEEDED.equals(notification.getType())) {
                ModInit.LOG.warn("Heap pressure threshold crossed - evicting Recoleta soft caches.");
                LowPauseScheduler.dispatch(true);
            }
        }, null, null);
        installed = true;
    }
}

