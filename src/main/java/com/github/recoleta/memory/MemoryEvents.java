package com.github.recoleta.memory;

import com.github.recoleta.config.MemoryConfig;
import com.github.recoleta.core.ModInit;
import com.github.recoleta.memory.gc.LowPauseScheduler;

import javax.management.ListenerNotFoundException;
import javax.management.NotificationEmitter;
import javax.management.NotificationListener;
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
    private static NotificationListener listener;

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

        listener = (notification, handback) -> {
            if (MemoryNotificationInfo.MEMORY_THRESHOLD_EXCEEDED.equals(notification.getType())
                    || MemoryNotificationInfo.MEMORY_COLLECTION_THRESHOLD_EXCEEDED.equals(notification.getType())) {
                ModInit.LOG.warn("Heap pressure threshold crossed - evicting Recoleta soft caches.");
                LowPauseScheduler.dispatch(true);
            }
        };
        emitter.addNotificationListener(listener, null, null);
        installed = true;
    }

    /**
     * Reverses {@link #install()}: removes the JVM notification listener
     * and zeroes the per-pool usage thresholds. Safe to call when not
     * installed; subsequent calls are no-ops.
     *
     * <p>Without this method the listener (and therefore the mod's
     * classloader) is pinned by the platform {@code MemoryMXBean} for the
     * full JVM lifetime &mdash; harmless for a normal Forge launch but
     * fatal for any host that wants to discard the mod classloader
     * (integration tests, hypothetical hot-reload).</p>
     */
    public static synchronized void uninstall() {
        if (!installed) {
            return;
        }
        final MemoryMXBean mem = ManagementFactory.getMemoryMXBean();
        if (mem instanceof NotificationEmitter emitter && listener != null) {
            try {
                emitter.removeNotificationListener(listener);
            } catch (final ListenerNotFoundException ignored) {
                /* nothing else holds it */
            }
        }
        listener = null;

        for (final MemoryPoolMXBean pool : ManagementFactory.getMemoryPoolMXBeans()) {
            if (pool.getType() != MemoryType.HEAP) continue;
            if (!pool.isUsageThresholdSupported()) continue;
            try {
                pool.setUsageThreshold(0L);
            } catch (final IllegalArgumentException ignored) {
                /* some pools refuse zero; leave them at the prior value */
            }
        }
        installed = false;
    }
}
