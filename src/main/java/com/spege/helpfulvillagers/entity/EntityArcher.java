package com.spege.helpfulvillagers.entity;

import java.util.ArrayList;

import com.spege.helpfulvillagers.ai.EntityAIGuardVillageArcher;
import com.spege.helpfulvillagers.enums.EnumActivity;

import net.minecraft.init.Items;
import net.minecraft.item.ItemBow;
import net.minecraft.item.ItemStack;
import net.minecraft.pathfinding.PathNavigateGround;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** Archer villager: guards the village at range with a bow. */
@SuppressWarnings("null")
public class EntityArcher extends AbstractVillager {
    private final ItemStack[] archerTools = new ItemStack[] { new ItemStack(Items.BOW) };
    public final int ARROW_TIME = 20;

    public EntityArcher(World world) {
        super(world);
        this.init();
    }

    public EntityArcher(AbstractVillager villager) {
        super(villager);
        this.init();
    }

    private void init() {
        this.profession = 5;
        this.profName = "Archer";
        this.currentActivity = EnumActivity.IDLE;
        this.searchRadius = 5;
        this.setTools(this.archerTools);
        this.getNewGuildHall();
        this.addThisAI();
    }

    private void addThisAI() {
        if (this.getNavigator() instanceof PathNavigateGround) {
            ((PathNavigateGround) this.getNavigator()).setBreakDoors(false);
        }
        this.tasks.addTask(2, new EntityAIGuardVillageArcher(this));
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
        return item.getItem() instanceof ItemBow;
    }

    @Override
    protected boolean canCraft() {
        return false;
    }
}
