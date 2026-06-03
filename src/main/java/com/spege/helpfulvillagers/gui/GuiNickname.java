package com.spege.helpfulvillagers.gui;

import com.spege.helpfulvillagers.entity.AbstractVillager;
import com.spege.helpfulvillagers.main.HelpfulVillagers;
import com.spege.helpfulvillagers.network.NicknamePacket;

import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.entity.player.EntityPlayer;

import java.io.IOException;

/**
 * Villager rename dialog. Displays a text field with the current nickname; on Enter the
 * new name is sent to the server via {@link NicknamePacket}.
 */
@SuppressWarnings("null")
public class GuiNickname extends GuiScreen {
    private final EntityPlayer player;
    private final AbstractVillager villager;
    private final int boxWidth = 150;
    private final int boxHeight = 20;
    private GuiTextField textField;
    private String name;
    private final String START_NAME;
    private boolean changeName;

    public GuiNickname(EntityPlayer player, AbstractVillager villager) {
        this.player = player;
        this.villager = villager;
        this.START_NAME = this.name = villager.nickname;
        this.changeName = false;
    }

    @Override
    public void initGui() {
        this.changeName = false;
        int posX = (this.width - this.boxWidth) / 2;
        int posY = (this.height - this.boxHeight) / 2;
        this.textField = new GuiTextField(0, this.fontRenderer, posX, posY, this.boxWidth, this.boxHeight);
        this.textField.setFocused(true);
        this.textField.setMaxStringLength(20);
        String currentName = this.villager.getCustomNameTag();
        this.textField.setText(currentName);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        int posX = (this.width - this.boxWidth) / 2;
        int posY = (this.height - this.boxHeight) / 2;
        this.drawDefaultBackground();
        this.drawString(this.fontRenderer, "Enter Nickname:", posX, posY - 20, 0xFFFFFF);
        this.textField.drawTextBox();
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        super.keyTyped(typedChar, keyCode);
        // Enter key
        if (keyCode == 28) {
            this.changeName = true;
            super.keyTyped(typedChar, 1); // Esc to close
        }
        this.textField.textboxKeyTyped(typedChar, keyCode);
    }

    @Override
    public void updateScreen() {
        this.name = this.textField.getText();
    }

    @Override
    public void onGuiClosed() {
        if (this.changeName && !this.name.equals(this.START_NAME)) {
            this.villager.nickname = this.name;
            HelpfulVillagers.network.sendToServer(new NicknamePacket(this.villager.getEntityId(), this.name));
        }
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
