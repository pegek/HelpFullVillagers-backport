package com.spege.helpfulvillagers.gui;

import com.spege.helpfulvillagers.entity.AbstractVillager;
import com.spege.helpfulvillagers.main.HelpfulVillagers;
import com.spege.helpfulvillagers.network.ConstructionJobPacket;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ResourceLocation;

public class GuiConstruction extends GuiScreen {
    private static final ResourceLocation BACKGROUND = new ResourceLocation("helpfulvillagers", "textures/gui/dialog_background.png");
    private EntityPlayer player;
    private AbstractVillager villager;
    public final int xSizeOfTexture = 120;
    public final int ySizeOfTexture = 105;

    public GuiConstruction(EntityPlayer player, AbstractVillager villager) {
        this.player = player;
        this.villager = villager;
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        this.drawDefaultBackground();
        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
        this.mc.getTextureManager().bindTexture(BACKGROUND);
        int posX = (this.width - this.xSizeOfTexture) / 2;
        int posY = (this.height - this.ySizeOfTexture) / 2;
        this.drawTexturedModalRect(posX, posY, 10, 10, this.xSizeOfTexture, this.ySizeOfTexture);
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    public void initGui() {
        this.buttonList.clear();
        int posX = (this.width - this.xSizeOfTexture) / 2;
        int posY = (this.height - this.ySizeOfTexture) / 2;
        this.buttonList.add(new GuiButton(0, posX + 10, posY + 5, 100, 20, "Demolish"));
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        HelpfulVillagers.network.sendToServer(new ConstructionJobPacket(this.villager.getEntityId(), this.player.getEntityId(), button.id));
    }
}
