package com.spege.helpfulvillagers.village;

import java.util.ArrayList;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;
import net.minecraft.world.storage.MapStorage;
import net.minecraft.world.storage.WorldSavedData;

/**
 * Per-world persistent storage for all {@link HelpfulVillage} instances (overworld only).
 *
 * <p>1.12.2 migration: WorldSavedData no longer takes a name in {@code load}; persistence goes
 * through {@code world.getPerWorldStorage().getOrLoadData(...)}/{@code setData(...)}, and
 * {@code writeToNBT} now RETURNS the {@link NBTTagCompound} (it was void in 1.7.10).
 */
public class HelpfulVillageCollection extends WorldSavedData {
    public static final String KEY = "HelpfulVillageCollection";
    private ArrayList<HelpfulVillage> villageList = new ArrayList<HelpfulVillage>();

    public HelpfulVillageCollection() {
        super(KEY);
    }

    public HelpfulVillageCollection(String name) {
        super(name);
    }

    public static HelpfulVillageCollection forWorld(World world) {
        if (world.isRemote) {
            return null;
        }
        MapStorage storage = world.getPerWorldStorage();
        HelpfulVillageCollection result = (HelpfulVillageCollection) storage.getOrLoadData(HelpfulVillageCollection.class, KEY);
        if (result == null) {
            result = new HelpfulVillageCollection();
            storage.setData(KEY, result);
        }
        return result;
    }

    public ArrayList<HelpfulVillage> getVillages() {
        return this.villageList;
    }

    public void setVillages(ArrayList<HelpfulVillage> villages) {
        this.villageList.clear();
        this.villageList.addAll(villages);
        this.markDirty();
    }

    public boolean isEmpty() {
        return this.villageList.isEmpty();
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound compound) {
        NBTTagList nbttaglist = new NBTTagList();
        for (HelpfulVillage village : this.villageList) {
            if (village.isAnnihilated) {
                continue;
            }
            NBTTagCompound nbttagcompound1 = new NBTTagCompound();
            village.writeVillageDataToNBT(nbttagcompound1);
            nbttaglist.appendTag(nbttagcompound1);
        }
        compound.setTag("Villages", nbttaglist);
        return compound;
    }

    @Override
    public void readFromNBT(NBTTagCompound compound) {
        NBTTagList nbttaglist = compound.getTagList("Villages", 10);
        for (int i = 0; i < nbttaglist.tagCount(); ++i) {
            NBTTagCompound nbttagcompound1 = nbttaglist.getCompoundTagAt(i);
            HelpfulVillage village = new HelpfulVillage();
            village.readVillageDataFromNBT(nbttagcompound1);
            this.villageList.add(village);
        }
    }
}
