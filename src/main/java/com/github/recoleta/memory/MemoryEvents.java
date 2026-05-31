package com.github.recoleta.memory;

import com.github.recoleta.config.MemoryConfig;
import com.github.recoleta.core.ModInit;
import com.github.recoleta.memory.gc.LowPauseScheduler;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

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
 * JVM-level heap-pressure watcher.
 *
 * <p>Two independent detection paths drive
 * {@link LowPauseScheduler#dispatch(boolean)}:</p>
 *
 * <ol>
 *   <li><b>JVM notification listener</b> &mdash; the platform
 *       {@link MemoryMXBean} fires
 *       {@code MEMORY_THRESHOLD_EXCEEDED} when heap occupancy crosses
 *       the configured {@link MemoryConfig#PRESSURE_RATIO}. This is a
 *       low-overhead native callback that gives sub-tick latency on
 *       the upward edge.</li>
 *   <li><b>Tick poll</b> &mdash; every
 *       {@link #POLL_INTERVAL_TICKS} ticks, both the server and the
 *       client thread sample {@link MemoryPoolMXBean#getUsage()} and
 *       dispatch idle / pressure transitions explicitly. The JVM does
 *       not emit a "threshold no longer exceeded" event, so without
 *       this poll the system would sit pinned in {@code dispatch(true)}
 *       forever after the first pressure spike, and
 *       {@code onIdle}-registered tasks
 *       (cache resize-back, pool refill) would never fire.</li>
 * </ol>
 *
 * <p>Hysteresis: the poll considers heap "recovered" only when usage
 * falls below {@link MemoryConfig#PRESSURE_RATIO}
 * &times; {@link #RECOVERY_BAND}, preventing rapid flapping when
 * occupancy oscillates around the threshold.</p>
 */
public final class MemoryEvents {

    /** Tick poll cadence; 20 = ~1 second. */
    private static final int POLL_INTERVAL_TICKS = 20;

    /**
     * Fraction of the pressure threshold below which the heap is
     * considered recovered. {@code 0.85} gives a 15% gap between the
     * pressure-on and pressure-off edges.
     */
    private static final double RECOVERY_BAND = 0.85D;

    private static boolean installed;
    private static NotificationListener listener;

    private static int serverTickCounter;
    private static int clientTickCounter;

    private MemoryEvents() {
        /* utility class - never instantiated */
    }

    /**
     * Installs the JVM notification listener and subscribes the
     * class to the Forge event bus for the tick poll.
     *
     * <p>Safe to call once during mod bootstrap; subsequent calls are
     * no-ops.</p>
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
            if (pool.getType() != MemoryType.HEAP) {
                continue;
            }
            if (!pool.isUsageThresholdSupported()) {
                continue;
            }
            final long max = pool.getUsage().getMax();
            if (max <= 0L) {
                continue;
            }
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

        MinecraftForge.EVENT_BUS.register(MemoryEvents.class);

        installed = true;
    }

    /**
     * Reverses {@link #install()}: removes the JVM notification
     * listener, zeroes the per-pool usage thresholds, and unregisters
     * the tick handlers from the event bus. Safe to call when not
     * installed; subsequent calls are no-ops.
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
            if (pool.getType() != MemoryType.HEAP) {
                continue;
            }
            if (!pool.isUsageThresholdSupported()) {
                continue;
            }
            try {
                pool.setUsageThreshold(0L);
            } catch (final IllegalArgumentException ignored) {
                /* some pools refuse zero; leave them at the prior value */
            }
        }

        try {
            MinecraftForge.EVENT_BUS.unregister(MemoryEvents.class);
        } catch (final RuntimeException ignored) {
            /* already unregistered */
        }

        installed = false;
    }

    @SubscribeEvent
    public static void onServerTick(final TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (++serverTickCounter < POLL_INTERVAL_TICKS) {
            return;
        }
        serverTickCounter = 0;
        pollHeapState();
    }

    @SubscribeEvent
    public static void onClientTick(final TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (++clientTickCounter < POLL_INTERVAL_TICKS) {
            return;
        }
        clientTickCounter = 0;
        pollHeapState();
    }

    /**
     * Samples every heap pool and dispatches a state transition if
     * the aggregate occupancy has crossed either edge.
     *
     * <p>Behaviour:</p>
     * <ul>
     *   <li>If <em>any</em> pool is at or above
     *       {@link MemoryConfig#PRESSURE_RATIO}, dispatch
     *       {@code true}. Same as the JVM listener path; the
     *       edge-triggered guard in {@code LowPauseScheduler.dispatch}
     *       prevents redundant fires.</li>
     *   <li>Else, if <em>every</em> pool is below
     *       {@code PRESSURE_RATIO * RECOVERY_BAND}, dispatch
     *       {@code false}. This is the only path that ever returns
     *       the system to "idle"; the JVM does not emit a recovery
     *       notification.</li>
     *   <li>Else (between recovery and pressure bands), no dispatch.
     *       This is the hysteresis dead band.</li>
     * </ul>
     */
    private static void pollHeapState() {
        if (!installed) {
            return;
        }
        if (!MemoryConfig.ENABLE_PRESSURE_EVICTION.get()) {
            return;
        }

        final double pressureRatio = MemoryConfig.PRESSURE_RATIO.get();
        final double recoveryRatio = pressureRatio * RECOVERY_BAND;

        boolean anyOverPressure = false;
        boolean allBelowRecovery = true;
        boolean anyChecked = false;

        for (final MemoryPoolMXBean pool : ManagementFactory.getMemoryPoolMXBeans()) {
            if (pool.getType() != MemoryType.HEAP) {
                continue;
            }
            if (!pool.isUsageThresholdSupported()) {
                continue;
            }
            final MemoryUsage usage = pool.getUsage();
            final long max = usage.getMax();
            if (max <= 0L) {
                continue;
            }
            anyChecked = true;
            final double ratio = (double) usage.getUsed() / (double) max;
            if (ratio >= pressureRatio) {
                anyOverPressure = true;
            }
            if (ratio >= recoveryRatio) {
                allBelowRecovery = false;
            }
        }

        if (!anyChecked) {
            return;
        }
        if (anyOverPressure) {
            LowPauseScheduler.dispatch(true);
        } else if (allBelowRecovery) {
            LowPauseScheduler.dispatch(false);
        }
    }
}
