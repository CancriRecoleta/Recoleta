package com.github.recoleta.forge;

import com.github.recoleta.Recoleta;
import com.github.recoleta.core.RecoletaBootstrap;
import com.github.recoleta.forge.config.ForgeConfigBridge;
import com.github.recoleta.forge.event.ForgeEvents;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

/**
 * Forge mod entry. Registers the spec, hooks the mod / Forge bus and
 * delegates one-shot wiring to {@link RecoletaBootstrap}.
 */
@Mod(Recoleta.MODID)
public final class RecoletaForge {

    public RecoletaForge() {
        final IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();

        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, ForgeConfigBridge.SPEC, "recoleta-memory.toml");
        modBus.addListener(ForgeConfigBridge::onConfigLoad);

        ForgeEvents.register(MinecraftForge.EVENT_BUS);

        RecoletaBootstrap.init();
    }
}
