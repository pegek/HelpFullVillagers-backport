package com.spege.helpfulvillagers.main;

import java.util.Iterator;

import com.spege.helpfulvillagers.network.ItemFrameEventPacket;

import net.minecraft.entity.item.EntityItemFrame;
import net.minecraftforge.client.event.RenderItemInFrameEvent;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.network.FMLNetworkEvent;

/**
 * Client-only event handler. Supplements {@link CommonHooks} with rendering and client-tick
 * bookkeeping. Registered via {@link MinecraftForge.EVENT_BUS} in {@link ClientProxy}.
 *
 * <p>1.12.2 migration: {@code RenderItemInFrameEvent.getEntityItemFrame()};
 * {@code getDisplayedItem().isEmpty()} instead of null-check; the 1.7.10 version-check URL ping
 * is dropped (not relevant for a port in development).
 */
@SuppressWarnings("null")
public class ClientHooks {
    @SubscribeEvent
    public void clientTickEventHandler(TickEvent.ClientTickEvent event) {
        Iterator<EntityItemFrame> iterator = HelpfulVillagers.checkedFrames.iterator();
        while (iterator.hasNext()) {
            EntityItemFrame frame = iterator.next();
            if (frame != null && frame.isEntityAlive()
                    && frame.getDisplayedItem() != null && !frame.getDisplayedItem().isEmpty()) {
                continue;
            }
            iterator.remove();
        }
    }

    @SubscribeEvent
    public void renderItemInFrameEventHandler(RenderItemInFrameEvent event) {
        EntityItemFrame frame = event.getEntityItemFrame();
        if (frame.getDisplayedItem() != null && !frame.getDisplayedItem().isEmpty()
                && !HelpfulVillagers.checkedFrames.contains(frame)) {
            HelpfulVillagers.checkedFrames.add(frame);
            HelpfulVillagers.network.sendToServer(new ItemFrameEventPacket(frame.getEntityId()));
        }
    }

    @SubscribeEvent
    public void clientDisconnectEventHandler(FMLNetworkEvent.ClientDisconnectionFromServerEvent event) {
        HelpfulVillagers.config.load();
        HelpfulVillagers.config.removeCategory(HelpfulVillagers.config.getCategory("general"));
        HelpfulVillagers.config.getInt("deathMessage", "general", HelpfulVillagers.deathMessageOption, 0, 2,
                "0 - Off, 1 - On, 2 - Verbose");
        HelpfulVillagers.config.getInt("birthMessage", "general", HelpfulVillagers.birthMessageOption, 0, 2,
                "0 - Off, 1 - On, 2 - Verbose");
        HelpfulVillagers.config.save();
    }

    @SubscribeEvent
    public void worldLoadEventHandler(WorldEvent.Load event) {
        if (event.getWorld().isRemote || event.getWorld().provider.getDimension() != 0) {
            return;
        }
        HelpfulVillagers.villageCollection = com.spege.helpfulvillagers.village.HelpfulVillageCollection.forWorld(event.getWorld());
        if (HelpfulVillagers.villageCollection != null && !HelpfulVillagers.villageCollection.isEmpty()) {
            HelpfulVillagers.villages.clear();
            HelpfulVillagers.villages.addAll(HelpfulVillagers.villageCollection.getVillages());
        }
    }
}
