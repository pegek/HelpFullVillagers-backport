package com.spege.helpfulvillagers.gui;

import com.spege.helpfulvillagers.entity.AbstractVillager;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.ContainerWorkbench;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;

/**
 * Teach-custom-recipe screen; inner container gives the server a vanilla workbench container
 * rooted at BlockPos(0,0,0). STUB — full logic deferred to the dedicated GUI step.
 */
@SuppressWarnings("deprecation")
public class GuiTeachRecipe extends GuiContainer {
    protected final EntityPlayer player;
    protected final AbstractVillager villager;

    public GuiTeachRecipe(EntityPlayer player, AbstractVillager villager) {
        super(new StubContainer());
        this.player = player;
        this.villager = villager;
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {}

    // ------------------------------------------------------------------
    // Server-side container returned by GuiHandler for GUI id 6.
    // ------------------------------------------------------------------
    public static class VillagerContainerWorkbench extends ContainerWorkbench {
        public VillagerContainerWorkbench(EntityPlayer player) {
            super(player.inventory, player.world, new BlockPos((int) player.posX, (int) player.posY, (int) player.posZ));
        }

        @Override
        public boolean canInteractWith(EntityPlayer player) {
            return true;
        }

        @Override
        public void onCraftMatrixChanged(IInventory inventoryIn) {
            // stub: no recipe output update yet
        }

        @Override
        public ItemStack transferStackInSlot(EntityPlayer playerIn, int index) {
            // stub: no shift-click handling yet
            return ItemStack.EMPTY;
        }
    }

    private static class StubContainer extends Container {
        @Override
        public boolean canInteractWith(EntityPlayer player) { return true; }
    }
}
