package com.github.recoleta.memory;

import com.github.recoleta.config.MemoryConfig;

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
 * actually-live entries by an order of magnitude. The trimmer maintains
 * separate server- and client-side registries; loaders drive the
 * {@link #onServerTickEnd()} and {@link #onClientTickEnd()} hooks at
 * the end of each tick.</p>
 *
 * <h2>Side-aware tracking</h2>
 *
 * <p>Trim is invoked from the same thread that owns the tracked
 * container, because {@link ArrayList#trimToSize()} is not safe under
 * concurrent modification. The trimmer therefore maintains separate
 * server- and client-side registries:</p>
 *
 * <ul>
 *   <li>{@link #trackArrayList(ArrayList)} / {@link #trackStringBuilder(StringBuilder)}
 *       &mdash; server-thread containers, trimmed by {@link #onServerTickEnd()}.</li>
 *   <li>{@link #trackClientArrayList(ArrayList)} &mdash; client-thread
 *       containers (chat history, GUI lists), trimmed by
 *       {@link #onClientTickEnd()}.</li>
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

    private record TrimmableEntry(WeakReference<?> target, Consumer<Object> action) {
        @SuppressWarnings("unchecked")
        boolean run() {
            final Object live = target.get();
            if (live == null) return false;
            ((Consumer<Object>) action).accept(live);
            return true;
        }
    }

    private static int serverTickCounter;
    private static int clientTickCounter;

    private SlackTrimmer() {
        /* utility class - never instantiated */
    }

    public static void trackArrayList(final ArrayList<?> list) {
        SERVER_ARRAY_LISTS.add(new WeakReference<>(list));
    }

    public static void trackClientArrayList(final ArrayList<?> list) {
        CLIENT_ARRAY_LISTS.add(new WeakReference<>(list));
    }

    public static void trackStringBuilder(final StringBuilder sb) {
        SERVER_STRING_BUILDERS.add(new WeakReference<>(sb));
    }

    public static <T> void trackTrimmable(final T target, final Consumer<T> action) {
        @SuppressWarnings("unchecked")
        final Consumer<Object> erased = (Consumer<Object>) action;
        SERVER_TRIMMABLES.add(new TrimmableEntry(new WeakReference<>(target), erased));
    }

    public static <T> void trackClientTrimmable(final T target, final Consumer<T> action) {
        @SuppressWarnings("unchecked")
        final Consumer<Object> erased = (Consumer<Object>) action;
        CLIENT_TRIMMABLES.add(new TrimmableEntry(new WeakReference<>(target), erased));
    }

    /**
     * Loader bridge: invoked at the end of every server tick. Cadence
     * gating and config check live here so the per-loader subscriber
     * is a single delegating call.
     */
    public static void onServerTickEnd() {
        if (!MemoryConfig.ENABLE_SLACK_TRIMMER.get()) return;
        if (++serverTickCounter < TRIM_INTERVAL_TICKS) return;
        serverTickCounter = 0;
        trimServerNow();
    }

    /** Client variant of {@link #onServerTickEnd()}. */
    public static void onClientTickEnd() {
        if (!MemoryConfig.ENABLE_SLACK_TRIMMER.get()) return;
        if (++clientTickCounter < TRIM_INTERVAL_TICKS) return;
        clientTickCounter = 0;
        trimClientNow();
    }

    public static void trimAllNow() {
        trimServerNow();
        trimClientNow();
    }

    public static void trimServerNow() {
        SERVER_ARRAY_LISTS.removeIf(ref -> {
            final ArrayList<?> live = ref.get();
            if (live == null) return true;
            live.trimToSize();
            return false;
        });
        SERVER_STRING_BUILDERS.removeIf(ref -> {
            final StringBuilder live = ref.get();
            if (live == null) return true;
            live.trimToSize();
            return false;
        });
        SERVER_TRIMMABLES.removeIf(entry -> !entry.run());
    }

    public static void trimClientNow() {
        CLIENT_ARRAY_LISTS.removeIf(ref -> {
            final ArrayList<?> live = ref.get();
            if (live == null) return true;
            live.trimToSize();
            return false;
        });
        CLIENT_TRIMMABLES.removeIf(entry -> !entry.run());
    }
}
