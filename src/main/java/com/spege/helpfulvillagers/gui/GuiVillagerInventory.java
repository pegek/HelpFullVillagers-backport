package com.spege.helpfulvillagers.gui;

import com.spege.helpfulvillagers.entity.AbstractVillager;
import com.spege.helpfulvillagers.inventory.ContainerInventoryVillager;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.inventory.IInventory;
import net.minecraft.util.ResourceLocation;

/** Villager bag inventory screen. STUB */
@SuppressWarnings("deprecation")
public class GuiVillagerInventory extends GuiContainer {
    private static final ResourceLocation BACKGROUND = new ResourceLocation("textures/gui/container/generic_54.png");

    public GuiVillagerInventory(AbstractVillager villager, IInventory playerInventory, IInventory villagerInventory) {
        super(new ContainerInventoryVillager(playerInventory, villagerInventory, villager));
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        // TODO: draw custom background
    }
}
