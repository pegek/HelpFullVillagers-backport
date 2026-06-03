package com.spege.helpfulvillagers.main;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;

/**
 * Client-side proxy. Adds client-only behaviour (renderers, client event hooks)
 * on top of {@link CommonProxy}.
 */
public class ClientProxy extends CommonProxy {

    @Override
    public void preInit(FMLPreInitializationEvent event) {
        super.preInit(event);
        MinecraftForge.EVENT_BUS.register(new ClientHooks());
    }

    @Override
    public void init(FMLInitializationEvent event) {
        super.init(event);
    }

    @Override
    public void registerRenderers() {
        // TODO step 11: RenderVillagerCustom + RenderFishHookCustom via ModelRegistryEvent/IRenderFactory.
    }
}
