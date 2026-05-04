package com.github.recoleta.neoforge;

import com.github.recoleta.Recoleta;
import com.github.recoleta.core.RecoletaBootstrap;
import com.github.recoleta.neoforge.config.NeoForgeConfigBridge;
import com.github.recoleta.neoforge.event.NeoForgeEvents;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;

/**
 * NeoForge mod entry. Registers the spec, hooks the mod / NeoForge bus
 * and delegates one-shot wiring to {@link RecoletaBootstrap}.
 */
@Mod(Recoleta.MODID)
public final class RecoletaNeoForge {

    public RecoletaNeoForge(final IEventBus modBus, final ModContainer container) {
        container.registerConfig(ModConfig.Type.COMMON, NeoForgeConfigBridge.SPEC, "recoleta-memory.toml");
        modBus.addListener(NeoForgeConfigBridge::onConfigLoad);

        NeoForgeEvents.register(NeoForge.EVENT_BUS);

        RecoletaBootstrap.init();
    }
}
