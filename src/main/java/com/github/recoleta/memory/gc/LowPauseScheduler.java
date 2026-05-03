package com.github.recoleta.memory.gc;

import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Cooperative scheduler for non-critical maintenance work.
 *
 * <p>Tasks registered here are invoked when the heap-pressure watcher
 * reports a state change. Tasks registered via {@link #onPressure(Runnable)}
 * fire when the JVM crosses the configured occupancy threshold; tasks
 * registered via {@link #onIdle(Runnable)} fire when occupancy returns
 * below it. This mirrors the way a generational concurrent collector
 * only escalates work under pressure and stays passive otherwise.</p>
 */
public final class LowPauseScheduler {

    /**
     * Handle returned by {@link #onIdle(Runnable)} / {@link #onPressure(Runnable)}
     * that lets a caller deregister a task. Without this, dropped caches would
     * be permanently strong-referenced via the static task lists below.
     */
    @FunctionalInterface
    public interface Subscription {
        /** Removes the associated task; idempotent. */
        void cancel();
    }

    /** Tasks invoked when the heap returns to a comfortable occupancy. */
    private static final CopyOnWriteArrayList<Runnable> IDLE_TASKS = new CopyOnWriteArrayList<>();

    /** Tasks invoked when the heap crosses the configured pressure ratio. */
    private static final CopyOnWriteArrayList<Runnable> PRESSURE_TASKS = new CopyOnWriteArrayList<>();

    /** Last published pressure state; used for edge-trigger guarding. */
    private static volatile Boolean LAST_STATE = null;

    private LowPauseScheduler() {
        /* utility class - never instantiated */
    }

    /**
     * Registers a maintenance task to run when heap pressure is low.
     *
     * @param task non-null cheap task; runs at most once per pressure cycle
     * @return cancellation handle; call {@link Subscription#cancel()} when the
     *         owning component is disposed
     */
    public static Subscription onIdle(final Runnable task) {
        IDLE_TASKS.add(task);
        return () -> IDLE_TASKS.remove(task);
    }

    /**
     * Registers a task to run when the JVM is under heap pressure.
     *
     * @param task non-null eviction task
     * @return cancellation handle; call {@link Subscription#cancel()} when the
     *         owning component is disposed
     */
    public static Subscription onPressure(final Runnable task) {
        PRESSURE_TASKS.add(task);
        return () -> PRESSURE_TASKS.remove(task);
    }

    /**
     * Invoked by {@link com.github.recoleta.memory.MemoryEvents} on each
     * pressure-state transition. Edge-triggered: identical consecutive
     * states are silently ignored, so a continuous pressure window does
     * not cause repeated eviction storms.
     *
     * @param underPressure {@code true} if heap occupancy crossed the configured ratio
     */
    public static void dispatch(final boolean underPressure) {
        final Boolean prev = LAST_STATE;
        if (prev != null && prev == underPressure) {
            return;
        }
        LAST_STATE = underPressure;
        final var tasks = underPressure ? PRESSURE_TASKS : IDLE_TASKS;
        for (final Runnable t : tasks) {
            try {
                t.run();
            } catch (final RuntimeException ignored) {
                /* maintenance failure is never fatal */
            }
        }
    }

    /**
     * Forces the next {@link #dispatch(boolean)} call to fire even if
     * the new state matches the previous one. Useful for the
     * {@code /recoleta memory pressure} command which always wants to
     * exercise the registered tasks.
     */
    public static void resetEdgeState() {
        LAST_STATE = null;
    }
}

