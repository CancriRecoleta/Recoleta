package com.github.recoleta.fabric;

import com.github.recoleta.command.MemoryCommand;
import com.github.recoleta.core.RecoletaBootstrap;
import com.github.recoleta.fabric.config.FabricConfigBridge;
import com.github.recoleta.memory.MemoryEvents;
import com.github.recoleta.memory.SlackTrimmer;
import com.github.recoleta.memory.gc.IncrementalCleaner;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

/**
 * Fabric main-side entry. Subscribes to server ticks + command
 * registration; loads the properties file; delegates to
 * {@link RecoletaBootstrap}.
 */
public final class RecoletaFabric implements ModInitializer {

    @Override
    public void onInitialize() {
        FabricConfigBridge.load();
        RecoletaBootstrap.init();
        RecoletaBootstrap.onConfigLoaded();

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            SlackTrimmer.onServerTickEnd();
            MemoryEvents.onServerTickEnd();
            IncrementalCleaner.onServerTickEnd();
        });

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                MemoryCommand.register(dispatcher));
    }
}
