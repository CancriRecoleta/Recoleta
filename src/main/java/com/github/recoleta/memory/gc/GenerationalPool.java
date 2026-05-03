package com.github.recoleta.memory.gc;

import com.github.recoleta.config.MemoryConfig;
import com.github.recoleta.memory.pool.PoolRegistry;
import net.minecraftforge.common.ForgeConfigSpec.IntValue;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Two-tier object pool inspired by generational garbage collection.
 *
 * <p>Borrowed instances live first in a small <i>young</i> deque; on
 * release they are pushed back to the young tier. When the young tier
 * is full the oldest entry is promoted into a larger <i>old</i> tier;
 * when the old tier is full the entry is dropped and left for the
 * underlying GC to reclaim.</p>
 *
 * <p>Capacities are read once during construction with a bootstrap-safe
 * fallback &mdash; this avoids the per-{@code release} {@code MemoryConfig.get()}
 * call (which would also throw before the config is loaded) and keeps
 * the hot path branch-free.</p>
 *
 * <p>Each instance auto-registers itself with {@link PoolRegistry} so
 * pressure-driven eviction and idle-driven trimming can reach it
 * without explicit wiring at the call site.</p>
 *
 * <p>Instances are <strong>not</strong> thread-safe; create one pool
 * per thread (or wrap externally) to avoid contention.</p>
 *
 * @param <T> the pooled object type
 */
public final class GenerationalPool<T> {

    /** Defaults used if the config spec has not been loaded yet. */
    private static final int DEFAULT_YOUNG_CAPACITY = 256;
    private static final int DEFAULT_OLD_CAPACITY = 64;

    private final Supplier<T> factory;
    private final Consumer<T> resetHook;

    /** Cached at construction; avoids per-call config lookups. */
    private final int youngCapacity;
    private final int oldCapacity;

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
     * returning them to the caller.
     *
     * @param factory   non-null supplier of fresh instances
     * @param resetHook non-null reset callback applied during {@link #acquire()}
     */
    public GenerationalPool(final Supplier<T> factory,
                            final Consumer<T> resetHook) {
        this.factory = factory;
        this.resetHook = resetHook;
        this.youngCapacity = readOrDefault(MemoryConfig.YOUNG_POOL_CAPACITY, DEFAULT_YOUNG_CAPACITY);
        this.oldCapacity = readOrDefault(MemoryConfig.OLD_POOL_CAPACITY, DEFAULT_OLD_CAPACITY);
        this.young = new ArrayDeque<>(Math.min(youngCapacity, 32));
        this.old = new ArrayDeque<>(Math.min(oldCapacity, 16));
        PoolRegistry.register(this);
    }

    private static int readOrDefault(final IntValue value, final int fallback) {
        try {
            return value.get();
        } catch (final IllegalStateException ignored) {
            return fallback;
        }
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
        if (young.size() >= youngCapacity) {
            final T promoted = young.pollLast();
            if (promoted != null) {
                if (old.size() >= oldCapacity) {
                    old.pollLast();
                }
                old.offerFirst(promoted);
            }
        }
        young.offerFirst(instance);
    }

    /**
     * Drops every cached instance. Wired to
     * {@link LowPauseScheduler#onPressure(Runnable)} via
     * {@link PoolRegistry}.
     */
    public void evictAll() {
        young.clear();
        old.clear();
    }

    /**
     * Drops only the old-tier deque. Wired to
     * {@link LowPauseScheduler#onIdle(Runnable)} via
     * {@link PoolRegistry} so freshly released slack is released
     * once heap pressure has subsided, without sacrificing the
     * fast-path young tier.
     */
    public void trim() {
        old.clear();
    }

    /** @return current young-tier occupancy */
    public int youngSize() {
        return young.size();
    }

    /** @return current old-tier occupancy */
    public int oldSize() {
        return old.size();
    }

    /** @return configured young-tier capacity */
    public int youngCapacity() {
        return youngCapacity;
    }

    /** @return configured old-tier capacity */
    public int oldCapacity() {
        return oldCapacity;
    }
}
