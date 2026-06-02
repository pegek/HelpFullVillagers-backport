package com.spege.helpfulvillagers.crafting;

import java.util.ArrayList;
import java.util.List;

import com.spege.helpfulvillagers.entity.AbstractVillager;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

/** A single requested craft target (an item stack) plus who requested it and at what priority. */
@SuppressWarnings("null")
public class CraftItem {
    private ItemStack item = ItemStack.EMPTY;
    private String name;
    private int priority;
    private boolean metadataSensitive;

    private CraftItem() {
    }

    public CraftItem(ItemStack item, String name, int priority) {
        this.item = item;
        this.name = name;
        this.priority = priority;
        this.metadataSensitive = false;
    }

    public CraftItem(ItemStack item, EntityPlayer player) {
        this(item, player.getName(), 1);
    }

    public CraftItem(ItemStack item, AbstractVillager villager) {
        this(item, villager.getName() + " the " + villager.profName, 0);
    }

    public ItemStack getItem() {
        return this.item;
    }

    public String getName() {
        return this.name;
    }

    public int getPriority() {
        return this.priority;
    }

    public boolean isSensitive() {
        return this.metadataSensitive;
    }

    public void setSensitivity(boolean b) {
        this.metadataSensitive = b;
    }

    public List<String> getTooltip() {
        ArrayList<String> list = new ArrayList<String>();
        list.add(this.item.getDisplayName() + " x" + this.item.getCount());
        if (this.priority >= 1) {
            list.add("Requested by Player:");
        } else {
            list.add("Requested by Villager:");
        }
        list.add(this.name);
        return list;
    }

    public NBTTagCompound writeToNBT(NBTTagCompound compound) {
        this.item.writeToNBT(compound);
        compound.setString("Name", this.name);
        compound.setInteger("Priority", this.priority);
        compound.setBoolean("Metadata", this.metadataSensitive);
        return compound;
    }

    public void readFromNBT(NBTTagCompound compound) {
        this.item = new ItemStack(compound);
        this.name = compound.getString("Name");
        this.priority = compound.getInteger("Priority");
        this.metadataSensitive = compound.getBoolean("Metadata");
    }

    public static CraftItem loadCraftItemFromNBT(NBTTagCompound compound) {
        CraftItem item = new CraftItem();
        item.readFromNBT(compound);
        return item.getItem().isEmpty() ? null : item;
    }
}
