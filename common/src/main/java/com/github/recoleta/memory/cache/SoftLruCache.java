package com.github.recoleta.memory.cache;

import com.github.recoleta.memory.gc.IncrementalCleaner;
import com.github.recoleta.memory.gc.LowPauseScheduler;

import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.SoftReference;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Bounded LRU cache whose values are held by {@link SoftReference}.
 *
 * <p>Entries are evicted under any of the following conditions:</p>
 * <ul>
 *   <li>The map size exceeds the current {@code maxEntries}: the
 *       least-recently-used entry is removed (classic LRU behaviour).</li>
 *   <li>The JVM clears the soft reference under heap pressure: the
 *       stale entry is drained by {@link IncrementalCleaner}.</li>
 *   <li>{@link LowPauseScheduler} fires {@code onPressure} after the
 *       JVM crosses the configured occupancy threshold; the cache
 *       shrinks to {@code baseMaxEntries / pressureDivisor} (default 4)
 *       and evicts in LRU order to fit. On {@code onIdle}
 *       it grows back to the configured {@code baseMaxEntries}.</li>
 * </ul>
 *
 * <p>The combination gives an upper bound on map cardinality while
 * letting the JVM reclaim the largest values first when memory gets
 * tight, plus an active "shrink under pressure" loop that does not
 * wait for the GC to opportunistically clear soft references.</p>
 *
 * @param <K> key type
 * @param <V> value type; only ever held softly
 */
public final class SoftLruCache<K, V> {

    /**
     * Default ratio between {@code baseMaxEntries} and the shrink
     * target on heap pressure. {@code 4} means the cache holds 25% of
     * its base size while the JVM is over the pressure threshold.
     */
    private static final int DEFAULT_PRESSURE_DIVISOR = 4;

    /** Internal soft reference that remembers its key for queue-based eviction. */
    private static final class KeyedSoftReference<K, V> extends SoftReference<V> {
        final K key;
        KeyedSoftReference(final K key, final V referent, final ReferenceQueue<? super V> q) {
            super(referent, q);
            this.key = key;
        }
    }

    private final int baseMaxEntries;
    private volatile int maxEntries;
    private final LinkedHashMap<K, KeyedSoftReference<K, V>> store;
    private final ReferenceQueue<V> queue = new ReferenceQueue<>();
    private final ReentrantLock lock = new ReentrantLock();
    private final IncrementalCleaner.Subscription cleanerSub;
    private final LowPauseScheduler.Subscription pressureSub;
    private final LowPauseScheduler.Subscription idleSub;

    /**
     * Creates a cache with the given hard upper bound on cardinality.
     *
     * <p>The {@link IncrementalCleaner} and {@link LowPauseScheduler}
     * subscriptions are retained so {@link #close()} can deregister
     * them; otherwise the static registration lists would strongly
     * reference this cache forever.</p>
     *
     * @param maxEntries strict cardinality cap (must be positive)
     */
    public SoftLruCache(final int maxEntries) {
        if (maxEntries <= 0) {
            throw new IllegalArgumentException("maxEntries must be > 0");
        }
        this.baseMaxEntries = maxEntries;
        this.maxEntries = maxEntries;
        this.store = new LinkedHashMap<>(16, 0.75F, true) {
            @Override
            protected boolean removeEldestEntry(final Map.Entry<K, KeyedSoftReference<K, V>> eldest) {
                return size() > SoftLruCache.this.maxEntries;
            }
        };
        this.cleanerSub = IncrementalCleaner.track(queue, ref -> evictStale(ref));
        this.pressureSub = LowPauseScheduler.onPressure(this::onPressure);
        this.idleSub = LowPauseScheduler.onIdle(this::onIdle);
    }

    /**
     * Stores a value, evicting the LRU entry if necessary.
     *
     * @param key   non-null key
     * @param value non-null value to wrap softly
     */
    public void put(final K key, final V value) {
        lock.lock();
        try {
            store.put(key, new KeyedSoftReference<>(key, value, queue));
        } finally {
            lock.unlock();
        }
    }

