package com.github.recoleta.memory.gc;

import com.github.recoleta.config.MemoryConfig;
import com.github.recoleta.core.ModInit;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Per-tick, bounded-budget drainer for {@link ReferenceQueue}s.
 *
 * <p>Soft- and weak-reference based caches typically expose a
 * {@link ReferenceQueue} that the cache owner has to poll periodically
 * to evict stale entries. Doing the polling in a single batch (or
 * worse, via {@code System.gc()}) creates the kind of long pause that
 * Shenandoah was designed to eliminate.</p>
 *
 * <p>{@code IncrementalCleaner} drains <i>at most</i>
 * {@link MemoryConfig#REFERENCE_DRAIN_BUDGET} entries per server tick,
 * spreading the work across many ticks and capping the worst-case
 * pause to a few microseconds. This is the same low-pause contract
 * Shenandoah enforces in the JVM, ported into the mod loop.</p>
 */
public final class IncrementalCleaner {

    /** Internal holder pairing a queue with its eviction callback. */
    private record Job(ReferenceQueue<?> queue, Consumer<Reference<?>> onStale) {
    }

    private static final CopyOnWriteArrayList<Job> JOBS = new CopyOnWriteArrayList<>();

    private IncrementalCleaner() {
        /* utility class - never instantiated */
    }

    /**
     * Subscribes the cleaner to the Forge event bus.
     *
     * @param forgeBus the singleton {@code MinecraftForge.EVENT_BUS}
     */
    public static void register(final IEventBus forgeBus) {
        forgeBus.register(IncrementalCleaner.class);
    }

    /**
     * Registers a {@link ReferenceQueue} for incremental draining.
     *
     * @param <T>     the referent type
     * @param queue   the queue to drain
     * @param onStale callback invoked once for each evicted reference
     */
    public static <T> void track(final ReferenceQueue<T> queue,
                                 final Consumer<Reference<? extends T>> onStale) {
        @SuppressWarnings({"unchecked", "rawtypes"})
        final Consumer<Reference<?>> erased = (Consumer) onStale;
        JOBS.add(new Job(queue, erased));
    }

    /**
     * Forge tick callback. Drains every registered queue up to the
     * per-tick budget, then yields control back to the game loop.
     *
     * @param event Forge server tick event
     */
    @SubscribeEvent
    public static void onServerTick(final TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        drain(MemoryConfig.REFERENCE_DRAIN_BUDGET.get());
    }

    /**
     * Drains every registered queue completely, ignoring the per-tick
     * budget. Intended for the {@code /recoleta memory compact}
     * command and for pressure callbacks registered on
     * {@link LowPauseScheduler}; not suitable for use inside the
     * server tick loop because it has no upper bound.
     */
    public static void drainAll() {
        drain(Integer.MAX_VALUE);
    }

    /**
     * Common drain implementation parameterised by per-queue budget.
     *
     * @param budget maximum entries to drain from each registered queue
     */
    private static void drain(final int budget) {
        for (final Job job : JOBS) {
            int drained = 0;
            Reference<?> ref;
            while (drained < budget && (ref = job.queue.poll()) != null) {
                try {
                    job.onStale.accept(ref);
                } catch (final RuntimeException ex) {
                    ModInit.LOG.warn("IncrementalCleaner callback threw", ex);
                }
                drained++;
            }
        }
    }
}

