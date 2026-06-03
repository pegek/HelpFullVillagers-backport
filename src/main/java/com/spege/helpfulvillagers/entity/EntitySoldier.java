package com.spege.helpfulvillagers.entity;

import java.util.ArrayList;

import com.spege.helpfulvillagers.ai.EntityAIGuardVillageSoldier;
import com.spege.helpfulvillagers.enums.EnumActivity;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraft.pathfinding.PathNavigateGround;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** Soldier villager: guards the village in melee with a sword. */
@SuppressWarnings("null")
public class EntitySoldier extends AbstractVillager {
    private final ItemStack[] soldierTools = new ItemStack[] {
            new ItemStack(Items.DIAMOND_SWORD), new ItemStack(Items.GOLDEN_SWORD), new ItemStack(Items.IRON_SWORD),
            new ItemStack(Items.STONE_SWORD), new ItemStack(Items.WOODEN_SWORD) };

    public EntitySoldier(World world) {
        super(world);
        this.init();
    }

    public EntitySoldier(AbstractVillager villager) {
        super(villager);
        this.init();
    }

    private void init() {
        this.profession = 4;
        this.profName = "Soldier";
        this.currentActivity = EnumActivity.IDLE;
        this.setTools(this.soldierTools);
        this.getNewGuildHall();
        this.addThisAI();
    }

    private void addThisAI() {
        if (this.getNavigator() instanceof PathNavigateGround) {
            ((PathNavigateGround) this.getNavigator()).setBreakDoors(false);
        }
        this.tasks.addTask(2, new EntityAIGuardVillageSoldier(this));
    }

    @Override
    public boolean shouldReturn() {
        return false;
    }

    public boolean isFullyArmored() {
        for (int i = 28; i < 32; ++i) {
            ItemStack armorPiece = this.inventory.getStackInSlot(i);
            if (!armorPiece.isEmpty()) {
                continue;
            }
            return false;
        }
        return true;
    }

    @Override
    public ArrayList<BlockPos> getValidCoords() {
        return null;
    }

    @Override
    public boolean isValidTool(ItemStack item) {
        return item.getItem() instanceof ItemSword;
    }

    @Override
    protected boolean canCraft() {
        return false;
    }
}
