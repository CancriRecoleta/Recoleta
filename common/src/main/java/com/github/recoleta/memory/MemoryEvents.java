package com.github.recoleta.memory;

import com.github.recoleta.config.MemoryConfig;
import com.github.recoleta.core.RecoletaBootstrap;
import com.github.recoleta.memory.gc.LowPauseScheduler;

import javax.management.ListenerNotFoundException;
import javax.management.NotificationEmitter;
import javax.management.NotificationListener;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryNotificationInfo;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryType;
import java.lang.management.MemoryUsage;

/**
 * JVM-level heap-pressure watcher. Loaders drive the
 * {@link #onServerTickEnd()} / {@link #onClientTickEnd()} hooks at the
 * end of every tick; the JVM-level notification listener is installed
 * once at bootstrap.
 */
public final class MemoryEvents {

    private static final int POLL_INTERVAL_TICKS = 20;
    private static final double RECOVERY_BAND = 0.85D;

    private static boolean installed;
    private static NotificationListener listener;

    private static int serverTickCounter;
    private static int clientTickCounter;

    private MemoryEvents() {
        /* utility class - never instantiated */
    }

    public static synchronized void install() {
        if (installed) return;
        if (!MemoryConfig.ENABLE_PRESSURE_EVICTION.get()) return;

        final MemoryMXBean mem = ManagementFactory.getMemoryMXBean();
        if (!(mem instanceof NotificationEmitter emitter)) {
            RecoletaBootstrap.LOG.warn("MemoryMXBean does not implement NotificationEmitter; pressure eviction disabled.");
            return;
        }

        for (final MemoryPoolMXBean pool : ManagementFactory.getMemoryPoolMXBeans()) {
            if (pool.getType() != MemoryType.HEAP) continue;
            if (!pool.isUsageThresholdSupported()) continue;
            final long max = pool.getUsage().getMax();
            if (max <= 0L) continue;
            final long threshold = (long) (max * MemoryConfig.PRESSURE_RATIO.get());
            pool.setUsageThreshold(threshold);
            RecoletaBootstrap.LOG.info("Recoleta pressure threshold on {} = {} bytes ({}%)",
                    pool.getName(), threshold, (int) (MemoryConfig.PRESSURE_RATIO.get() * 100));
        }

        listener = (notification, handback) -> {
            if (MemoryNotificationInfo.MEMORY_THRESHOLD_EXCEEDED.equals(notification.getType())
                    || MemoryNotificationInfo.MEMORY_COLLECTION_THRESHOLD_EXCEEDED.equals(notification.getType())) {
                RecoletaBootstrap.LOG.warn("Heap pressure threshold crossed - evicting Recoleta soft caches.");
                LowPauseScheduler.dispatch(true);
            }
        };
        emitter.addNotificationListener(listener, null, null);
        installed = true;
    }

    public static synchronized void uninstall() {
        if (!installed) return;
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
                /* some pools refuse zero; leave them */
            }
        }
        installed = false;
    }

    public static void onServerTickEnd() {
        if (++serverTickCounter < POLL_INTERVAL_TICKS) return;
        serverTickCounter = 0;
        pollHeapState();
    }

    public static void onClientTickEnd() {
        if (++clientTickCounter < POLL_INTERVAL_TICKS) return;
        clientTickCounter = 0;
        pollHeapState();
    }

    private static void pollHeapState() {
        if (!installed) return;
        if (!MemoryConfig.ENABLE_PRESSURE_EVICTION.get()) return;

        final double pressureRatio = MemoryConfig.PRESSURE_RATIO.get();
        final double recoveryRatio = pressureRatio * RECOVERY_BAND;

        boolean anyOverPressure = false;
        boolean allBelowRecovery = true;
        boolean anyChecked = false;

        for (final MemoryPoolMXBean pool : ManagementFactory.getMemoryPoolMXBeans()) {
            if (pool.getType() != MemoryType.HEAP) continue;
            if (!pool.isUsageThresholdSupported()) continue;
            final MemoryUsage usage = pool.getUsage();
            final long max = usage.getMax();
            if (max <= 0L) continue;
            anyChecked = true;
            final double ratio = (double) usage.getUsed() / (double) max;
            if (ratio >= pressureRatio) anyOverPressure = true;
            if (ratio >= recoveryRatio) allBelowRecovery = false;
        }

        if (!anyChecked) return;
        if (anyOverPressure) {
            LowPauseScheduler.dispatch(true);
        } else if (allBelowRecovery) {
            LowPauseScheduler.dispatch(false);
        }
    }
}
