package com.spege.helpfulvillagers.entity;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import com.spege.helpfulvillagers.ai.EntityAIClericRestock;
import com.spege.helpfulvillagers.ai.EntityAIClericSupport;
import com.spege.helpfulvillagers.enums.EnumActivity;

import net.minecraft.entity.ai.EntityAIAvoidEntity;
import net.minecraft.entity.ai.EntityAIRestrictOpenDoor;
import net.minecraft.entity.monster.EntityMob;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * Cleric villager: heals/cleanses villagers with essence brewed from mob drops at his guild's
 * brewing stand, shadows the guards in combat and blesses (enchants + fully repairs) their gear
 * on kill-count milestones. Guild marker: item frame with a Bottle o' Enchanting; the hall needs
 * a chest, an enchanting table and a brewing stand to function.
 */
@SuppressWarnings("null")
public class EntityCleric extends AbstractVillager {
    /** Hostile-mob drops convertible to essence (1 item = 1 essence). */
    public static final Set<Item> ESSENCE_ITEMS = new HashSet<Item>(Arrays.asList(
            Items.ROTTEN_FLESH, Items.BONE, Items.STRING, Items.GUNPOWDER,
            Items.SPIDER_EYE, Items.SLIME_BALL));
    public static final int ESSENCE_CAP = 64;
    public static final int HEAL_COST = 2;
    public static final int CLEANSE_COST = 3;
    /** Cumulative kill milestones -> (blessed items, enchant power); tier resets after the last. */
    public static final int[] MILESTONE_KILLS = { 15, 50, 100 };
    public static final int[] MILESTONE_ITEMS = { 1, 2, 3 };
    public static final int[] MILESTONE_POWER = { 5, 15, 30 };

    public int essence;
    public int killCounter;
    /** Next milestone index (0..2), see MILESTONE_*. */
    public int enchantTier;

    public EntityCleric(World world) {
        super(world);
        this.init();
    }

    public EntityCleric(AbstractVillager villager) {
        super(villager);
        this.init();
    }

    private void init() {
        this.profession = 10;
        this.profName = "Cleric";
        this.currentActivity = EnumActivity.IDLE;
        this.searchRadius = 10;
        this.getNewGuildHall();
        this.addThisAI();
    }

    private void addThisAI() {
        // Reduced 4-block panic radius (other professions use 8): the cleric must stay near
        // fights to support the guards, fleeing only from point-blank danger.
        this.tasks.addTask(1, new EntityAIAvoidEntity<EntityMob>(this, EntityMob.class, 4.0f, 0.5, 0.6));
        this.tasks.addTask(2, new EntityAIClericSupport(this));
        this.tasks.addTask(3, new EntityAIClericRestock(this));
        this.tasks.addTask(5, new EntityAIRestrictOpenDoor(this));
    }

    /** Number of essence-convertible items currently in the main inventory. */
    public int countEssenceItems() {
        int count = 0;
        for (int i = 0; i < this.inventory.getSizeInventory(); ++i) {
            ItemStack stack = this.inventory.getStackInSlot(i);
            if (!stack.isEmpty() && ESSENCE_ITEMS.contains(stack.getItem())) {
                count += stack.getCount();
            }
        }
        return count;
    }

    @Override
    public ArrayList<BlockPos> getValidCoords() {
        return null;
    }

    @Override
    public boolean isValidTool(ItemStack item) {
        return false;
    }

    @Override
    protected boolean canCraft() {
        return false;
    }

    @Override
    public void writeEntityToNBT(NBTTagCompound compound) {
        super.writeEntityToNBT(compound);
        compound.setInteger("Essence", this.essence);
        compound.setInteger("KillCounter", this.killCounter);
        compound.setInteger("EnchantTier", this.enchantTier);
    }

    @Override
    public void readEntityFromNBT(NBTTagCompound compound) {
        super.readEntityFromNBT(compound);
        this.essence = compound.getInteger("Essence");
        this.killCounter = compound.getInteger("KillCounter");
        this.enchantTier = compound.getInteger("EnchantTier");
    }
}
