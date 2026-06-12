package com.spege.helpfulvillagers.gui;

import com.spege.helpfulvillagers.entity.AbstractVillager;
import com.spege.helpfulvillagers.main.HelpfulVillagers;
import com.spege.helpfulvillagers.network.ProfessionChangePacket;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ResourceLocation;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/**
 * Profession-selection dialog. Shows a 2-column grid of 9 profession buttons; buttons
 * for locked guild halls are disabled with a tooltip.
 */
@SuppressWarnings("null")
public class GuiProfessionDialog extends GuiScreen {
    private static final ResourceLocation BACKGROUND =
            new ResourceLocation("helpfulvillagers", "textures/gui/dialog_background.png");

    private final EntityPlayer player;
    private final AbstractVillager villager;
    public final int xSizeOfTexture = 230;
    public final int ySizeOfTexture = 130;

    public GuiProfessionDialog(EntityPlayer player, AbstractVillager villager) {
        this.player = player;
        this.villager = villager;
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        // Update button enabled state based on unlocked halls
        for (int i = 1; i < this.buttonList.size(); ++i) {
            GuiButton btn = this.buttonList.get(i);
            try {
                btn.enabled = this.villager.homeVillage.unlockedHalls[btn.id - 1];
            } catch (NullPointerException e) {
                btn.enabled = false;
            }
        }

        this.drawDefaultBackground();
        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
        this.mc.getTextureManager().bindTexture(BACKGROUND);
        int posX = (this.width - this.xSizeOfTexture) / 2;
        int posY = (this.height - this.ySizeOfTexture) / 2;
        this.drawTexturedModalRect(posX, posY, 0, 0, this.xSizeOfTexture, this.ySizeOfTexture);
        super.drawScreen(mouseX, mouseY, partialTicks);

        // Tooltip on disabled (locked) buttons
        for (int i = 0; i < this.buttonList.size(); ++i) {
            GuiButton btn = this.buttonList.get(i);
            if (btn.isMouseOver() && !btn.enabled) {
                List<String> temp = Arrays.asList("Build Guild Hall To Unlock");
                this.drawHoveringText(temp, mouseX, mouseY);
            }
        }
    }

    @Override
    public void initGui() {
        this.buttonList.clear();
        int posX = (this.width - this.xSizeOfTexture) / 2;
        int posY = (this.height - this.ySizeOfTexture) / 2;

        this.buttonList.add(new GuiButton(0, posX + 10, posY + 5, 100, 20, "Villager"));
        this.buttonList.add(new GuiButton(1, posX + 10, posY + 30, 100, 20, "Lumberjack"));
        this.buttonList.add(new GuiButton(2, posX + 10, posY + 55, 100, 20, "Miner"));
        this.buttonList.add(new GuiButton(3, posX + 10, posY + 80, 100, 20, "Farmer"));
        this.buttonList.add(new GuiButton(4, posX + 120, posY + 5, 100, 20, "Soldier"));
        this.buttonList.add(new GuiButton(5, posX + 120, posY + 30, 100, 20, "Archer"));
        this.buttonList.add(new GuiButton(6, posX + 120, posY + 55, 100, 20, "Merchant"));
        this.buttonList.add(new GuiButton(7, posX + 120, posY + 80, 100, 20, "Fisherman"));
        this.buttonList.add(new GuiButton(8, posX + 10, posY + 105, 100, 20, "Rancher"));
        this.buttonList.add(new GuiButton(9, posX + 120, posY + 105, 100, 20, "Builder"));
        // Third-row button sits just below the 230x130 background panel — cosmetic, flagged in spec.
        this.buttonList.add(new GuiButton(10, posX + 65, posY + 130, 100, 20, "Cleric"));
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        this.mc.player.closeScreen();
        this.villager.changeProfession(button.id);
        HelpfulVillagers.network.sendToServer(new ProfessionChangePacket(this.villager.getEntityId(), button.id));
    }
}