    /**
     * @param key non-null lookup key
     * @return the cached value, or {@code null} if missing or already reclaimed
     */
    public V get(final K key) {
        lock.lock();
        try {
            final KeyedSoftReference<K, V> ref = store.get(key);
            if (ref == null) {
                return null;
            }
            final V val = ref.get();
            if (val == null) {
                store.remove(key);
            }
            return val;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Removes a single entry.
     *
     * @param key non-null key
     */
    public void invalidate(final K key) {
        lock.lock();
        try {
            store.remove(key);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Drops every entry. Intended for heap-pressure callbacks registered with
     * {@link com.github.recoleta.memory.gc.LowPauseScheduler}.
     */
    public void invalidateAll() {
        lock.lock();
        try {
            store.clear();
        } finally {
            lock.unlock();
        }
    }

    /**
     * @return current entry count (including entries whose value has been reclaimed but not yet drained)
     */
    public int size() {
        lock.lock();
        try {
            return store.size();
        } finally {
            lock.unlock();
        }
    }

    /**
     * @return the configured baseline cap; reachable cap during
     *         heap-idle periods.
     */
    public int baseMaxEntries() {
        return baseMaxEntries;
    }

    /**
     * @return the cap currently in effect; equals
     *         {@link #baseMaxEntries()} when the JVM is below the
     *         configured pressure threshold.
     */
    public int currentMaxEntries() {
        return maxEntries;
    }

    /**
     * Resizes the cap and immediately evicts in LRU order down to
     * {@code newMax} entries.
     *
     * <p>The natural {@code removeEldestEntry} hook only fires on
     * {@code put}; calling this method explicitly forces eviction so
     * shrink-under-pressure releases tenured heap right away rather
     * than waiting for incidental {@code put} traffic.</p>
     *
     * @param newMax new cap; clamped to a minimum of one
     */
    public void resize(final int newMax) {
        final int target = Math.max(1, newMax);
        lock.lock();
        try {
            this.maxEntries = target;
            if (store.size() <= target) {
                return;
            }
            final Iterator<Map.Entry<K, KeyedSoftReference<K, V>>> it = store.entrySet().iterator();
            // LinkedHashMap with access-order iterates from least-recently-used
            // to most-recently-used, so iterator.remove() drops cold entries
            // first - exactly what we want.
            while (it.hasNext() && store.size() > target) {
                it.next();
                it.remove();
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Drops every entry and deregisters from {@link IncrementalCleaner}
     * and {@link LowPauseScheduler}, allowing this cache to be
     * garbage-collected. Idempotent. The cache must not be used after
     * {@code close()} returns.
     */
    public void close() {
        cleanerSub.cancel();
        pressureSub.cancel();
        idleSub.cancel();
        invalidateAll();
    }

    /**
     * {@link LowPauseScheduler#onPressure(Runnable) onPressure} hook:
     * shrink to {@code baseMaxEntries / DEFAULT_PRESSURE_DIVISOR} and
     * evict accordingly.
     */
    private void onPressure() {
        resize(baseMaxEntries / DEFAULT_PRESSURE_DIVISOR);
    }

    /**
     * {@link LowPauseScheduler#onIdle(Runnable) onIdle} hook: restore
     * the configured cap.
     */
    private void onIdle() {
        resize(baseMaxEntries);
    }

    /**
     * Internal callback handed to {@link IncrementalCleaner}.
     *
     * @param ref reference reported as enqueued
     */
    @SuppressWarnings("unchecked")
    private void evictStale(final Reference<?> ref) {
        if (ref instanceof SoftLruCache.KeyedSoftReference<?, ?> keyed) {
            lock.lock();
            try {
                final K key = (K) keyed.key;
                final KeyedSoftReference<K, V> current = store.get(key);
                if (current == ref) {
                    store.remove(key);
                }
            } finally {
                lock.unlock();
            }
        }
    }
}
