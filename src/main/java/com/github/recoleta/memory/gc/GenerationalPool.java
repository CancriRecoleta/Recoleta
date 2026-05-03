package com.github.recoleta.memory.gc;

import com.github.recoleta.config.MemoryConfig;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.Supplier;

/**
 * Two-tier object pool inspired by generational garbage collection.
 *
 * <p>Borrowed instances live first in a small <i>young</i> deque; on
 * release they are pushed back to the young tier. When the young tier
 * is full the oldest entry is promoted into a larger <i>old</i> tier;
 * when the old tier is full the entry is dropped and left for the
 * underlying GC to reclaim. This biases reuse towards the most
 * recently touched instances (the same locality assumption that makes
 * generational GCs effective) while bounding the resident pool size.</p>
 *
 * <p>Capacities are read from {@link MemoryConfig#YOUNG_POOL_CAPACITY}
 * and {@link MemoryConfig#OLD_POOL_CAPACITY}.</p>
 *
 * <p>Instances are <strong>not</strong> thread-safe; create one pool
 * per thread (or wrap externally) to avoid contention.</p>
 *
 * @param <T> the pooled object type; must be safely reusable after
 *            {@link #release(Object)}
 */
public final class GenerationalPool<T> {

    /** Factory used when both tiers are empty. */
    private final Supplier<T> factory;

    /** Optional reset hook invoked just before re-acquisition. */
    private final java.util.function.Consumer<T> resetHook;

    private final Deque<T> young;
    private final Deque<T> old;

    /**
     * Creates a generational pool with no reset hook.
     *
     * @param factory non-null supplier of fresh instances
     */
    public GenerationalPool(final Supplier<T> factory) {
        this(factory, t -> { /* no-op */ });
    }

    /**
     * Creates a generational pool that resets borrowed instances before
     * returning them to the caller (for example to clear collection
     * contents).
     *
     * @param factory   non-null supplier of fresh instances
     * @param resetHook non-null reset callback applied during {@link #acquire()}
     */
    public GenerationalPool(final Supplier<T> factory,
                            final java.util.function.Consumer<T> resetHook) {
        this.factory = factory;
        this.resetHook = resetHook;
        this.young = new ArrayDeque<>(MemoryConfig.YOUNG_POOL_CAPACITY.get());
        this.old = new ArrayDeque<>(MemoryConfig.OLD_POOL_CAPACITY.get());
    }

    /**
     * Borrows an instance from the pool, allocating a fresh one only if
     * both tiers are empty.
     *
     * @return a ready-to-use instance
     */
    public T acquire() {
        T t = young.pollFirst();
        if (t == null) {
            t = old.pollFirst();
        }
        if (t == null) {
            t = factory.get();
        }
        resetHook.accept(t);
        return t;
    }

    /**
     * Returns an instance to the pool. The instance is pushed onto the
     * young tier; if the young tier is full its oldest entry is promoted
     * to the old tier; if the old tier is also full the oldest old entry
     * is dropped and left to the JVM GC.
     *
     * @param instance non-null instance previously produced by {@link #acquire()}
     */
    public void release(final T instance) {
        if (young.size() >= MemoryConfig.YOUNG_POOL_CAPACITY.get()) {
            final T promoted = young.pollLast();
            if (promoted != null) {
                if (old.size() >= MemoryConfig.OLD_POOL_CAPACITY.get()) {
                    old.pollLast();
                }
                old.offerFirst(promoted);
            }
        }
        young.offerFirst(instance);
    }

    /**
     * Drops every cached instance. Intended to be called by
     * {@link com.github.recoleta.memory.MemoryEvents} when the JVM
     * signals heap pressure.
     */
    public void evictAll() {
        young.clear();
        old.clear();
    }

    /**
     * @return current young-tier occupancy
     */
    public int youngSize() {
        return young.size();
    }

    /**
     * @return current old-tier occupancy
     */
    public int oldSize() {
        return old.size();
    }
}

