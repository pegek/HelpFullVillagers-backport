package com.spege.helpfulvillagers.inventory;

import java.util.ArrayList;
import java.util.Arrays;

import com.spege.helpfulvillagers.entity.AbstractVillager;
import com.spege.helpfulvillagers.main.HelpfulVillagers;
import com.spege.helpfulvillagers.network.InventoryPacket;
import com.spege.helpfulvillagers.util.AIHelper;

import net.minecraft.block.Block;
import net.minecraft.block.BlockLog;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;

/**
 * The villager's backing inventory: 27 main slots + 5 equipment slots (0 = held tool,
 * 1-4 = armour). Also accumulates gathered materials/smeltables during a craft job.
 *
 * <p>1.12.2 migration: all slot storage uses {@link ItemStack#EMPTY} instead of null,
 * and the IInventory contract gained isEmpty()/getField/setField/clear()/getDisplayName()
 * plus EntityPlayer parameters on open/closeInventory.
 */
@SuppressWarnings("null")
public class InventoryVillager implements IInventory {
    private final ItemStack[] mainInventory;
    private final ItemStack[] equipmentInventory;
    public ArrayList<ItemStack> materialsCollected = new ArrayList<ItemStack>();
    public ArrayList<ItemStack> smeltablesCollected = new ArrayList<ItemStack>();
    String inventoryTitle;
    public AbstractVillager owner;

    public InventoryVillager(AbstractVillager abstractEntity) {
        this.mainInventory = new ItemStack[this.getSizeInventory()];
        this.equipmentInventory = new ItemStack[this.getSizeEquipment()];
        Arrays.fill(this.mainInventory, ItemStack.EMPTY);
        Arrays.fill(this.equipmentInventory, ItemStack.EMPTY);
        this.owner = abstractEntity;
    }

    @Override
    public int getSizeInventory() {
        return 27;
    }

    public int getSizeEquipment() {
        return 5;
    }

    @Override
    public ItemStack getStackInSlot(int index) {
        if (index < 0) {
            return ItemStack.EMPTY;
        }
        ItemStack[] aitemstack = this.mainInventory;
        if (index >= aitemstack.length) {
            index -= aitemstack.length;
            aitemstack = this.equipmentInventory;
        }
        if (index < aitemstack.length) {
            return aitemstack[index];
        }
        return ItemStack.EMPTY;
    }

    @Override
    public ItemStack decrStackSize(int index, int amount) {
        ItemStack[] aitemstack = this.mainInventory;
        if (index >= this.mainInventory.length) {
            aitemstack = this.equipmentInventory;
            index -= this.mainInventory.length;
        }
        if (index >= 0 && index < aitemstack.length && !aitemstack[index].isEmpty()) {
            if (aitemstack[index].getCount() <= amount) {
                ItemStack itemstack = aitemstack[index];
                aitemstack[index] = ItemStack.EMPTY;
                return itemstack;
            }
            ItemStack itemstack = aitemstack[index].splitStack(amount);
            if (aitemstack[index].getCount() == 0) {
                aitemstack[index] = ItemStack.EMPTY;
            }
            return itemstack;
        }
        return ItemStack.EMPTY;
    }

    @Override
    public void setInventorySlotContents(int index, ItemStack itemStack) {
        if (index < 0) {
            return;
        }
        if (index >= this.mainInventory.length) {
            if ((index -= this.mainInventory.length) >= this.equipmentInventory.length) {
                return;
            }
            this.equipmentInventory[index] = itemStack;
        } else {
            this.mainInventory[index] = itemStack;
        }
    }

    public void setMainContents(int index, ItemStack itemStack) {
        this.mainInventory[index] = itemStack;
    }

    public void setEquipmentContents(int index, ItemStack itemStack) {
        this.equipmentInventory[index] = itemStack;
    }

    @Override
    public ItemStack removeStackFromSlot(int i) {
        ItemStack stack = this.getStackInSlot(i);
        if (!stack.isEmpty()) {
            this.setInventorySlotContents(i, ItemStack.EMPTY);
        }
        return stack;
    }

    @Override
    public String getName() {
        return this.inventoryTitle != null ? this.inventoryTitle : "";
    }

    @Override
    public ITextComponent getDisplayName() {
        return new TextComponentString(this.getName());
    }

    @Override
    public int getInventoryStackLimit() {
        return 64;
    }

    @Override
    public boolean isUsableByPlayer(EntityPlayer player) {
        return true;
    }

