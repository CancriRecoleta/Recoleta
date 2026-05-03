package com.github.recoleta.command;

import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Subscribes Recoleta's command tree to the Forge event bus.
 *
 * <p>Lives in a dedicated class so that command registration code can
 * evolve without touching the central {@code ModInit}.</p>
 */
public final class CommandRegistration {

    private CommandRegistration() {
        /* utility class - never instantiated */
    }

    /**
     * Registers this class as a listener on the Forge event bus.
     *
     * @param forgeBus the singleton {@code MinecraftForge.EVENT_BUS}
     */
    public static void register(final IEventBus forgeBus) {
        forgeBus.register(CommandRegistration.class);
    }

    /**
     * Forge callback fired during {@code /reload} and at server start.
     *
     * @param event the Forge command-registration event
     */
    @SubscribeEvent
    public static void onRegister(final RegisterCommandsEvent event) {
        MemoryCommand.register(event.getDispatcher());
    }
}

