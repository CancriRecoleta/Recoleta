package com.github.recoleta.memory.cache;

import com.github.recoleta.memory.gc.IncrementalCleaner;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Map;
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

    private final Map<T, KeyedWeakReference<T>> store = new HashMap<>();
    private final ReferenceQueue<T> queue = new ReferenceQueue<>();
    private final ReentrantLock lock = new ReentrantLock();

    /**
     * Creates an empty intern table and subscribes its reference queue
     * to the {@link IncrementalCleaner}.
     */
    public WeakInternTable() {
        IncrementalCleaner.track(queue, ref -> {
            lock.lock();
            try {
                store.values().removeIf(v -> v == ref);
            } finally {
                lock.unlock();
            }
        });
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
            final KeyedWeakReference<T> existing = store.get(candidate);
            if (existing != null) {
                final T live = existing.get();
                if (live != null) {
                    return live;
                }
            }
            store.put(candidate, new KeyedWeakReference<>(candidate, queue));
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
            return store.size();
        } finally {
            lock.unlock();
        }
    }
}

