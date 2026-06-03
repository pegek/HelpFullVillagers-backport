package com.spege.helpfulvillagers.main;

import java.util.ArrayList;
import java.util.Iterator;

import com.spege.helpfulvillagers.econ.VillageEconomy;
import com.spege.helpfulvillagers.entity.EntityRegularVillager;
import com.spege.helpfulvillagers.village.HelpfulVillage;
import com.spege.helpfulvillagers.village.HelpfulVillageCollection;

import net.minecraft.entity.item.EntityItemFrame;
import net.minecraft.entity.passive.EntityVillager;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.network.FMLNetworkEvent;

/**
 * Server-side (and integrated-server) event handler. Responsibilities:
 * <ul>
 *   <li>Replace vanilla {@link EntityVillager} spawns with the mod's {@link EntityRegularVillager}.</li>
 *   <li>Periodically update village boxes, hall detection, and economy calculations.</li>
 *   <li>Load/persist {@link HelpfulVillageCollection} on world load.</li>
 * </ul>
 *
 * <p>1.12.2 migration: {@code cpw.mods.fml.*} → {@code net.minecraftforge.fml.*};
 * {@code event.getWorld().provider.getDimension()} for dimension check; {@code AxisAlignedBB.intersects}.
 * The 1.7.10 version check / login chat message is removed (port-specific, not a feature).
 */
@SuppressWarnings({ "null", "deprecation" })
public class CommonHooks {
    private static final int VILLAGE_UPDATE = 1200;
    public static int villageTicks = 1200;
    private int prevFrameSize = 0;
    private boolean dayReset = true;
    private boolean nightReset = true;

    @SubscribeEvent
    public void entityJoinedWorldEventHandler(EntityJoinWorldEvent event) {
        if (event.getWorld().isRemote) {
            return;
        }
        if (event.getEntity().getClass() == EntityVillager.class) {
            EntityVillager villager = (EntityVillager) event.getEntity();
            if (villager.getProfession() > -1 && villager.getProfession() < 6) {
                HelpfulVillagers.logger.info("[HV] EntityJoinWorld: replacing vanilla villager (profession={}) at {},{},{}",
                        villager.getProfession(), (int) villager.posX, (int) villager.posY, (int) villager.posZ);
                EntityRegularVillager newVillager = new EntityRegularVillager(event.getWorld());
                newVillager.setLocationAndAngles(villager.posX, villager.posY, villager.posZ, villager.rotationYaw, villager.rotationPitch);
                if (villager.isChild()) {
                    newVillager.setGrowingAge(villager.getGrowingAge());
                }
                event.setCanceled(true);
                villager.setDead();
                event.getWorld().spawnEntity(newVillager);
            }
        } else if (event.getEntity().getClass() == EntityItemFrame.class) {
            EntityItemFrame frame = (EntityItemFrame) event.getEntity();
            if (frame.getDisplayedItem() != null && !frame.getDisplayedItem().isEmpty()) {
                villageTicks = 1200;
            }
        }
    }

