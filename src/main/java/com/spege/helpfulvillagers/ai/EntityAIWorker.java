package com.spege.helpfulvillagers.ai;

import java.util.Iterator;
import java.util.Random;

import com.spege.helpfulvillagers.crafting.CraftItem;
import com.spege.helpfulvillagers.crafting.CraftTree;
import com.spege.helpfulvillagers.entity.AbstractVillager;
import com.spege.helpfulvillagers.enums.EnumActivity;
import com.spege.helpfulvillagers.util.AIHelper;

import net.minecraft.entity.ai.EntityAIBase;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.tileentity.TileEntityFurnace;
import net.minecraft.util.math.BlockPos;

/**
 * Shared base for the gathering/crafting profession AIs (lumberjack, miner, farmer, fisherman,
 * rancher). Drives the IDLE -> GATHER -> RETURN -> CRAFT -> STORE activity state machine; subclasses
 * supply {@link #gather()}.
 *
 * <p>1.12.2 migration: ChunkCoordinates -> BlockPos (tile coords via {@link net.minecraft.tileentity.TileEntity#getPos()});
 * ItemStack null -> ItemStack.EMPTY; EntityAIBase SRG method names mapped.
 */
@SuppressWarnings({ "null", "deprecation" })
public abstract class EntityAIWorker extends EntityAIBase {
    protected AbstractVillager villager;
    protected BlockPos target;
    protected float speed;
    protected int previousTime;
    protected int currentTime;
    protected float harvestTime;
    protected Random gen;
    private boolean craftInit;
    protected boolean craftCheck;
    protected boolean readyToSmelt;
    protected boolean readyToCraft;
    private CraftTree craftTree;

    public EntityAIWorker(AbstractVillager villager) {
        this.villager = villager;
        this.target = null;
        this.speed = 0.5f;
        this.currentTime = 0;
        this.previousTime = 0;
        this.harvestTime = 0.0f;
        this.gen = new Random();
        this.craftInit = false;
        this.craftCheck = false;
        this.readyToSmelt = false;
        this.readyToCraft = false;
        this.craftTree = null;
        this.setMutexBits(1);
    }

    @Override
    public boolean shouldExecute() {
        switch (this.villager.currentActivity) {
            case GATHER:
                return true;
            case RETURN:
                return false;
            case CRAFT:
                return true;
            case STORE:
                return true;
            case IDLE:
                return this.idle();
            default:
                return false;
        }
    }

    @Override
    public boolean shouldContinueExecuting() {
        switch (this.villager.currentActivity) {
            case GATHER:
                return this.gather();
            case RETURN:
                return false;
            case CRAFT:
                return this.craft();
            case STORE:
                return this.store();
            case IDLE:
                return this.idle();
            default:
                return false;
        }
    }

    protected boolean idle() {
        this.villager.currentActivity = EnumActivity.IDLE;
        if (!this.villager.world.isRemote && this.villager.homeVillage == null) {
            System.out.println("No Home Village");
            return false;
        }
        this.villager.checkGuildHall();
        if (this.villager.homeGuildHall == null) {
            return false;
        }
        if (this.villager.currentCraftItem != null && this.villager.currentCraftItem.getPriority() >= 1) {
            if ((this.readyToCraft || this.readyToSmelt) && !this.villager.nearHall()) {
                this.villager.currentActivity = EnumActivity.RETURN;
                return false;
            }
            if ((this.readyToCraft || this.readyToSmelt) && this.villager.nearHall()) {
                this.villager.currentActivity = EnumActivity.CRAFT;
                return true;
            }
            if (!this.craftCheck) {
                this.villager.currentActivity = EnumActivity.CRAFT;
                this.craftCheck = true;
                return true;
            }
        }
        if (this.villager.inventory.isFull() || !this.villager.hasTool) {
            if (this.villager.nearHall()) {
                if (this.villager.currentCraftItem != null && !this.craftCheck) {
                    this.villager.currentActivity = EnumActivity.CRAFT;
                    this.craftCheck = true;
                    return true;
                }
                if (!this.villager.inventory.isEmpty() || !this.villager.hasTool) {
                    this.villager.currentActivity = EnumActivity.STORE;
                    this.craftCheck = false;
                    return true;
                }
                this.craftCheck = false;
                return false;
            }
            this.villager.currentActivity = EnumActivity.RETURN;
            this.craftCheck = false;
            return false;
        }
        if (this.villager.world.isDaytime()) {
            this.villager.currentActivity = EnumActivity.GATHER;
            this.craftCheck = false;
            return true;
        }
        if (!this.villager.nearHall()) {
            this.villager.currentActivity = EnumActivity.RETURN;
            this.craftCheck = false;
            return false;
        }
        if (this.villager.currentCraftItem != null && !this.craftCheck) {
            this.villager.currentActivity = EnumActivity.CRAFT;
            this.craftCheck = true;
            return true;
        }
        if (!this.villager.inventory.isEmpty() || !this.villager.hasTool) {
            this.villager.currentActivity = EnumActivity.STORE;
            this.craftCheck = false;
            return true;
        }
        this.craftCheck = false;
        return true;
    }

