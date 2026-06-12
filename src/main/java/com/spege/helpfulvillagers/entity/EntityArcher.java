package com.spege.helpfulvillagers.entity;

import java.util.ArrayList;

import com.spege.helpfulvillagers.ai.EntityAIGuardVillageArcher;
import com.spege.helpfulvillagers.enums.EnumActivity;
import com.spege.helpfulvillagers.main.HelpfulVillagers;

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

    @Override
    public boolean needsCombatAmmo() {
        return !HelpfulVillagers.infiniteArrows && this.inventory.containsItem(new ItemStack(Items.ARROW)) < 0;
    }

    @Override
    public ItemStack getCombatAmmoItem() {
        return new ItemStack(Items.ARROW);
    }

    @Override
    public boolean shouldKeepInInventory(ItemStack stack) {
        return !HelpfulVillagers.infiniteArrows && stack.getItem() == Items.ARROW;
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
