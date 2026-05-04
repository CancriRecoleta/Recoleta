package com.github.recoleta.command;

import com.github.recoleta.Recoleta;
import com.github.recoleta.config.MemoryConfig;
import com.github.recoleta.memory.RecoletaCounters;
import com.github.recoleta.memory.SlackTrimmer;
import com.github.recoleta.memory.cache.RecoletaCaches;
import com.github.recoleta.memory.cache.RecoletaInterns;
import com.github.recoleta.memory.gc.IncrementalCleaner;
import com.github.recoleta.memory.gc.LowPauseScheduler;
import com.github.recoleta.memory.pool.MutableBlockPosPool;
import com.github.recoleta.memory.pool.PoolRegistry;
import com.github.recoleta.memory.pool.Vec3Pool;
import com.github.recoleta.mixin.common.CapabilityDispatcherFastCompareMixin;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryUsage;
import java.util.Locale;

/**
 * Implements the {@code /recoleta memory} command tree.
 *
 * <p>Subcommands:</p>
 * <ul>
 *   <li>{@code /recoleta memory status} - prints the JVM identity, the
 *       active GC, current heap occupancy, the configured pressure
 *       threshold, and the size of the shared intern table.</li>
 *   <li>{@code /recoleta memory compact} - forces an immediate slack
 *       trim plus a full incremental-cleaner drain, then reports the
 *       reclaimed bytes.</li>
 *   <li>{@code /recoleta memory pressure} - dispatches the registered
 *       pressure-eviction callbacks as if the JVM had crossed the
 *       configured threshold. Useful for testing the whole pipeline.</li>
 * </ul>
 *
 * <p>All subcommands require permission level 2 (op) so that a normal
 * survival player cannot spam them.</p>
 */
public final class MemoryCommand {

    /** Brigadier permission level required to invoke any subcommand. */
    private static final int PERMISSION_LEVEL = 2;

    private MemoryCommand() {
        /* command holder - never instantiated */
    }

    /**
     * Registers the {@code /recoleta memory} tree against a Brigadier
     * dispatcher. Wired to {@link CommandRegistration}.
     *
     * @param dispatcher the dispatcher exposed by {@code RegisterCommandsEvent}
     */
    public static void register(final CommandDispatcher<CommandSourceStack> dispatcher) {
        final LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal(Recoleta.MODID)
                .requires(src -> src.hasPermission(PERMISSION_LEVEL))
                .then(Commands.literal("memory")
                        .then(Commands.literal("status").executes(MemoryCommand::status))
                        .then(Commands.literal("compact").executes(MemoryCommand::compact))
                        .then(Commands.literal("pressure").executes(MemoryCommand::pressure)));
        dispatcher.register(root);
    }

