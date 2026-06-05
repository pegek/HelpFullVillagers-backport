package com.spege.helpfulvillagers.main;

import com.spege.helpfulvillagers.entity.EntityArcher;
import com.spege.helpfulvillagers.entity.EntityBuilder;
import com.spege.helpfulvillagers.entity.EntityFarmer;
import com.spege.helpfulvillagers.entity.EntityFishHookCustom;
import com.spege.helpfulvillagers.entity.EntityFisherman;
import com.spege.helpfulvillagers.entity.EntityLumberjack;
import com.spege.helpfulvillagers.entity.EntityMerchant;
import com.spege.helpfulvillagers.entity.EntityMiner;
import com.spege.helpfulvillagers.entity.EntityRancher;
import com.spege.helpfulvillagers.entity.EntityRegularVillager;
import com.spege.helpfulvillagers.entity.EntitySoldier;
import com.spege.helpfulvillagers.renderer.RenderBuilder;
import com.spege.helpfulvillagers.renderer.RenderFishHookCustom;
import com.spege.helpfulvillagers.renderer.RenderVillagerCustom;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.client.registry.RenderingRegistry;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;

/**
 * Client-side proxy. Adds client-only behaviour (renderers, client event hooks)
 * on top of {@link CommonProxy}.
 *
 * <p>1.12.2: entity renderers MUST be registered in {@code preInit} (before the RenderManager
 * caches its renderer map) via {@link RenderingRegistry#registerEntityRenderingHandler} with an
 * {@code IRenderFactory} lambda. Registering in {@code init} is too late and the entities fall back
 * to the vanilla RenderVillager. One registration per concrete entity class (Forge dispatches by
 * exact runtime class).
 */
public class ClientProxy extends CommonProxy {

    @Override
    public void preInit(FMLPreInitializationEvent event) {
        super.preInit(event);
        MinecraftForge.EVENT_BUS.register(new ClientHooks());
        this.registerRenderers();
    }

    @Override
    public void init(FMLInitializationEvent event) {
        super.init(event);
    }

    @Override
    public void registerRenderers() {
        RenderingRegistry.registerEntityRenderingHandler(EntityRegularVillager.class, m -> new RenderVillagerCustom(m));
        RenderingRegistry.registerEntityRenderingHandler(EntityLumberjack.class,      m -> new RenderVillagerCustom(m));
        RenderingRegistry.registerEntityRenderingHandler(EntityMiner.class,           m -> new RenderVillagerCustom(m));
        RenderingRegistry.registerEntityRenderingHandler(EntityFarmer.class,          m -> new RenderVillagerCustom(m));
        RenderingRegistry.registerEntityRenderingHandler(EntitySoldier.class,         m -> new RenderVillagerCustom(m));
        RenderingRegistry.registerEntityRenderingHandler(EntityArcher.class,          m -> new RenderVillagerCustom(m));
        RenderingRegistry.registerEntityRenderingHandler(EntityMerchant.class,        m -> new RenderVillagerCustom(m));
        RenderingRegistry.registerEntityRenderingHandler(EntityFisherman.class,       m -> new RenderVillagerCustom(m));
        RenderingRegistry.registerEntityRenderingHandler(EntityRancher.class,         m -> new RenderVillagerCustom(m));
        RenderingRegistry.registerEntityRenderingHandler(EntityBuilder.class, manager -> new RenderBuilder(manager));
        RenderingRegistry.registerEntityRenderingHandler(EntityFishHookCustom.class, manager -> new RenderFishHookCustom(manager));
        HelpfulVillagers.logger.info("[HV] registerRenderers: registered 10 entity renderers (preInit)");
    }
}
