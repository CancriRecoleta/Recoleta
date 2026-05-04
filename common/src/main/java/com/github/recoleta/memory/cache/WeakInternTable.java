package com.github.recoleta.memory.cache;

import com.github.recoleta.memory.gc.IncrementalCleaner;
import com.github.recoleta.memory.gc.LowPauseScheduler;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Weak intern table for {@link String} (and other immutable) values.
 *
 * <p>Minecraft loads tens of thousands of {@code ResourceLocation}s,
 * tag identifiers and JSON keys at world load. Many are duplicates of a
 * handful of canonical strings (for example the {@code "minecraft"}
 * namespace), but the JVM's built-in {@code String#intern} stalls
 * because the string table is sized for global use.</p>
 *
 * <p>This intern table is per-Recoleta and uses {@link WeakReference}
 * values so that a string is collected as soon as the last user
 * releases it. The reference queue is registered with
 * {@link IncrementalCleaner} so eviction never causes a tick spike.</p>
 *
 * @param <T> the interned value type; must implement value-equality semantics
 */
public final class WeakInternTable<T> {

    private static final class KeyedWeakReference<T> extends WeakReference<T> {
        final int hash;
        KeyedWeakReference(final T referent, final ReferenceQueue<? super T> q) {
            super(referent, q);
            this.hash = referent.hashCode();
        }
    }

    private final Map<Integer, ArrayList<KeyedWeakReference<T>>> store = new HashMap<>();
    private final ReferenceQueue<T> queue = new ReferenceQueue<>();
    private final ReentrantLock lock = new ReentrantLock();
    private final IncrementalCleaner.Subscription cleanerSub;
    private final LowPauseScheduler.Subscription pressureSub;

    /**
     * Creates an empty intern table and subscribes its reference queue
     * to the {@link IncrementalCleaner}.
     *
     * <p>Subscriptions are retained as fields so {@link #close()} can
     * deregister them; otherwise the static {@code IncrementalCleaner.JOBS}
     * and {@code LowPauseScheduler.PRESSURE_TASKS} lists would strongly
     * reference this table forever.</p>
     */
    public WeakInternTable() {
        this.cleanerSub = IncrementalCleaner.track(queue, ref -> {
            if (!(ref instanceof KeyedWeakReference<?> keyed)) {
                return;
            }
            lock.lock();
            try {
                final ArrayList<KeyedWeakReference<T>> bucket = store.get(keyed.hash);
                if (bucket == null) {
                    return;
                }
                bucket.removeIf(v -> v == ref);
                if (bucket.isEmpty()) {
                    store.remove(keyed.hash);
                }
            } finally {
                lock.unlock();
            }
        });
        this.pressureSub = LowPauseScheduler.onPressure(this::clear);
    }

    /**
     * Returns the canonical instance for {@code candidate}, inserting it
     * if no equal value is currently held.
     *
     * @param candidate non-null value to canonicalise
     * @return the canonical, weakly-held instance
     */
    public T intern(final T candidate) {
        lock.lock();
        try {
            final int hash = candidate.hashCode();
            final ArrayList<KeyedWeakReference<T>> bucket = store.computeIfAbsent(hash, unused -> new ArrayList<>(1));
            bucket.removeIf(ref -> ref.get() == null);
            for (final KeyedWeakReference<T> existing : bucket) {
                final T live = existing.get();
                if (candidate.equals(live)) {
                    return live;
                }
            }
            bucket.add(new KeyedWeakReference<>(candidate, queue));
            return candidate;
        } finally {
            lock.unlock();
        }
    }

    /**
     * @return current entry count (including any whose value has been reclaimed but not yet drained)
     */
    public int size() {
        lock.lock();
        try {
            int count = 0;
            for (final ArrayList<KeyedWeakReference<T>> bucket : store.values()) {
                count += bucket.size();
            }
            return count;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Drops all canonical entries. Existing callers keep using their current
     * instances; future calls will repopulate the table.
     */
    public void clear() {
        lock.lock();
        try {
            store.clear();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Drops every entry and deregisters this table from
     * {@link IncrementalCleaner} and {@link LowPauseScheduler}, allowing it
     * to be garbage-collected. Idempotent. The table must not be used after
     * {@code close()} returns.
     */
    public void close() {
        cleanerSub.cancel();
        pressureSub.cancel();
        clear();
    }
}