    @Override
    public boolean isItemValidForSlot(int slot, ItemStack stack) {
        if (stack.isEmpty()) {
            return true;
        }
        if (slot == 27) {
            return this.owner.isValidTool(stack);
        }
        if (slot == 28) {
            return stack.getItem() instanceof ItemArmor
                    && ((ItemArmor) stack.getItem()).armorType == EntityEquipmentSlot.HEAD;
        }
        if (slot == 29) {
            return stack.getItem() instanceof ItemArmor
                    && ((ItemArmor) stack.getItem()).armorType == EntityEquipmentSlot.CHEST;
        }
        if (slot == 30) {
            return stack.getItem() instanceof ItemArmor
                    && ((ItemArmor) stack.getItem()).armorType == EntityEquipmentSlot.LEGS;
        }
        if (slot == 31) {
            return stack.getItem() instanceof ItemArmor
                    && ((ItemArmor) stack.getItem()).armorType == EntityEquipmentSlot.FEET;
        }
        return true;
    }

    @Override
    public boolean isEmpty() {
        // Preserves the 1.7.10 semantics: only the main inventory is considered here
        // (equipment slots are intentionally ignored by the villager's own logic).
        for (ItemStack i : this.mainInventory) {
            if (!i.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    public boolean isFull() {
        for (ItemStack i : this.mainInventory) {
            if (i.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    public int containsItem(ItemStack item) {
        for (int i = 0; i < this.mainInventory.length; ++i) {
            if (!this.mainInventory[i].isEmpty() && this.mainInventory[i].getItem() == item.getItem()) {
                return i;
            }
        }
        return -1;
    }

    public int containsItem() {
        for (int i = 0; i < this.mainInventory.length; ++i) {
            if (!this.mainInventory[i].isEmpty() && this.owner.isValidTool(this.mainInventory[i])) {
                return i;
            }
        }
        return -1;
    }

    public int containsItemWithMetadata(ItemStack item) {
        for (int i = 0; i < this.mainInventory.length; ++i) {
            if (!this.mainInventory[i].isEmpty()
                    && this.mainInventory[i].getItem() == item.getItem()
                    && this.mainInventory[i].getMetadata() == item.getMetadata()) {
                return i;
            }
        }
        return -1;
    }

    public int getTotalAmount(ItemStack item) {
        int count = 0;
        for (ItemStack stack : this.mainInventory) {
            if (!stack.isEmpty() && stack.getItem().equals(item.getItem())) {
                count += stack.getCount();
            }
        }
        return count;
    }

    public int findSolidBlock(ArrayList<Block> exclude) {
        for (int i = 0; i < this.mainInventory.length; ++i) {
            if (this.mainInventory[i].isEmpty()) {
                continue;
            }
            Block block = Block.getBlockFromItem(this.mainInventory[i].getItem());
            if (!exclude.contains(block) && block.getDefaultState().isOpaqueCube()) {
                return i;
            }
        }
        return -1;
    }

    public void swapItems(int index1, int index2) {
        if (index1 >= 0 && index1 < this.mainInventory.length && index2 >= 0 && index2 < this.mainInventory.length) {
            ItemStack item1 = this.mainInventory[index1];
            this.mainInventory[index1] = this.mainInventory[index2];
            this.mainInventory[index2] = item1;
            this.syncInventory();
        }
    }

    public void swapItems(TileEntityChest chest, int chestIndex, int invIndex) {
        if (chestIndex >= 0 && chestIndex < chest.getSizeInventory() && invIndex >= 0 && invIndex < this.mainInventory.length) {
            ItemStack item1 = chest.getStackInSlot(chestIndex);
            ItemStack item2 = this.mainInventory[invIndex];
            chest.setInventorySlotContents(chestIndex, item2);
            this.mainInventory[invIndex] = item1;
            this.syncInventory();
        }
    }

    public void swapEquipment(int index1, int index2) {
        if (index1 >= 0 && index1 < this.mainInventory.length && index2 >= 0 && index2 < this.equipmentInventory.length) {
            ItemStack item1 = this.mainInventory[index1];
            this.mainInventory[index1] = this.equipmentInventory[index2];
            this.equipmentInventory[index2] = item1;
            this.syncInventory();
        }
    }

    public void swapEquipment(TileEntityChest chest, int chestIndex, int invIndex) {
        if (chestIndex >= 0 && chestIndex < chest.getSizeInventory() && invIndex >= 0 && invIndex < this.equipmentInventory.length) {
            ItemStack item1 = chest.getStackInSlot(chestIndex);
            ItemStack item2 = this.equipmentInventory[invIndex];
            chest.setInventorySlotContents(chestIndex, item2);
            this.equipmentInventory[invIndex] = item1;
            this.syncInventory();
        }
    }

    public void addItem(ItemStack item) {
        int i;
        for (i = 0; i < this.equipmentInventory.length; ++i) {
            if (this.equipmentInventory[i].isEmpty() && this.isItemValidForSlot(this.mainInventory.length + i, item)) {
                this.equipmentInventory[i] = item;
                return;
            }
        }
        for (i = 0; i < this.mainInventory.length; ++i) {
            if (this.mainInventory[i].isEmpty() || !this.mainInventory[i].getDisplayName().equals(item.getDisplayName())) {
                continue;
            }
            int temp = item.getCount();
            while (this.mainInventory[i].getCount() < this.mainInventory[i].getMaxStackSize()) {
                this.mainInventory[i].grow(1);
                if (--temp <= 0) {
                    return;
                }
            }
            item.setCount(temp);
        }
        for (i = 0; i < this.mainInventory.length; ++i) {
            if (this.mainInventory[i].isEmpty()) {
                this.mainInventory[i] = item.copy();
                return;
            }
        }
        // Fixed: drop the leftover stack server-side. The 1.7.10 original had an inverted side check
        // (dropped only on the client), so on the server overflow items were silently lost and on the
        // client a ghost EntityItem was spawned. Entity spawning must be server-side — matches the
        // sibling dropFromInventory() guard below.
        if (!this.owner.world.isRemote) {
            this.dropItem(item);
        }
    }

    private void dropItem(ItemStack item) {
        if (!item.isEmpty()) {
            EntityItem worldItem = new EntityItem(this.owner.world,
                    this.owner.posX, this.owner.posY, this.owner.posZ, item);
            this.owner.world.spawnEntity(worldItem);
        }
    }

    public void dropFromInventory(int i) {
        if (!this.owner.world.isRemote) {
            ItemStack stack = this.getStackInSlot(i);
            if (!stack.isEmpty()) {
                this.dropItem(stack);
                this.setInventorySlotContents(i, ItemStack.EMPTY);
            }
        }
    }

    public void dumpInventory(TileEntityChest chest) {
        this.syncInventory();
        block0:
        for (int i = 0; i < this.mainInventory.length; ++i) {
            if (this.mainInventory[i].isEmpty()) {
                continue;
            }
            for (int j = 0; j < chest.getSizeInventory(); ++j) {
                ItemStack chestItem = chest.getStackInSlot(j);
                if (chestItem.isEmpty()) {
                    chest.setInventorySlotContents(j, this.mainInventory[i]);
                    this.owner.homeVillage.economy.increaseItemSupply(this.owner, this.mainInventory[i]);
                    this.mainInventory[i] = ItemStack.EMPTY;
                    continue block0;
                }
                if (chestItem.getCount() >= chestItem.getMaxStackSize()
                        || !this.mainInventory[i].getDisplayName().equals(chestItem.getDisplayName())) {
                    continue;
                }
                int chestStackSize = chestItem.getCount();
                int invStackSize = this.mainInventory[i].getCount();
                if ((chestStackSize += invStackSize) > chestItem.getMaxStackSize()) {
                    invStackSize = chestStackSize - chestItem.getMaxStackSize();
                    chestStackSize = chestItem.getMaxStackSize();
                } else {
                    invStackSize = 0;
                }
                this.mainInventory[i].setCount(invStackSize);
                if (this.mainInventory[i].getCount() <= 0) {
                    this.mainInventory[i] = ItemStack.EMPTY;
                }
                chestItem.setCount(chestStackSize);
                chest.setInventorySlotContents(j, chestItem);
                this.owner.homeVillage.economy.increaseItemSupply(this.owner, chestItem);
            }
        }
        chest.markDirty();
    }

    public void dumpInventory() {
        for (int i = 0; i < this.mainInventory.length + this.equipmentInventory.length; ++i) {
            this.dropFromInventory(i);
        }
    }

    public void storeAsCollected(ItemStack item, boolean smelt) {
        ArrayList<ItemStack> collected = smelt ? this.smeltablesCollected : this.materialsCollected;
        boolean sensitive = this.owner.currentCraftItem.isSensitive();
        Block itemBlock = Block.getBlockFromItem(item.getItem());
        for (int i = 0; i < this.mainInventory.length; ++i) {
            ItemStack invItem = this.mainInventory[i];
            if (invItem.isEmpty() || invItem.getCount() <= 0) {
                continue;
            }
            Block invBlock = Block.getBlockFromItem(invItem.getItem());
            boolean matches = sensitive
                    ? invItem.getDisplayName().equals(item.getDisplayName())
                    : invItem.getItem().equals(item.getItem())
                            || (itemBlock instanceof BlockLog && invBlock instanceof BlockLog);
            if (!matches) {
                continue;
            }
            if (invItem.getCount() >= item.getCount()) {
                int itemSize = item.getCount();
                invItem.shrink(itemSize);
                AIHelper.mergeItemStackArrays(new ItemStack(item.getItem(), itemSize), collected);
                if (invItem.getCount() <= 0) {
                    this.mainInventory[i] = ItemStack.EMPTY;
                }
                item.setCount(0);
                return;
            }
            int itemSize = invItem.getCount();
            item.shrink(invItem.getCount());
            AIHelper.mergeItemStackArrays(new ItemStack(invItem.getItem(), itemSize), collected);
            this.mainInventory[i] = ItemStack.EMPTY;
        }
    }

    public void dumpCollected(boolean smelt) {
        ArrayList<ItemStack> tempList = new ArrayList<ItemStack>();
        int slot = 0;
        if (smelt) {
            for (ItemStack i : this.smeltablesCollected) {
                if (!this.mainInventory[slot].isEmpty()) {
                    tempList.add(this.mainInventory[slot].copy());
                }
                this.mainInventory[slot] = i;
                ++slot;
            }
        } else {
            for (ItemStack i : this.materialsCollected) {
                if (i.getDisplayName().equals(this.owner.currentCraftItem.getItem().getDisplayName())) {
                    this.owner.storeCraftedItem();
                    continue;
                }
                if (!this.mainInventory[slot].isEmpty()) {
                    tempList.add(this.mainInventory[slot].copy());
                }
                this.mainInventory[slot] = i;
                ++slot;
            }
        }
        for (ItemStack i : tempList) {
            this.addItem(i);
        }
    }

    public void decrementSlot(int index) {
        if (this.mainInventory[index].isEmpty()) {
            return;
        }
        this.mainInventory[index].shrink(1);
        if (this.mainInventory[index].getCount() <= 0) {
            this.mainInventory[index] = ItemStack.EMPTY;
        }
    }

    public ItemStack getCurrentItem() {
        return this.equipmentInventory[0];
    }

    public void setCurrentItem(ItemStack itemStack) {
        this.equipmentInventory[0] = itemStack;
    }

    public void syncInventory() {
        if (!this.owner.world.isRemote) {
            HelpfulVillagers.network.sendToAll(
                    new InventoryPacket(this.owner.getEntityId(), this.mainInventory, this.equipmentInventory));
        }
    }

    public void syncEquipment() {
        if (!this.owner.world.isRemote) {
            HelpfulVillagers.network.sendToAll(
                    new InventoryPacket(this.owner.getEntityId(), null, this.equipmentInventory));
        }
    }

    public NBTTagList writeToNBT(NBTTagList list) {
        NBTTagCompound nbt;
        for (int i = 0; i < this.mainInventory.length; ++i) {
            if (this.mainInventory[i].isEmpty()) {
                continue;
            }
            nbt = new NBTTagCompound();
            nbt.setByte("Slot", (byte) i);
            this.mainInventory[i].writeToNBT(nbt);
            list.appendTag(nbt);
        }
        for (int i = 0; i < this.equipmentInventory.length; ++i) {
            if (this.equipmentInventory[i].isEmpty()) {
                continue;
            }
            nbt = new NBTTagCompound();
            nbt.setByte("Slot", (byte) (i + this.mainInventory.length));
            this.equipmentInventory[i].writeToNBT(nbt);
            list.appendTag(nbt);
        }
        for (ItemStack stack : this.materialsCollected) {
            nbt = new NBTTagCompound();
            nbt.setByte("Slot", (byte) -1);
            stack.writeToNBT(nbt);
            list.appendTag(nbt);
        }
        for (ItemStack stack : this.smeltablesCollected) {
            nbt = new NBTTagCompound();
            nbt.setByte("Slot", (byte) -1);
            stack.writeToNBT(nbt);
            list.appendTag(nbt);
        }
        return list;
    }

    public void readFromNBT(NBTTagList list) {
        for (int i = 0; i < list.tagCount(); ++i) {
            NBTTagCompound nbt = list.getCompoundTagAt(i);
            byte slot = nbt.getByte("Slot");
            ItemStack itemstack = new ItemStack(nbt);
            if (itemstack.isEmpty()) {
                continue;
            }
            if (slot >= 0) {
                this.setInventorySlotContents(slot, itemstack);
                continue;
            }
            this.addItem(itemstack);
        }
    }

    @Override
    public boolean hasCustomName() {
        return false;
    }

    @Override
    public void openInventory(EntityPlayer player) {
    }

    @Override
    public void closeInventory(EntityPlayer player) {
    }

    @Override
    public void markDirty() {
    }

    @Override
    public int getField(int id) {
        return 0;
    }

    @Override
    public void setField(int id, int value) {
    }

    @Override
    public int getFieldCount() {
        return 0;
    }

    @Override
    public void clear() {
        Arrays.fill(this.mainInventory, ItemStack.EMPTY);
        Arrays.fill(this.equipmentInventory, ItemStack.EMPTY);
    }
}
