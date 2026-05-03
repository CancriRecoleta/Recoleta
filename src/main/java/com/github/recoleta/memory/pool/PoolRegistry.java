package com.github.recoleta.memory.pool;

import com.github.recoleta.memory.gc.GenerationalPool;
import com.github.recoleta.memory.gc.LowPauseScheduler;

import java.lang.ref.WeakReference;
import java.util.Iterator;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Cross-thread registry of every live {@link GenerationalPool} instance.
 *
 * <p>Each thread-local pool registers itself on construction and is
 * tracked here through a {@link WeakReference}. The registry exposes
 * three aggregate operations that are valuable but cannot be done from
 * inside a single per-thread pool:</p>
 *
 * <ul>
 *   <li>{@link #aggregateCachedCount()} - sum of cached instances across
 *       all live pools (e.g. for the {@code /recoleta memory status}
 *       readout).</li>
 *   <li>{@link #trimAll()} - drop only the old-tier deque of every pool;
 *       wired to {@link LowPauseScheduler#onIdle(Runnable)} so cached
 *       slack is released after the heap returns to idle.</li>
 *   <li>{@link #evictAll()} - drop both tiers of every pool; wired to
 *       {@link LowPauseScheduler#onPressure(Runnable)} so heap-pressure
 *       transitions reclaim every pooled instance.</li>
 * </ul>
 *
 * <p>Stale weak references are pruned lazily on every iteration so the
 * registry never holds onto the inaccessible pools of dead threads.</p>
 */
public final class PoolRegistry {

    private static final ConcurrentLinkedQueue<WeakReference<GenerationalPool<?>>> POOLS =
            new ConcurrentLinkedQueue<>();

    /** Guard so {@link #bootstrap()} only wires callbacks once. */
    private static final AtomicBoolean WIRED = new AtomicBoolean(false);

    private PoolRegistry() {
        /* utility class - never instantiated */
    }

    /**
     * Registers a freshly constructed pool. Called from the
     * {@link GenerationalPool} constructor.
     *
     * @param pool the pool instance to track
     */
    public static void register(final GenerationalPool<?> pool) {
        POOLS.add(new WeakReference<>(pool));
    }

    /**
     * Wires the registry into {@link LowPauseScheduler}. Idempotent.
     * Should be called from the mod bootstrap once the config has been
     * accepted, so the per-pool capacities are stable.
     */
    public static void bootstrap() {
        if (WIRED.compareAndSet(false, true)) {
            LowPauseScheduler.onPressure(PoolRegistry::evictAll);
            LowPauseScheduler.onIdle(PoolRegistry::trimAll);
        }
    }

    /**
     * @return total cached instances across every live pool on every
     *         thread; stale weak references are pruned in passing
     */
    public static long aggregateCachedCount() {
        long total = 0L;
        final Iterator<WeakReference<GenerationalPool<?>>> it = POOLS.iterator();
        while (it.hasNext()) {
            final GenerationalPool<?> pool = it.next().get();
            if (pool == null) {
                it.remove();
                continue;
            }
            total += pool.youngSize();
            total += pool.oldSize();
        }
        return total;
    }

    /**
     * Calls {@link GenerationalPool#trim()} on every live pool, releasing
     * only the old tier. Called from idle pressure callbacks.
     */
    public static void trimAll() {
        forEach(GenerationalPool::trim);
    }

    /**
     * Calls {@link GenerationalPool#evictAll()} on every live pool.
     * Called from pressure callbacks.
     */
    public static void evictAll() {
        forEach(GenerationalPool::evictAll);
    }

    private static void forEach(final java.util.function.Consumer<GenerationalPool<?>> action) {
        final Iterator<WeakReference<GenerationalPool<?>>> it = POOLS.iterator();
        while (it.hasNext()) {
            final GenerationalPool<?> pool = it.next().get();
            if (pool == null) {
                it.remove();
                continue;
            }
            try {
                action.accept(pool);
            } catch (final RuntimeException ignored) {
                /* maintenance failure must never propagate */
            }
        }
    }
}

