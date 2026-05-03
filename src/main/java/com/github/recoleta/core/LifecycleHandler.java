package com.github.recoleta.core;

import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;

/**
 * Forge mod-event-bus lifecycle callbacks.
 *
 * <p>Kept in a dedicated class so that {@link ModInit} stays focused on
 * one-shot wiring, and so each phase-specific listener can be unit
 * tested in isolation.</p>
 */
public final class LifecycleHandler {

    private LifecycleHandler() {
        /* utility class - never instantiated */
    }

    /**
     * Hook fired during {@link FMLCommonSetupEvent}. Currently a debug
     * marker; future passes can install caches that depend on registries
     * being frozen here.
     *
     * @param event the Forge common-setup event
     */
    public static void onCommonSetup(final FMLCommonSetupEvent event) {
        ModInit.LOG.debug("Recoleta common setup complete.");
    }
}