    @SubscribeEvent
    public void serverTickEventHandler(TickEvent.ServerTickEvent event) {
        try {
            Iterator<EntityItemFrame> iterator2 = HelpfulVillagers.checkedFrames.iterator();
            while (iterator2.hasNext()) {
                EntityItemFrame frame = iterator2.next();
                if (frame != null && frame.isEntityAlive()
                        && frame.getDisplayedItem() != null && !frame.getDisplayedItem().isEmpty()) {
                    continue;
                }
                iterator2.remove();
            }
            if (HelpfulVillagers.checkedFrames.size() != this.prevFrameSize) {
                villageTicks = 1200;
                this.prevFrameSize = HelpfulVillagers.checkedFrames.size();
            }
            for (int i = 0; i < HelpfulVillagers.villages.size(); ++i) {
                net.minecraft.world.World world = HelpfulVillagers.villages.get(i).world;
                if (this.dayReset && world.isDaytime()) {
                    villageTicks = 1200;
                    this.dayReset = false;
                    break;
                }
                if (!this.dayReset && !world.isDaytime()) {
                    this.dayReset = true;
                }
                if (this.nightReset && !world.isDaytime()) {
                    villageTicks = 1200;
                    this.nightReset = false;
                    break;
                }
                if (this.nightReset || !world.isDaytime()) {
                    continue;
                }
                this.nightReset = true;
            }
            if (villageTicks >= 1200) {
                int i;
                ArrayList<Integer> removeVillages = new ArrayList<Integer>();
                Iterator<HelpfulVillage> it = HelpfulVillagers.villages.iterator();
                while (it.hasNext()) {
                    HelpfulVillage village = it.next();
                    if (!village.isAnnihilated
                            && (!village.isFullyLoaded() || village.getPopulation() > 0
                                    || village.getTotalAdded() < village.getTotalVillagers())) {
                        continue;
                    }
                    it.remove();
                }
                for (i = 0; i < HelpfulVillagers.villages.size(); ++i) {
                    for (int j = 0; j < HelpfulVillagers.villages.size(); ++j) {
                        if (i == j || removeVillages.contains(i)) {
                            continue;
                        }
                        HelpfulVillage currentVillage = HelpfulVillagers.villages.get(i);
                        HelpfulVillage otherVillage = HelpfulVillagers.villages.get(j);
                        // A freshly created village has null actualBounds until updateVillageBox()
                        // runs later in this tick; skip it in the merge pass to avoid an NPE.
                        if (currentVillage.actualBounds == null || otherVillage.actualBounds == null) {
                            continue;
                        }
                        if (!currentVillage.actualBounds.intersects(otherVillage.actualBounds)) {
                            continue;
                        }
                        currentVillage.mergeVillage(otherVillage);
                        removeVillages.add(j);
                    }
                }
                for (i = 0; i < removeVillages.size(); ++i) {
                    int removeIndex = removeVillages.get(i);
                    HelpfulVillagers.villages.remove(removeIndex);
                }
                removeVillages.clear();
                for (i = 0; i < HelpfulVillagers.villages.size(); ++i) {
                    HelpfulVillagers.villages.get(i).updateVillageBox();
                    HelpfulVillagers.villages.get(i).findHalls();
                    HelpfulVillagers.villages.get(i).checkHalls();
                    if (!HelpfulVillagers.villages.get(i).pricesCalculated
                            && !HelpfulVillagers.villages.get(i).priceCalcStarted) {
                        HelpfulVillagers.villages.get(i).economy = new VillageEconomy(
                                HelpfulVillagers.villages.get(i), true);
                    }
                    if (villageTicks != 1300) {
                        continue;
                    }
                    HelpfulVillagers.villages.get(i).economy.decreaseAllDemand();
                }
                if (HelpfulVillagers.villageCollection != null) {
                    HelpfulVillagers.villageCollection.setVillages(HelpfulVillagers.villages);
                }
                villageTicks = 0;
            } else {
                ++villageTicks;
            }
        } catch (Exception e) {
            e.printStackTrace();
            villageTicks = 0;
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
        HelpfulVillagers.logger.info("[HV] WorldLoad: loading village collection for overworld...");
        HelpfulVillagers.villageCollection = HelpfulVillageCollection.forWorld(event.getWorld());
        if (HelpfulVillagers.villageCollection != null && !HelpfulVillagers.villageCollection.isEmpty()) {
            HelpfulVillagers.villages.clear();
            HelpfulVillagers.villages.addAll(HelpfulVillagers.villageCollection.getVillages());
            HelpfulVillagers.logger.info("[HV] WorldLoad: loaded {} saved villages", HelpfulVillagers.villages.size());
        } else {
            HelpfulVillagers.logger.info("[HV] WorldLoad: no saved villages (new world or empty collection)");
        }
    }
}
