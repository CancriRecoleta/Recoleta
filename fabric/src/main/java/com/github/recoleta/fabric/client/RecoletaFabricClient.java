package com.github.recoleta.fabric.client;

import com.github.recoleta.memory.MemoryEvents;
import com.github.recoleta.memory.SlackTrimmer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

/**
 * Fabric client-side entry. Subscribes to client ticks so the
 * client-only trimmer registry runs.
 */
public final class RecoletaFabricClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            SlackTrimmer.onClientTickEnd();
            MemoryEvents.onClientTickEnd();
        });
    }
}