    protected boolean store() {
        if (this.villager.homeGuildHall == null) {
            return this.idle();
        }
        if (!this.villager.inventory.isEmpty() || !this.villager.hasTool) {
            TileEntityChest chest = this.villager.homeGuildHall.getAvailableChest();
            if (chest != null) {
                this.villager.moveTo(chest.getPos(), this.speed);
            } else {
                this.villager.changeGuildHall = true;
            }
            if (chest != null
                    && AIHelper.findDistance((int) this.villager.posX, chest.getPos().getX()) <= 2
                    && AIHelper.findDistance((int) this.villager.posY, chest.getPos().getY()) <= 2
                    && AIHelper.findDistance((int) this.villager.posZ, chest.getPos().getZ()) <= 2) {
                try {
                    this.villager.inventory.dumpInventory(chest);
                } catch (NullPointerException e) {
                    // 1.7.10 closed the chest here (no-arg closeInventory); no 1.12.2 analogue.
                }
                if (!this.villager.hasTool) {
                    for (TileEntityChest toolChest : this.villager.homeGuildHall.guildChests) {
                        int index = AIHelper.chestContains(toolChest, this.villager);
                        if (index < 0) {
                            continue;
                        }
                        this.villager.inventory.swapEquipment(toolChest, index, 0);
                    }
                }
            }
        }
        if (!this.villager.hasTool && this.villager.queuedTool.isEmpty()) {
            int lowestPrice = Integer.MAX_VALUE;
            ItemStack lowestItem = ItemStack.EMPTY;
            for (int i = 0; i < this.villager.getValidTools().length; ++i) {
                ItemStack item = this.villager.getValidTools()[i];
                int price = this.villager.homeVillage.economy.getPrice(item.getDisplayName());
                if (price >= lowestPrice && !lowestItem.isEmpty()) {
                    continue;
                }
                lowestPrice = price;
                lowestItem = item;
            }
            this.villager.addCraftItem(new CraftItem(lowestItem, this.villager));
            this.villager.queuedTool = lowestItem;
        } else if (this.villager.hasTool) {
            this.villager.queuedTool = ItemStack.EMPTY;
        }
        return this.idle();
    }

