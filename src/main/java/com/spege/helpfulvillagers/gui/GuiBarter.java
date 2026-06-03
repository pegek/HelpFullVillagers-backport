package com.spege.helpfulvillagers.gui;

import com.spege.helpfulvillagers.entity.AbstractVillager;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;

/** Barter/economy exchange screen. STUB */
@SuppressWarnings("deprecation")
public class GuiBarter extends GuiContainer {
    protected final EntityPlayer player;
    protected final AbstractVillager villager;

    public GuiBarter(EntityPlayer player, AbstractVillager villager) {
        super(new StubContainer());
        this.player = player;
        this.villager = villager;
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {}

    private static class StubContainer extends Container {
        @Override
        public boolean canInteractWith(EntityPlayer player) { return true; }
    }
}
