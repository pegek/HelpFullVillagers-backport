package com.spege.helpfulvillagers.gui;

import com.spege.helpfulvillagers.entity.AbstractVillager;
import com.spege.helpfulvillagers.main.HelpfulVillagers;
import com.spege.helpfulvillagers.network.GUICommandPacket;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ResourceLocation;

import java.io.IOException;

/**
 * Main interaction dialog (recruit / equip / inventory / professions / crafting / barter).
 * Shows a column of buttons whose last entry depends on the villager's profession.
 */
@SuppressWarnings("null")
public class GuiVillagerDialog extends GuiScreen {
    private static final ResourceLocation BACKGROUND =
            new ResourceLocation("helpfulvillagers", "textures/gui/dialog_background.png");

    private final EntityPlayer player;
    private final AbstractVillager villager;
    public final int xSizeOfTexture;
    public final int ySizeOfTexture;

    public GuiVillagerDialog(EntityPlayer player, AbstractVillager villager) {
        this.player = player;
        this.villager = villager;
        this.xSizeOfTexture = 120;
        this.ySizeOfTexture = villager.profession != 0 ? 130 : 105;
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

        if (this.villager.leader == null) {
            this.buttonList.add(new GuiButton(0, posX + 10, posY + 5, 100, 20, "Follow Me"));
        } else {
            this.buttonList.add(new GuiButton(1, posX + 10, posY + 5, 100, 20, "Stop Following"));
        }
        this.buttonList.add(new GuiButton(2, posX + 10, posY + 30, 100, 20, "Trade"));
        this.buttonList.add(new GuiButton(3, posX + 10, posY + 55, 100, 20, "Change Profession"));
        this.buttonList.add(new GuiButton(4, posX + 10, posY + 80, 100, 20, "Give Nickname"));

        if (this.villager.profession == 4 || this.villager.profession == 5) {
            this.buttonList.add(new GuiButton(5, posX + 10, posY + 105, 100, 20, "Guard Villager"));
        } else if (this.villager.profession == 6) {
            this.buttonList.add(new GuiButton(8, posX + 10, posY + 105, 100, 20, "Barter"));
        } else if (this.villager.profession != 0) {
            this.buttonList.add(new GuiButton(6, posX + 10, posY + 105, 100, 20, "Crafting"));
        }
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        int guiCommand = button.id;
        this.mc.player.closeScreen();
        if (this.villager.world.isRemote) {
            this.villager.guiCommand = guiCommand;
            HelpfulVillagers.network.sendToServer(new GUICommandPacket(this.villager.getEntityId(), guiCommand));
        }
    }
}
