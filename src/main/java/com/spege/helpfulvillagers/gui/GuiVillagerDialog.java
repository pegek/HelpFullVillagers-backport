package com.spege.helpfulvillagers.gui;

import com.spege.helpfulvillagers.entity.AbstractVillager;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.entity.player.EntityPlayer;

/**
 * Main interaction dialog (recruit / equip / inventory / professions / crafting / barter).
 * STUB — full UI deferred to the dedicated GUI step.
 */
public class GuiVillagerDialog extends GuiScreen {
    protected final EntityPlayer player;
    protected final AbstractVillager villager;

    public GuiVillagerDialog(EntityPlayer player, AbstractVillager villager) {
        this.player = player;
        this.villager = villager;
    }
}
