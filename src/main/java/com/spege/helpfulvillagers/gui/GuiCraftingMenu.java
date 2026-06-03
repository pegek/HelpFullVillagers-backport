package com.spege.helpfulvillagers.gui;

import com.spege.helpfulvillagers.entity.AbstractVillager;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;

/** Crafting job queue menu. STUB */
@SuppressWarnings("deprecation")
public class GuiCraftingMenu extends GuiContainer {
    protected final EntityPlayer player;
    protected final AbstractVillager villager;

    public GuiCraftingMenu(EntityPlayer player, AbstractVillager villager) {
        super(new StubContainer());
        this.player = player;
        this.villager = villager;
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {}

    private static class StubContainer extends Container {
        @Override
        public boolean canInteractWith(net.minecraft.entity.player.EntityPlayer player) { return true; }
    }
}
