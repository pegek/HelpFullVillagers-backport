package com.spege.helpfulvillagers.main;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;

/**
 * Server/common-side proxy. Holds logic safe to run on both physical sides.
 * The client-only counterpart is {@link ClientProxy}.
 */
public class CommonProxy {

    public void preInit(FMLPreInitializationEvent event) {
        MinecraftForge.EVENT_BUS.register(new CommonHooks());
    }

    public void init(FMLInitializationEvent event) {
        // no-op on the dedicated server
    }

    /** Registers entity renderers. No-op on the server; overridden on the client. */
    public void registerRenderers() {
    }
}
