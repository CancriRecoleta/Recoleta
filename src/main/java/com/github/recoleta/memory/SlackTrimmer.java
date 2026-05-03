package com.github.recoleta.memory;

import com.github.recoleta.config.MemoryConfig;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Reclaims the trailing slack of long-lived collections during low-tick
 * periods.
 *
 * <p>{@link ArrayList}, {@link StringBuilder} and many fastutil
 * containers grow geometrically and never shrink. Over a long play
 * session the resident size of those backing arrays can dwarf the
 * actually-live entries by an order of magnitude. The trimmer iterates
 * its registry every {@code TRIM_INTERVAL_TICKS} server ticks (default
 * 600 = 30 s) and calls the appropriate {@code trimToSize} method on
 * each registered container.</p>
 *
 * <p>Callers register containers via {@link #trackArrayList(ArrayList)}
 * and friends; deregistration is automatic once the container is
 * garbage collected (the registry uses weak references).</p>
 */
public final class SlackTrimmer {

    /** Trim cadence (server ticks). 20 ticks = 1 second. */
    private static final int TRIM_INTERVAL_TICKS = 600;

    private static final CopyOnWriteArrayList<java.lang.ref.WeakReference<ArrayList<?>>> ARRAY_LISTS =
            new CopyOnWriteArrayList<>();
    private static final CopyOnWriteArrayList<java.lang.ref.WeakReference<StringBuilder>> STRING_BUILDERS =
            new CopyOnWriteArrayList<>();

    private static int tickCounter;

    private SlackTrimmer() {
        /* utility class - never instantiated */
    }

    /**
     * Subscribes the trimmer to the Forge event bus.
     *
     * @param forgeBus the singleton {@code MinecraftForge.EVENT_BUS}
     */
    public static void register(final IEventBus forgeBus) {
        forgeBus.register(SlackTrimmer.class);
    }

    /**
     * Adds an {@link ArrayList} to the trim registry.
     *
     * @param list non-null list whose tail capacity should be reclaimed periodically
     */
    public static void trackArrayList(final ArrayList<?> list) {
        ARRAY_LISTS.add(new java.lang.ref.WeakReference<>(list));
    }

    /**
     * Adds a {@link StringBuilder} to the trim registry.
     *
     * @param sb non-null builder whose tail capacity should be reclaimed periodically
     */
    public static void trackStringBuilder(final StringBuilder sb) {
        STRING_BUILDERS.add(new java.lang.ref.WeakReference<>(sb));
    }

    /**
     * Forge tick callback. Trims registered containers every
     * {@link #TRIM_INTERVAL_TICKS} ticks if the feature is enabled.
     *
     * @param event Forge server tick event
     */
    @SubscribeEvent
    public static void onServerTick(final TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!MemoryConfig.ENABLE_SLACK_TRIMMER.get()) return;
        if (++tickCounter < TRIM_INTERVAL_TICKS) return;
        tickCounter = 0;

        ARRAY_LISTS.removeIf(ref -> {
            final ArrayList<?> live = ref.get();
            if (live == null) return true;
            live.trimToSize();
            return false;
        });
        STRING_BUILDERS.removeIf(ref -> {
            final StringBuilder live = ref.get();
            if (live == null) return true;
            live.trimToSize();
            return false;
        });
    }
}

