package com.spege.helpfulvillagers.crafting;

import java.util.ArrayList;

import com.spege.helpfulvillagers.entity.AbstractVillager;
import com.spege.helpfulvillagers.main.HelpfulVillagers;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

/** Two-tier (player-priority / villager) queue of items a crafting villager should make. */
public class CraftQueue {
    private ArrayList<CraftItem> playerItems = new ArrayList<CraftItem>();
    private ArrayList<CraftItem> villagerItems = new ArrayList<CraftItem>();

    public CraftQueue() {
    }

    public CraftQueue(ArrayList<CraftItem> items) {
        for (CraftItem i : items) {
            if (i == null) {
                continue;
            }
            if (i.getPriority() >= 1) {
                this.playerItems.add(i);
                continue;
            }
            this.villagerItems.add(i);
        }
    }

    private static void markCollectionDirty() {
        if (HelpfulVillagers.villageCollection != null) {
            HelpfulVillagers.villageCollection.markDirty();
        }
    }

    public void getCraftItem(AbstractVillager villager) {
        CraftItem item;
        int i;
        for (i = 0; i < this.playerItems.size(); ++i) {
            item = this.playerItems.get(i);
            if (!villager.canCraft(item)) {
                continue;
            }
            villager.currentCraftItem = this.playerItems.remove(i);
            markCollectionDirty();
            return;
        }
        for (i = 0; i < this.villagerItems.size(); ++i) {
            item = this.villagerItems.get(i);
            if (!villager.canCraft(item)) {
                continue;
            }
            villager.currentCraftItem = this.villagerItems.remove(i);
            markCollectionDirty();
            return;
        }
    }

    public CraftItem getItemStackAt(int index) {
        ArrayList<CraftItem> temp = new ArrayList<CraftItem>();
        temp.addAll(this.playerItems);
        temp.addAll(this.villagerItems);
        if (index >= temp.size()) {
            return null;
        }
        return temp.get(index);
    }

    public void removeItemStackAt(int index) {
        if (index >= this.playerItems.size()) {
            if ((index -= this.playerItems.size()) >= this.villagerItems.size()) {
                System.out.println("ERROR: Index Too Large");
            } else {
                this.villagerItems.remove(index);
            }
        } else {
            this.playerItems.remove(index);
        }
    }

    public void addPlayerItem(CraftItem item) {
        if (item != null) {
            this.playerItems.add(item);
            markCollectionDirty();
        }
    }

    public void addVillagerItem(CraftItem item) {
        if (item != null) {
            this.villagerItems.add(item);
            markCollectionDirty();
        }
    }

    public int getSize() {
        return this.playerItems.size() + this.villagerItems.size();
    }

    public ArrayList<CraftItem> getPlayerQueue() {
        return new ArrayList<CraftItem>(this.playerItems);
    }

    public ArrayList<CraftItem> getVillagerQueue() {
        return new ArrayList<CraftItem>(this.villagerItems);
    }

    public ArrayList<CraftItem> getAll() {
        ArrayList<CraftItem> temp = new ArrayList<CraftItem>();
        temp.addAll(this.playerItems);
        temp.addAll(this.villagerItems);
        return temp;
    }

    public NBTTagList writeToNBT(NBTTagList nbtTagList) {
        NBTTagCompound nbttagcompound;
        for (CraftItem i : this.playerItems) {
            nbttagcompound = new NBTTagCompound();
            nbttagcompound.setBoolean("Player", true);
            nbttagcompound.setTag("Item", i.writeToNBT(new NBTTagCompound()));
            nbtTagList.appendTag(nbttagcompound);
        }
        for (CraftItem i : this.villagerItems) {
            nbttagcompound = new NBTTagCompound();
            nbttagcompound.setBoolean("Player", false);
            nbttagcompound.setTag("Item", i.writeToNBT(new NBTTagCompound()));
            nbtTagList.appendTag(nbttagcompound);
        }
        return nbtTagList;
    }

    public void readFromNBT(NBTTagList nbttaglist) {
        for (int i = 0; i < nbttaglist.tagCount(); ++i) {
            NBTTagCompound nbttagcompound = nbttaglist.getCompoundTagAt(i);
            boolean player = nbttagcompound.getBoolean("Player");
            NBTTagCompound craftCompound = nbttagcompound.getCompoundTag("Item");
            CraftItem craftItem = CraftItem.loadCraftItemFromNBT(craftCompound);
            if (craftItem == null) {
                continue;
            }
            if (player) {
                this.playerItems.add(craftItem);
                continue;
            }
            this.villagerItems.add(craftItem);
        }
    }

    public void clear() {
        this.playerItems.clear();
        this.villagerItems.clear();
        markCollectionDirty();
    }

    public void mergeQueue(CraftQueue otherQueue) {
        this.playerItems.addAll(otherQueue.getPlayerQueue());
        this.villagerItems.addAll(otherQueue.getVillagerQueue());
    }

    @Override
    public String toString() {
        return this.playerItems.toString() + " " + this.villagerItems.toString();
    }
}