    /**
     * Prints a human-readable status snapshot to the command source.
     *
     * @param ctx the Brigadier execution context
     * @return Brigadier success code (1)
     */
    private static int status(final com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx) {
        final CommandSourceStack src = ctx.getSource();
        final MemoryUsage heap = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
        final String gc = ManagementFactory.getGarbageCollectorMXBeans().stream()
                .map(b -> b.getName())
                .reduce((a, b) -> a + "+" + b).orElse("?");

        send(src, ChatFormatting.GOLD, "Recoleta memory status");
        send(src, ChatFormatting.GRAY, "  JVM           : " + System.getProperty("java.vm.name", "?")
                + " " + System.getProperty("java.vm.version", "?"));
        send(src, ChatFormatting.GRAY, "  Active GC     : " + gc);
        send(src, ChatFormatting.GRAY, String.format(Locale.ROOT,
                "  Heap usage    : %.1f / %.1f MiB (%.1f%%)",
                heap.getUsed() / 1048576.0,
                heap.getMax() / 1048576.0,
                heap.getMax() > 0 ? 100.0 * heap.getUsed() / heap.getMax() : 0.0));
        send(src, ChatFormatting.GRAY, String.format(Locale.ROOT,
                "  Pressure cap  : %.0f%%   (eviction %s)",
                MemoryConfig.PRESSURE_RATIO.get() * 100.0,
                MemoryConfig.ENABLE_PRESSURE_EVICTION.get() ? "ENABLED" : "disabled"));
        send(src, ChatFormatting.GRAY, "  Interned strs : " + RecoletaInterns.STRINGS.size());
        final long rlHits = RecoletaCounters.RL_TOSTRING_CACHE_HIT.sum();
        final long rlMisses = RecoletaCounters.RL_TOSTRING_CACHE_MISS.sum();
        final long rlTotal = rlHits + rlMisses;
        final double rlHitRate = rlTotal > 0 ? 100.0 * rlHits / rlTotal : 0.0;
        send(src, ChatFormatting.GRAY, String.format(Locale.ROOT,
                "  RL toString$  : entries=%d hits=%d miss=%d hit-rate=%.1f%%",
                RecoletaCaches.RL_TO_STRING.size(), rlHits, rlMisses, rlHitRate));
        final long lcHits = RecoletaCounters.LITERAL_CONTENTS_CACHE_HIT.sum();
        final long lcMisses = RecoletaCounters.LITERAL_CONTENTS_CACHE_MISS.sum();
        final long lcTotal = lcHits + lcMisses;
        final double lcHitRate = lcTotal > 0 ? 100.0 * lcHits / lcTotal : 0.0;
        send(src, ChatFormatting.GRAY, String.format(Locale.ROOT,
                "  Literal text$ : entries=%d hits=%d miss=%d hit-rate=%.1f%%",
                RecoletaCaches.LITERAL_CONTENTS.size(), lcHits, lcMisses, lcHitRate));
        final long tcHits = RecoletaCounters.TRANSLATABLE_CONTENTS_CACHE_HIT.sum();
        final long tcMisses = RecoletaCounters.TRANSLATABLE_CONTENTS_CACHE_MISS.sum();
        final long tcTotal = tcHits + tcMisses;
        final double tcHitRate = tcTotal > 0 ? 100.0 * tcHits / tcTotal : 0.0;
        send(src, ChatFormatting.GRAY, String.format(Locale.ROOT,
                "  I18n key$     : entries=%d hits=%d miss=%d hit-rate=%.1f%%",
                RecoletaCaches.TRANSLATABLE_CONTENTS.size(), tcHits, tcMisses, tcHitRate));
        final long kcHits = RecoletaCounters.KEYBIND_CONTENTS_CACHE_HIT.sum();
        final long kcMisses = RecoletaCounters.KEYBIND_CONTENTS_CACHE_MISS.sum();
        final long kcTotal = kcHits + kcMisses;
        final double kcHitRate = kcTotal > 0 ? 100.0 * kcHits / kcTotal : 0.0;
        send(src, ChatFormatting.GRAY, String.format(Locale.ROOT,
                "  Keybind$      : entries=%d hits=%d miss=%d hit-rate=%.1f%%",
                RecoletaCaches.KEYBIND_CONTENTS.size(), kcHits, kcMisses, kcHitRate));
        send(src, ChatFormatting.GRAY, "  Particle cap  : " + MemoryConfig.PARTICLE_PER_TYPE_CAP.get()
                + " (vanilla 16384)");
        send(src, ChatFormatting.GRAY, "  Drain budget  : " + MemoryConfig.REFERENCE_DRAIN_BUDGET.get()
                + " refs/tick");
        send(src, ChatFormatting.GRAY, "  Packed AABB   : " + (MemoryConfig.ENABLE_PACKED_AABB_PATH_CACHE.get() ? "ENABLED" : "disabled"));
        send(src, ChatFormatting.GRAY, "  Spawn patch   : " + (MemoryConfig.ENABLE_SPAWNER_DISTANCE_ALLOCATION_PATCH.get() ? "ENABLED" : "disabled"));
        send(src, ChatFormatting.GRAY, "  NBT small map : " + (MemoryConfig.ENABLE_COMPOUNDTAG_SMALL_MAPS.get() ? "ENABLED" : "disabled"));
        send(src, ChatFormatting.GRAY, "  Cap compare   : " + (MemoryConfig.ENABLE_CAPABILITY_FAST_COMPARE.get() ? "ENABLED" : "disabled"));
        send(src, ChatFormatting.GRAY, "  Chunk pkt list: " + (MemoryConfig.ENABLE_CHUNK_PACKET_RIGHT_SIZE.get() ? "ENABLED" : "disabled"));
        send(src, ChatFormatting.GRAY, String.format(Locale.ROOT,
                "  Pool mPos     : acq=%d rel=%d inUse~%d cached(tl)=%d",
                MutableBlockPosPool.acquireCount(),
                MutableBlockPosPool.releaseCount(),
                MutableBlockPosPool.outstandingCount(),
                MutableBlockPosPool.cachedCountCurrentThread()));
        send(src, ChatFormatting.GRAY, String.format(Locale.ROOT,
                "  Pool vec3     : acq=%d rel=%d inUse~%d cached(tl)=%d",
                Vec3Pool.acquireCount(),
                Vec3Pool.releaseCount(),
                Vec3Pool.outstandingCount(),
                Vec3Pool.cachedCountCurrentThread()));
        send(src, ChatFormatting.GRAY, String.format(Locale.ROOT,
                "  Pool aggregate: cached(all threads)=%d",
                PoolRegistry.aggregateCachedCount()));
        return 1;
    }

    /**
     * Forces an immediate full slack-trim plus a large reference-queue
     * drain, then reports the reclaimed heap bytes.
     *
     * @param ctx the Brigadier execution context
     * @return Brigadier success code (1)
     */
    private static int compact(final com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx) {
        final CommandSourceStack src = ctx.getSource();
        final long before = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed();

        SlackTrimmer.trimAllNow();
        IncrementalCleaner.drainAll();

        final long after = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed();
        final long delta = before - after;
        send(src, ChatFormatting.GREEN, String.format(Locale.ROOT,
                "Compaction done. Heap delta: %+.2f MiB (live trim only - GC may release more later).",
                delta / 1048576.0));
        return 1;
    }

    /**
     * Synthetically dispatches the heap-pressure callback chain so an
     * operator can verify that registered eviction tasks behave
     * correctly without having to actually pressure the heap.
     *
     * @param ctx the Brigadier execution context
     * @return Brigadier success code (1)
     */
    private static int pressure(final com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx) {
        final CommandSourceStack src = ctx.getSource();
        LowPauseScheduler.resetEdgeState();
        LowPauseScheduler.dispatch(true);
        send(src, ChatFormatting.YELLOW, "Pressure callbacks dispatched.");
        return 1;
    }

    /**
     * Convenience wrapper for plain-text feedback messages.
     *
     * @param src    the command source
     * @param colour the chat colour
     * @param text   the message body
     */
    private static void send(final CommandSourceStack src, final ChatFormatting colour, final String text) {
        src.sendSuccess(() -> Component.literal(text).withStyle(colour), false);
    }
}

