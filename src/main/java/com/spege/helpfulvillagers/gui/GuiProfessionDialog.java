package com.spege.helpfulvillagers.gui;

import com.spege.helpfulvillagers.entity.AbstractVillager;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.entity.player.EntityPlayer;

/** Profession-selection dialog. STUB */
public class GuiProfessionDialog extends GuiScreen {
    protected final EntityPlayer player;
    protected final AbstractVillager villager;

    public GuiProfessionDialog(EntityPlayer player, AbstractVillager villager) {
        this.player = player;
        this.villager = villager;
    }
}
