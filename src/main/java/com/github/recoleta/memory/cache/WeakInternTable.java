package com.github.recoleta.memory.cache;

import com.github.recoleta.memory.gc.IncrementalCleaner;
import com.github.recoleta.memory.gc.LowPauseScheduler;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;

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
 * <h2>Concurrency model</h2>
 *
 * <p>Buckets are stored in a {@link ConcurrentHashMap} keyed by
 * {@link Object#hashCode()}. Per-bucket mutation runs inside
 * {@link ConcurrentHashMap#compute(Object, java.util.function.BiFunction)},
 * which holds the bin-level lock for the duration of the lambda. This
 * gives Caffeine-style striped locking essentially for free: independent
 * hash buckets proceed in parallel, replacing the previous single
 * {@code ReentrantLock} that serialised every {@link #intern(Object)}
 * call. The hot {@code ResourceLocation} / {@code CompoundTag} key
 * de-duplication paths therefore scale with thread count instead of
 * pinning on a global mutex.</p>
 *
 * @param <T> the interned value type; must implement value-equality semantics
 */
public final class WeakInternTable<T> {

    /**
     * Weak reference variant that remembers the hash of its referent.
     *
     * <p>The hash is captured at construction so it remains addressable
     * even after the referent has been cleared (the reference queue
     * delivers the bare {@code Reference}, not the original value).
     * Knowing the hash lets the cleaner callback locate the owning
     * bucket without scanning the whole table.</p>
     */
    private static final class KeyedWeakReference<T> extends WeakReference<T> {
        final int hash;

        KeyedWeakReference(final T referent, final ReferenceQueue<? super T> q) {
            super(referent, q);
            this.hash = referent.hashCode();
        }
    }

    /**
     * Outer map keyed by {@link Object#hashCode()}; values are the
     * per-bucket {@link ArrayList}s of weak references.
     *
     * <p>Buckets are mutated only inside
     * {@link ConcurrentHashMap#compute(Object, java.util.function.BiFunction)}
     * lambdas, which serialise concurrent writers on the same hash bin
     * but allow distinct bins to proceed in parallel. Reads via
     * {@link #size()} are weakly consistent and used only for stats.</p>
     */
    private final ConcurrentHashMap<Integer, ArrayList<KeyedWeakReference<T>>> store = new ConcurrentHashMap<>();
    private final ReferenceQueue<T> queue = new ReferenceQueue<>();
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
            store.compute(keyed.hash, (k, bucket) -> {
                if (bucket == null) {
                    return null;
                }
                bucket.removeIf(v -> v == ref);
                return bucket.isEmpty() ? null : bucket;
            });
        });
        this.pressureSub = LowPauseScheduler.onPressure(this::clear);
    }

    /**
     * Returns the canonical instance for {@code candidate}, inserting it
     * if no equal value is currently held.
     *
     * <p>The bucket is mutated atomically inside
     * {@link ConcurrentHashMap#compute(Object, java.util.function.BiFunction)},
     * so concurrent callers on different hash buckets do not block one
     * another and concurrent callers on the same bucket get a single
     * canonical winner.</p>
     *
     * @param candidate non-null value to canonicalise
     * @return the canonical, weakly-held instance
     */
    @SuppressWarnings("unchecked")
    public T intern(final T candidate) {
        final int hash = candidate.hashCode();
        final Object[] result = new Object[1];
        store.compute(hash, (k, bucket) -> {
            if (bucket == null) {
                bucket = new ArrayList<>(1);
            }
            // Drop entries whose referent has been collected. Walk
            // tail-to-head so removals do not perturb iteration of the
            // matching scan that follows.
            for (int i = bucket.size() - 1; i >= 0; i--) {
                if (bucket.get(i).get() == null) {
                    bucket.remove(i);
                }
            }
            for (final KeyedWeakReference<T> existing : bucket) {
                final T live = existing.get();
                if (live != null && candidate.equals(live)) {
                    result[0] = live;
                    return bucket;
                }
            }
            bucket.add(new KeyedWeakReference<>(candidate, queue));
            result[0] = candidate;
            return bucket;
        });
        return (T) result[0];
    }

    /**
     * @return current entry count (including any whose value has been reclaimed but not yet drained)
     */
    public int size() {
        int count = 0;
        for (final ArrayList<KeyedWeakReference<T>> bucket : store.values()) {
            count += bucket.size();
        }
        return count;
    }

    /**
     * Drops all canonical entries. Existing callers keep using their current
     * instances; future calls will repopulate the table.
     */
    public void clear() {
        store.clear();
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
