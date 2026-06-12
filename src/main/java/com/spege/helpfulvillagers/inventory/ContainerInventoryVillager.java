package com.spege.helpfulvillagers.inventory;

import com.spege.helpfulvillagers.entity.AbstractVillager;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

/** Container backing the villager inventory GUI: 27 main + 6 equipment slots, plus the player inventory. */
@SuppressWarnings("null")
public class ContainerInventoryVillager extends Container {
    private AbstractVillager villager;

    public ContainerInventoryVillager(IInventory inventoryPlayer, IInventory inventoryVillager, AbstractVillager villager) {
        try {
            int width;
            int height;
            this.villager = villager;
            int slotIndex = 0;
            for (height = 0; height < 3; ++height) {
                for (width = 0; width < 9; ++width) {
                    this.addSlotToContainer(new Slot(inventoryVillager, slotIndex, width * 18 + 8, height * 18 + 9));
                    ++slotIndex;
                }
            }
            for (int slot = 0; slot < InventoryVillager.EQUIPMENT_SIZE; ++slot) {
                this.addSlotToContainer(new Slot(inventoryVillager, slotIndex, slot * 18 + 43, 68));
                ++slotIndex;
            }
            slotIndex = 0;
            for (height = 0; height < 3; ++height) {
                for (width = 0; width < 9; ++width) {
                    this.addSlotToContainer(new Slot(inventoryPlayer, slotIndex + 9, width * 18 + 8, height * 18 + 93));
                    ++slotIndex;
                }
            }
            for (int i = 0; i < 9; ++i) {
                this.addSlotToContainer(new Slot(inventoryPlayer, i, i * 18 + 8, 151));
            }
        } catch (ArrayIndexOutOfBoundsException e) {
            // empty catch block
        }
    }

    @Override
    public boolean canInteractWith(EntityPlayer player) {
        return true;
    }

    @Override
    public ItemStack transferStackInSlot(EntityPlayer player, int slotId) {
        try {
            Slot slot = this.inventorySlots.get(slotId);
            ItemStack transferStack = ItemStack.EMPTY;
            if (slot != null && slot.getHasStack()) {
                ItemStack slotStack = slot.getStack();
                transferStack = slotStack.copy();
                if (slotId < 33
                        ? !this.mergeItemStack(slotStack, 33, this.inventorySlots.size(), true)
                        : !this.mergeItemStack(slotStack, 0, 33, false)) {
                    return ItemStack.EMPTY;
                }
                if (slotStack.getCount() <= 0) {
                    slot.putStack(ItemStack.EMPTY);
                } else {
                    slot.onSlotChanged();
                }
            }
            return transferStack;
        } catch (ArrayIndexOutOfBoundsException e) {
            e.printStackTrace();
            return ItemStack.EMPTY;
        }
    }

    @Override
    public void onContainerClosed(EntityPlayer player) {
        for (int i = 27; i < 33; ++i) {
            if (this.villager.inventory.isItemValidForSlot(i, this.getSlot(i).getStack())) {
                continue;
            }
            this.villager.inventory.dropFromInventory(i);
            this.getSlot(i).putStack(ItemStack.EMPTY);
        }
    }
}
