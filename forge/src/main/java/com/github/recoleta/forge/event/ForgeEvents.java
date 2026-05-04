package com.github.recoleta.forge.event;

import com.github.recoleta.command.MemoryCommand;
import com.github.recoleta.memory.MemoryEvents;
import com.github.recoleta.memory.SlackTrimmer;
import com.github.recoleta.memory.gc.IncrementalCleaner;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Translates Forge bus events into Recoleta common-side static calls.
 */
public final class ForgeEvents {

    private ForgeEvents() {
        /* utility class - never instantiated */
    }

    public static void register(final IEventBus forgeBus) {
        forgeBus.register(ForgeEvents.class);
    }

    @SubscribeEvent
    public static void onServerTick(final TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        SlackTrimmer.onServerTickEnd();
        MemoryEvents.onServerTickEnd();
        IncrementalCleaner.onServerTickEnd();
    }

    @SubscribeEvent
    public static void onClientTick(final TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        SlackTrimmer.onClientTickEnd();
        MemoryEvents.onClientTickEnd();
    }

    @SubscribeEvent
    public static void onRegisterCommands(final RegisterCommandsEvent event) {
        MemoryCommand.register(event.getDispatcher());
    }
}
