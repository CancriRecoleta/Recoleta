package com.github.recoleta.memory;

import com.github.recoleta.config.MemoryConfig;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Reclaims the trailing slack of long-lived collections during low-tick
 * periods.
 *
 * <p>{@link ArrayList}, {@link StringBuilder} and many fastutil
 * containers grow geometrically and never shrink. Over a long play
 * session the resident size of those backing arrays can dwarf the
 * actually-live entries by an order of magnitude. The trimmer iterates
 * its registry every {@code TRIM_INTERVAL_TICKS} ticks (default
 * 600 = 30 s) and calls {@code trimToSize} on each registered container.</p>
 *
 * <h2>Side-aware tracking</h2>
 *
 * <p>Trim is invoked from the same thread that owns the tracked
 * container, because {@link ArrayList#trimToSize()} is not safe under
 * concurrent modification. The trimmer therefore maintains separate
 * server- and client-side registries, each driven by its own tick
 * event:</p>
 *
 * <ul>
 *   <li>{@link #trackArrayList(ArrayList)} / {@link #trackStringBuilder(StringBuilder)}
 *       &mdash; server-thread containers, trimmed on
 *       {@link TickEvent.ServerTickEvent}.</li>
 *   <li>{@link #trackClientArrayList(ArrayList)} &mdash; client-thread
 *       containers (chat history, GUI lists), trimmed on
 *       {@link TickEvent.ClientTickEvent}. {@code ClientTickEvent}
 *       never fires on a dedicated server, so the registry stays
 *       inert there.</li>
 * </ul>
 *
 * <p>Deregistration is automatic once the container is garbage
 * collected (the registries use weak references).</p>
 */
public final class SlackTrimmer {

    /** Trim cadence (ticks). 20 ticks = 1 second. */
    private static final int TRIM_INTERVAL_TICKS = 600;

    private static final CopyOnWriteArrayList<WeakReference<ArrayList<?>>> SERVER_ARRAY_LISTS =
            new CopyOnWriteArrayList<>();
    private static final CopyOnWriteArrayList<WeakReference<StringBuilder>> SERVER_STRING_BUILDERS =
            new CopyOnWriteArrayList<>();
    private static final CopyOnWriteArrayList<WeakReference<ArrayList<?>>> CLIENT_ARRAY_LISTS =
            new CopyOnWriteArrayList<>();
    private static final CopyOnWriteArrayList<TrimmableEntry> SERVER_TRIMMABLES =
            new CopyOnWriteArrayList<>();
    private static final CopyOnWriteArrayList<TrimmableEntry> CLIENT_TRIMMABLES =
            new CopyOnWriteArrayList<>();

    /**
     * Pairs a {@link WeakReference} to a target collection with the
     * action that compacts it. Used for fastutil maps / sets / lists
     * that expose a {@code trim()} method but no common interface, and
     * for any other "give me back my slack" hook the caller wants to
     * register.
     */
    private record TrimmableEntry(WeakReference<?> target, Consumer<Object> action) {
        @SuppressWarnings("unchecked")
        boolean run() {
            final Object live = target.get();
            if (live == null) {
                return false;
            }
            ((Consumer<Object>) action).accept(live);
            return true;
        }
    }

    private static int serverTickCounter;
    private static int clientTickCounter;

    /**
     * Cross-thread pressure-trim request flags. Set by
     * {@link #requestPressureTrim()} (typically wired to
     * {@link com.github.recoleta.memory.gc.LowPauseScheduler#onPressure(Runnable)}
     * which fires from the JVM heap-notification thread or any tick),
     * and consumed by the server / client tick handlers.
     *
     * <p>Volatile so the cross-thread set is visible without any
     * synchronisation; the consume side runs on the trimmable's owning
     * thread, eliminating the concurrent-modification hazard that the
     * old {@link #trimAllNow()} pressure path carried.</p>
     */
    private static volatile boolean serverTrimRequested;
    private static volatile boolean clientTrimRequested;

    private SlackTrimmer() {
        /* utility class - never instantiated */
    }

    /**
     * Subscribes the trimmer to the Forge event bus. Registers handlers
     * for both {@link TickEvent.ServerTickEvent} and
     * {@link TickEvent.ClientTickEvent}; the latter is silently never
     * dispatched on a dedicated server, which is exactly the desired
     * behaviour.
     *
     * @param forgeBus the singleton {@code MinecraftForge.EVENT_BUS}
     */
    public static void register(final IEventBus forgeBus) {
        forgeBus.register(SlackTrimmer.class);
    }

    /**
     * Adds an {@link ArrayList} owned by the server thread to the trim
     * registry.
     *
     * @param list non-null list whose tail capacity should be reclaimed periodically
     */
    public static void trackArrayList(final ArrayList<?> list) {
        SERVER_ARRAY_LISTS.add(new WeakReference<>(list));
    }

    /**
     * Adds an {@link ArrayList} owned by the client (render) thread to
     * the trim registry. Use this for client-side containers such as
     * chat history that can grow large but never shrink, and that
     * <em>must not</em> be trimmed from the server thread.
     *
     * @param list non-null list whose tail capacity should be reclaimed periodically
     */
    public static void trackClientArrayList(final ArrayList<?> list) {
        CLIENT_ARRAY_LISTS.add(new WeakReference<>(list));
    }

    /**
     * Adds a {@link StringBuilder} owned by the server thread to the
     * trim registry.
     *
     * @param sb non-null builder whose tail capacity should be reclaimed periodically
     */
    public static void trackStringBuilder(final StringBuilder sb) {
        SERVER_STRING_BUILDERS.add(new WeakReference<>(sb));
    }

    /**
     * Generic server-side trim hook. Use for fastutil collections
     * ({@code Long2ObjectOpenHashMap}, {@code LongOpenHashSet} etc.)
     * that grow geometrically and expose a {@code trim()} method but
     * do not share a common interface with {@code ArrayList}.
     *
     * <p>The {@code target} is held weakly &mdash; if the collection is
     * collected by the JVM, the entry self-removes on the next trim
     * pass. {@code action} should not capture {@code target} strongly,
     * which is automatic for a method-reference style call:</p>
     *
     * <pre>
     *   SlackTrimmer.trackTrimmable(this.entitiesInLevel,
     *           (LongOpenHashSet s) -&gt; s.trim());
     * </pre>
     *
     * @param target non-null collection instance
     * @param action callback compacting the collection
     * @param <T> target type
     */
    public static <T> void trackTrimmable(final T target, final Consumer<T> action) {
        @SuppressWarnings("unchecked")
        final Consumer<Object> erased = (Consumer<Object>) action;
        SERVER_TRIMMABLES.add(new TrimmableEntry(new WeakReference<>(target), erased));
    }

    /**
     * Client-side variant of {@link #trackTrimmable(Object, Consumer)}.
     *
     * @param target non-null collection instance
     * @param action callback compacting the collection
     * @param <T> target type
     */
    public static <T> void trackClientTrimmable(final T target, final Consumer<T> action) {
        @SuppressWarnings("unchecked")
        final Consumer<Object> erased = (Consumer<Object>) action;
        CLIENT_TRIMMABLES.add(new TrimmableEntry(new WeakReference<>(target), erased));
    }

    @SubscribeEvent
    public static void onServerTick(final TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (!MemoryConfig.ENABLE_SLACK_TRIMMER.get()) {
            return;
        }
        if (serverTrimRequested) {
            serverTrimRequested = false;
            serverTickCounter = 0;
            trimServerNow();
            return;
        }
        if (++serverTickCounter < TRIM_INTERVAL_TICKS) {
            return;
        }
        serverTickCounter = 0;
        trimServerNow();
    }

    @SubscribeEvent
    public static void onClientTick(final TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (!MemoryConfig.ENABLE_SLACK_TRIMMER.get()) {
            return;
        }
        if (clientTrimRequested) {
            clientTrimRequested = false;
            clientTickCounter = 0;
            trimClientNow();
            return;
        }
        if (++clientTickCounter < TRIM_INTERVAL_TICKS) {
            return;
        }
        clientTickCounter = 0;
        trimClientNow();
    }

    /**
     * Records a request to compact every registered container at the
     * earliest safe opportunity. Intended for heap-pressure callbacks
     * fired from off-tick threads (the JVM {@code MemoryMXBean}
     * notification thread in particular).
     *
     * <p>The actual trimming runs on the next server / client tick END
     * phase, on the same thread that owns the registered collections.
     * This eliminates the concurrent-modification hazard that the old
     * {@link #trimAllNow()} pressure path carried, where trimming
     * fastutil hash tables from the wrong thread could tear their
     * internal state.</p>
     *
     * <p>Idempotent: setting an already-set flag is a no-op. Safe to
     * call from any thread.</p>
     */
    public static void requestPressureTrim() {
        serverTrimRequested = true;
        clientTrimRequested = true;
    }

    /**
     * Trims every currently-registered container immediately,
     * regardless of side or tick cadence. Intended for the
     * {@code /recoleta memory compact} command, which always executes
     * on the server thread.
     *
     * <p><b>Thread-safety:</b> only safe to call from the server
     * thread, because the client-side pass mutates client-thread
     * collections concurrently with the render loop. For pressure
     * eviction triggered from off-tick threads, use
     * {@link #requestPressureTrim()} instead, which defers the work
     * to the next safe tick.</p>
     */
    public static void trimAllNow() {
        trimServerNow();
        trimClientNow();
    }

    /**
     * Trims server-side containers only. Safe to call from the server
     * thread.
     */
    public static void trimServerNow() {
        SERVER_ARRAY_LISTS.removeIf(ref -> {
            final ArrayList<?> live = ref.get();
            if (live == null) {
                return true;
            }
            live.trimToSize();
            return false;
        });
        SERVER_STRING_BUILDERS.removeIf(ref -> {
            final StringBuilder live = ref.get();
            if (live == null) {
                return true;
            }
            live.trimToSize();
            return false;
        });
        SERVER_TRIMMABLES.removeIf(entry -> !entry.run());
    }

    /**
     * Trims client-side containers only. Safe to call from the client
     * (render) thread.
     */
    public static void trimClientNow() {
        CLIENT_ARRAY_LISTS.removeIf(ref -> {
            final ArrayList<?> live = ref.get();
            if (live == null) {
                return true;
            }
            live.trimToSize();
            return false;
        });
        CLIENT_TRIMMABLES.removeIf(entry -> !entry.run());
    }
}
