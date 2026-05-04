package com.github.recoleta.memory.gc;

import com.github.recoleta.config.MemoryConfig;
import com.github.recoleta.core.RecoletaBootstrap;

import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Per-tick, bounded-budget drainer for {@link ReferenceQueue}s.
 *
 * <p>{@code IncrementalCleaner} drains <i>at most</i>
 * {@link MemoryConfig#REFERENCE_DRAIN_BUDGET} entries per server tick,
 * spreading the work across many ticks and capping the worst-case
 * pause to a few microseconds.</p>
 */
public final class IncrementalCleaner {

    private record Job(ReferenceQueue<?> queue, Consumer<Reference<?>> onStale) {
    }

    @FunctionalInterface
    public interface Subscription {
        void cancel();
    }

    private static final CopyOnWriteArrayList<Job> JOBS = new CopyOnWriteArrayList<>();

    private IncrementalCleaner() {
        /* utility class - never instantiated */
    }

    public static <T> Subscription track(final ReferenceQueue<T> queue,
                                         final Consumer<Reference<? extends T>> onStale) {
        @SuppressWarnings({"unchecked", "rawtypes"})
        final Consumer<Reference<?>> erased = (Consumer) onStale;
        final Job job = new Job(queue, erased);
        JOBS.add(job);
        return () -> JOBS.remove(job);
    }

    /** Loader bridge: invoke at the end of every server tick. */
    public static void onServerTickEnd() {
        drain(MemoryConfig.REFERENCE_DRAIN_BUDGET.get());
    }

    public static void drainAll() {
        drain(Integer.MAX_VALUE);
    }

    private static void drain(final int budget) {
        for (final Job job : JOBS) {
            int drained = 0;
            Reference<?> ref;
            while (drained < budget && (ref = job.queue.poll()) != null) {
                try {
                    job.onStale.accept(ref);
                } catch (final RuntimeException ex) {
                    RecoletaBootstrap.LOG.warn("IncrementalCleaner callback threw", ex);
                }
                drained++;
            }
        }
    }
}