    protected boolean craft() {
        if (this.villager.currentCraftItem == null) {
            return this.idle();
        }
        if (!this.craftInit) {
            this.craftTree = new CraftTree(this.villager.currentCraftItem.getItem(), this.villager);
            this.craftInit = true;
        }
        if (!this.readyToSmelt) {
            if (!this.villager.smeltablesNeeded.isEmpty()) {
                Iterator<ItemStack> iterator = this.villager.smeltablesNeeded.iterator();
                while (iterator.hasNext()) {
                    ItemStack currItem = iterator.next();
                    this.villager.inventory.storeAsCollected(currItem, true);
                    if (currItem.getCount() <= 0) {
                        iterator.remove();
                        continue;
                    }
                    this.villager.lookForItem(currItem);
                    if (this.villager.inventory.getTotalAmount(currItem) < currItem.getCount()) {
                        continue;
                    }
                    this.villager.inventory.storeAsCollected(currItem, true);
                    if (currItem.getCount() > 0) {
                        continue;
                    }
                    iterator.remove();
                }
            } else if (!this.villager.inventory.smeltablesCollected.isEmpty()) {
                this.readyToSmelt = true;
                return this.idle();
            }
        } else if (!this.villager.inventory.smeltablesCollected.isEmpty()) {
            if (this.villager.nearHall()) {
                TileEntityFurnace furnace = this.villager.homeGuildHall.getAvailableFurnace();
                if (furnace != null) {
                    if (!furnace.getStackInSlot(2).isEmpty()) {
                        this.villager.inventory.addItem(furnace.getStackInSlot(2));
                        furnace.setInventorySlotContents(2, ItemStack.EMPTY);
                    }
                    if (!TileEntityFurnace.isItemFuel(furnace.getStackInSlot(1))) {
                        int burnTime = this.villager.inventory.smeltablesCollected.get(0).getCount() * 200;
                        AIHelper.addFuelToFurnace(this.villager.homeVillage, furnace, burnTime);
                    } else {
                        ItemStack item = this.villager.inventory.smeltablesCollected.remove(0);
                        furnace.setInventorySlotContents(0, item);
                    }
                } else {
                    this.villager.changeGuildHall = true;
                }
            }
        } else {
            this.readyToSmelt = false;
            if (this.villager.materialsNeeded.isEmpty() && this.villager.inventory.materialsCollected.isEmpty()) {
                this.villager.resetCraftItem();
                this.craftInit = false;
                return this.idle();
            }
        }
        if (!this.readyToCraft) {
            if (!this.villager.materialsNeeded.isEmpty()) {
                Iterator<ItemStack> iterator = this.villager.materialsNeeded.iterator();
                while (iterator.hasNext()) {
                    ItemStack currItem = iterator.next();
                    this.villager.inventory.storeAsCollected(currItem, false);
                    if (currItem.getCount() <= 0) {
                        iterator.remove();
                        continue;
                    }
                    this.villager.lookForItem(currItem);
                    if (this.villager.inventory.getTotalAmount(currItem) < currItem.getCount()) {
                        continue;
                    }
                    this.villager.inventory.storeAsCollected(currItem, false);
                    if (currItem.getCount() > 0) {
                        continue;
                    }
                    iterator.remove();
                }
            } else if (!this.villager.inventory.materialsCollected.isEmpty()) {
                this.readyToCraft = true;
                return this.idle();
            }
        } else if (this.villager.nearHall() && this.villager.homeGuildHall.hasWorkbench()) {
            Iterator<CraftTree.Node> iterator = this.villager.craftChain.iterator();
            while (iterator.hasNext()) {
                CraftTree.Node currNode = iterator.next();
                if (currNode.isSmelted()) {
                    continue;
                }
                for (ItemStack i : currNode.getInputs()) {
                    if (AIHelper.removeItemStack(i, this.villager.inventory.materialsCollected)) {
                        continue;
                    }
                    System.out.println("MATERIALS NOT COLLECTED: " + i);
                }
                int amountProduced = currNode.getItemStack().getCount() + currNode.getLeftover();
                AIHelper.mergeItemStackArrays(
                        new ItemStack(currNode.getItemStack().getItem(), amountProduced, currNode.getItemStack().getMetadata()),
                        this.villager.inventory.materialsCollected);
                iterator.remove();
            }
            this.villager.inventory.dumpCollected(false);
            this.villager.resetCraftItem();
            this.craftInit = false;
            this.readyToCraft = false;
            return this.store();
        }
        return this.idle();
    }

    protected abstract boolean gather();
}
