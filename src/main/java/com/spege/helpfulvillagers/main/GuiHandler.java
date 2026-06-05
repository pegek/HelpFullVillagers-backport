package com.spege.helpfulvillagers.main;

import com.spege.helpfulvillagers.entity.AbstractVillager;
import com.spege.helpfulvillagers.gui.GuiBarter;
import com.spege.helpfulvillagers.gui.GuiConstruction;
import com.spege.helpfulvillagers.gui.GuiCraftStats;
import com.spege.helpfulvillagers.gui.GuiCraftingMenu;
import com.spege.helpfulvillagers.gui.GuiNickname;
import com.spege.helpfulvillagers.gui.GuiProfessionDialog;
import com.spege.helpfulvillagers.gui.GuiTeachRecipe;
import com.spege.helpfulvillagers.gui.GuiVillagerDialog;
import com.spege.helpfulvillagers.gui.GuiVillagerInventory;
import com.spege.helpfulvillagers.inventory.ContainerInventoryVillager;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.network.IGuiHandler;

/**
 * Routes Forge GUI open requests (by id) to the correct screen / container pair.
 * GUI IDs match the 1.7.10 original:
 * 0 = VillagerDialog, 1 = ProfessionDialog, 2 = VillagerInventory, 3 = Nickname,
 * 4 = CraftingMenu, 5 = CraftStats, 6 = TeachRecipe, 7 = Barter.
 * {@code posX} carries the villager entity id (convention from the 1.7.10 original).
 *
 * <p>1.12.2: {@code cpw.mods.fml.common.network.IGuiHandler} → {@code net.minecraftforge.fml.common.network.IGuiHandler}.
 */
@SuppressWarnings("deprecation")
public class GuiHandler implements IGuiHandler {
    @Override
    public Object getServerGuiElement(int guiId, EntityPlayer player, World world, int posX, int posY, int posZ) {
        AbstractVillager villager = (AbstractVillager) world.getEntityByID(posX);
        if (villager != null) {
            switch (guiId) {
                case 2:
                    return new ContainerInventoryVillager(player.inventory, villager.inventory, villager);
                case 6:
                    return new GuiTeachRecipe.VillagerContainerWorkbench(player);
                default:
                    break;
            }
        }
        return null;
    }

    @Override
    public Object getClientGuiElement(int guiId, EntityPlayer player, World world, int posX, int posY, int posZ) {
        AbstractVillager villager = (AbstractVillager) world.getEntityByID(posX);
        if (villager != null) {
            switch (guiId) {
                case 0:
                    return new GuiVillagerDialog(player, villager);
                case 1:
                    return new GuiProfessionDialog(player, villager);
                case 2:
                    return new GuiVillagerInventory(villager, player.inventory, villager.inventory);
                case 3:
                    return new GuiNickname(player, villager);
                case 4:
                    return new GuiCraftingMenu(player, villager);
                case 5:
                    return new GuiCraftStats(player, villager);
                case 6:
                    return new GuiTeachRecipe(player, villager);
                case 7:
                    return new GuiBarter(player, villager);
                case 8:
                    return new GuiConstruction(player, villager);
                default:
                    break;
            }
        }
        return null;
    }
}
