package com.spege.helpfulvillagers.econ;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

/** Tracks the supply/demand-adjusted price of a single tradeable item within a village economy. */
@SuppressWarnings("null")
public class ItemPrice {
    private ItemStack item = ItemStack.EMPTY;
    private int price;
    private double supply = 1.0;
    private double demand = 1.0;

    public ItemPrice() {
    }

    public ItemPrice(ItemStack item, int price) {
        this.item = item;
        this.price = price;
    }

    public ItemPrice(ItemStack item, int price, double supply, double demand) {
        this.item = item;
        this.price = price;
        this.supply = supply;
        this.demand = demand;
    }

    public void changeSupply(double val) {
        this.supply += val;
        if (this.supply <= 0.0) {
            this.supply -= val;
        }
    }

    public void changeDemand(double val) {
        this.demand += val;
        if (this.demand <= 0.0) {
            this.demand -= val;
        }
    }

    public void increaseSupply(double amount) {
        this.changeSupply(1.0 / (double) this.item.getMaxStackSize() * amount);
    }

    public void decreaseSupply(double amount) {
        this.changeSupply(-1.0 / (double) this.item.getMaxStackSize() * amount);
    }

    public void increaseDemand(double amount) {
        this.changeDemand(1.0 / (double) this.item.getMaxStackSize() * amount);
    }

    public void decreaseDemand(double amount) {
        this.changeDemand(-1.0 / (double) this.item.getMaxStackSize() * amount);
    }

    public ItemStack getItem() {
        return this.item;
    }

    public int getOriginalPrice() {
        return this.price;
    }

    public int getPrice() {
        int newPrice = (int) ((double) this.price * (this.demand / this.supply));
        if (newPrice <= 0) {
            return 1;
        }
        return newPrice;
    }

    public double getSupply() {
        return this.supply;
    }

    public double getDemand() {
        return this.demand;
    }

    public NBTTagCompound writeToNBT(NBTTagCompound compound) {
        this.item.writeToNBT(compound);
        compound.setInteger("Price", this.price);
        compound.setDouble("Supply", this.supply);
        compound.setDouble("Demand", this.demand);
        return compound;
    }

    public void readFromNBT(NBTTagCompound compound) {
        this.item = new ItemStack(compound);
        this.price = compound.getInteger("Price");
        this.supply = compound.getDouble("Supply");
        this.demand = compound.getDouble("Demand");
    }

    public static ItemPrice loadCraftItemFromNBT(NBTTagCompound compound) {
        ItemPrice itemPrice = new ItemPrice();
        itemPrice.readFromNBT(compound);
        return itemPrice.getItem().isEmpty() ? null : itemPrice;
    }
}
