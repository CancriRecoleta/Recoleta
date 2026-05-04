package com.github.recoleta.neoforge.event;

import com.github.recoleta.command.MemoryCommand;
import com.github.recoleta.memory.MemoryEvents;
import com.github.recoleta.memory.SlackTrimmer;
import com.github.recoleta.memory.gc.IncrementalCleaner;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Translates NeoForge bus events into Recoleta common-side static calls.
 */
public final class NeoForgeEvents {

    private NeoForgeEvents() {
        /* utility class - never instantiated */
    }

    public static void register(final IEventBus forgeBus) {
        forgeBus.addListener(NeoForgeEvents::onServerTickPost);
        forgeBus.addListener(NeoForgeEvents::onClientTickPost);
        forgeBus.addListener(NeoForgeEvents::onRegisterCommands);
    }

    private static void onServerTickPost(final ServerTickEvent.Post event) {
        SlackTrimmer.onServerTickEnd();
        MemoryEvents.onServerTickEnd();
        IncrementalCleaner.onServerTickEnd();
    }

    private static void onClientTickPost(final ClientTickEvent.Post event) {
        SlackTrimmer.onClientTickEnd();
        MemoryEvents.onClientTickEnd();
    }

    private static void onRegisterCommands(final RegisterCommandsEvent event) {
        MemoryCommand.register(event.getDispatcher());
    }
}
