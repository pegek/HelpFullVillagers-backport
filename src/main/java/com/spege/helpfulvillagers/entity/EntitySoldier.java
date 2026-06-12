package com.spege.helpfulvillagers.entity;

import java.util.ArrayList;

import com.spege.helpfulvillagers.ai.EntityAIGuardMeleeAttack;
import com.spege.helpfulvillagers.ai.EntityAIGuardResupply;
import com.spege.helpfulvillagers.ai.EntityAIPatrolVillage;
import com.spege.helpfulvillagers.ai.EntityAIVillageGuardTarget;
import com.spege.helpfulvillagers.enums.EnumActivity;

import com.spege.helpfulvillagers.inventory.InventoryVillager;

import net.minecraft.entity.monster.EntityCreeper;
import net.minecraft.init.Items;
import net.minecraft.item.ItemShield;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
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
        // No setBreakDoors(false) here: in 1.12.2 that maps to nodeProcessor.setCanOpenDoors(false),
        // making closed wooden doors impassable walls AND disabling EntityAIOpenDoor (it needs a
        // path through the door). addAI() already configures door pathing correctly.
        // Soldiers ignore creepers entirely (melee vs creeper = explosions in the village);
        // creepers are the archers' job.
        this.targetTasks.addTask(1, new EntityAIVillageGuardTarget(this, null,
                e -> !(e instanceof EntityCreeper)));
        this.tasks.addTask(2, new EntityAIGuardResupply(this));
        this.tasks.addTask(3, new EntityAIGuardMeleeAttack(this));
        // Priority 5: below combat/resupply, above wander, so idle guards walk their rounds.
        this.tasks.addTask(5, new EntityAIPatrolVillage(this));
    }

    @Override
    public boolean shouldReturn() {
        return false;
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
    public boolean acceptsOffhandItem(ItemStack stack) {
        return stack.getItem() instanceof ItemShield;
    }

    @Override
    public boolean needsOffhandEquipment() {
        return this.inventory.getStackInSlot(InventoryVillager.OFFHAND_SLOT).isEmpty();
    }

    @Override
    public ItemStack getDesiredOffhandItem() {
        return new ItemStack(Items.SHIELD);
    }

    @Override
    protected boolean canCraft() {
        return false;
    }
}
