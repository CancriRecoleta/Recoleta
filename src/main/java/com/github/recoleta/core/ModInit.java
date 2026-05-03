package com.github.recoleta.core;

import com.github.recoleta.Recoleta;
import com.github.recoleta.command.CommandRegistration;
import com.github.recoleta.config.MemoryConfig;
import com.github.recoleta.memory.MemoryEvents;
import com.github.recoleta.memory.SlackTrimmer;
import com.github.recoleta.memory.gc.IncrementalCleaner;
import com.github.recoleta.memory.gc.LowPauseScheduler;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Central bootstrap for the Recoleta mod.
 *
 * <p>Registers the configuration spec on the Forge config system, hooks
 * the idle-time {@link SlackTrimmer}, the heap-pressure {@link MemoryEvents}
 * watcher and the per-tick {@link IncrementalCleaner}, and emits a banner
 * describing which optional optimisations are active.</p>
 */
public final class ModInit {

    /** Shared logger for all Recoleta subsystems. */
    public static final Logger LOG = LoggerFactory.getLogger(Recoleta.MODID);

    private ModInit() {
        /* utility class - never instantiated */
    }

    /**
     * Performs all one-shot wiring that must happen during the mod
     * constructor phase.
     *
     * @param ctx the Forge mod-loading context handed to the {@code @Mod} class
     */
    public static void bootstrap(final FMLJavaModLoadingContext ctx) {
        final IEventBus modBus = ctx.getModEventBus();
        final IEventBus forgeBus = MinecraftForge.EVENT_BUS;

        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, MemoryConfig.SPEC, "recoleta-memory.toml");

        SlackTrimmer.register(forgeBus);
        IncrementalCleaner.register(forgeBus);
        CommandRegistration.register(forgeBus);
        registerPressureCallbacks();

        modBus.addListener(ModInit::onConfigLoaded);
        modBus.addListener(LifecycleHandler::onCommonSetup);
    }

    private static void onConfigLoaded(final ModConfigEvent.Loading event) {
        final ModConfig config = event.getConfig();
        if (!Recoleta.MODID.equals(config.getModId()) || config.getType() != ModConfig.Type.COMMON) {
            return;
        }
        MemoryEvents.install();
        logRuntimeBanner();
    }

    /**
     * Wires the heap-pressure dispatcher so that the
     * {@link com.github.recoleta.memory.MemoryEvents} watcher actually
     * causes work to happen when the JVM crosses the configured
     * threshold. Without this method the entire pressure pipeline
     * would be inert.
     *
     * <p>The two registered tasks are intentionally cheap and
     * idempotent: trimming long-lived collection slack and draining
     * every reference queue. Both can run repeatedly without harm.</p>
     */
    private static void registerPressureCallbacks() {
        LowPauseScheduler.onPressure(SlackTrimmer::trimAllNow);
        LowPauseScheduler.onPressure(IncrementalCleaner::drainAll);
    }

    /**
     * Emits a single INFO-level banner summarising the JVM and which
     * userland subsystems are active. Detection of the underlying GC and
     * VM flags is best-effort and uses {@link java.lang.management.ManagementFactory},
     * so this code compiles and runs unchanged on any JDK 17+.
     */
    private static void logRuntimeBanner() {
        final String vmName = System.getProperty("java.vm.name", "?");
        final String vmVersion = System.getProperty("java.vm.version", "?");
        final String gc = java.lang.management.ManagementFactory
                .getGarbageCollectorMXBeans().stream()
                .map(java.lang.management.GarbageCollectorMXBean::getName)
                .reduce((a, b) -> a + "+" + b).orElse("?");

        LOG.info("Recoleta loaded - target JVM = Java 17+, current = {} {}", vmName, vmVersion);
        LOG.info("  Active GC                       : {}", gc);
        LOG.info("  Userland packed value encodings : memory.header (JEP 519 port)");
        LOG.info("  Generational pool + cleaner     : memory.gc      (JEP 521 port)");
        LOG.info("  Slack trimmer / pressure evict  : {} / {}",
                MemoryConfig.ENABLE_SLACK_TRIMMER.get(),
                MemoryConfig.ENABLE_PRESSURE_EVICTION.get());
    }
}

