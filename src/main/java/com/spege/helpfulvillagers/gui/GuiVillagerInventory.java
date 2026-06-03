package com.spege.helpfulvillagers.gui;

import com.spege.helpfulvillagers.entity.AbstractVillager;
import com.spege.helpfulvillagers.entity.EntityArcher;
import com.spege.helpfulvillagers.entity.EntityFarmer;
import com.spege.helpfulvillagers.entity.EntityFisherman;
import com.spege.helpfulvillagers.entity.EntityLumberjack;
import com.spege.helpfulvillagers.entity.EntityMiner;
import com.spege.helpfulvillagers.entity.EntityRancher;
import com.spege.helpfulvillagers.entity.EntitySoldier;
import com.spege.helpfulvillagers.inventory.ContainerInventoryVillager;

import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.inventory.IInventory;
import net.minecraft.util.ResourceLocation;

/**
 * Villager bag inventory screen. Renders a chest-like background with per-profession
 * tool-silhouette overlays in empty equipment slots.
 */
@SuppressWarnings({ "null", "deprecation" })
public class GuiVillagerInventory extends GuiContainer {
    private static final ResourceLocation BACKGROUND =
            new ResourceLocation("helpfulvillagers", "textures/gui/villager_trade.png");

    private final int xSizeOfTexture;
    private final int ySizeOfTexture;
    private final AbstractVillager villager;

    public GuiVillagerInventory(AbstractVillager villager, IInventory playerInventory, IInventory villagerInventory) {
        super(new ContainerInventoryVillager(playerInventory, villagerInventory, villager));
        this.xSizeOfTexture = this.xSize;
        this.ySizeOfTexture = this.ySize + 7;
        this.villager = villager;
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        // No foreground labels — matches original
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        this.mc.getTextureManager().bindTexture(BACKGROUND);
        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
        int x = (this.width - this.xSize) / 2;
        int y = (this.height - this.ySize) / 2;
        this.drawTexturedModalRect(x, y, 0, 0, this.xSizeOfTexture, this.ySizeOfTexture);

        // Armor slot silhouettes when empty (slots 28–31)
        if (this.villager.inventory.getStackInSlot(28).isEmpty()) {
            this.drawTexturedModalRect(x + 64, y + 72, 176, 0, 10, 9);
        }
        if (this.villager.inventory.getStackInSlot(29).isEmpty()) {
            this.drawTexturedModalRect(x + 80, y + 70, 176, 9, 14, 13);
        }
        if (this.villager.inventory.getStackInSlot(30).isEmpty()) {
            this.drawTexturedModalRect(x + 100, y + 70, 176, 22, 14, 13);
        }
        if (this.villager.inventory.getStackInSlot(31).isEmpty()) {
            this.drawTexturedModalRect(x + 116, y + 71, 176, 35, 14, 10);
        }

        // Tool slot silhouette when empty — profession-specific icon
        if (this.villager.getCurrentItem().isEmpty()) {
            if (this.villager instanceof EntityLumberjack) {
                this.drawTexturedModalRect(x + 43, y + 69, 176, 45, 14, 14);
            } else if (this.villager instanceof EntityMiner) {
                this.drawTexturedModalRect(x + 44, y + 70, 176, 59, 14, 14);
            } else if (this.villager instanceof EntityFarmer) {
                this.drawTexturedModalRect(x + 43, y + 69, 176, 73, 14, 14);
            } else if (this.villager instanceof EntitySoldier) {
                this.drawTexturedModalRect(x + 43, y + 68, 176, 88, 16, 16);
            } else if (this.villager instanceof EntityArcher) {
                this.drawTexturedModalRect(x + 44, y + 69, 176, 105, 16, 16);
            } else if (this.villager instanceof EntityFisherman) {
                this.drawTexturedModalRect(x + 44, y + 69, 176, 120, 16, 16);
            } else if (this.villager instanceof EntityRancher) {
                this.drawTexturedModalRect(x + 44, y + 69, 176, 134, 16, 16);
            }
        }
    }
}
