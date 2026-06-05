package com.spege.helpfulvillagers.entity;

import java.util.ArrayList;

import com.spege.helpfulvillagers.ai.EntityAIRancher;
import com.spege.helpfulvillagers.enums.EnumActivity;
import com.spege.helpfulvillagers.main.HelpfulVillagers;
import com.spege.helpfulvillagers.util.AIHelper;
import com.spege.helpfulvillagers.village.RanchGuildHall;

import net.minecraft.entity.ai.EntityAIAvoidEntity;
import net.minecraft.entity.ai.EntityAIRestrictOpenDoor;
import net.minecraft.entity.monster.EntityMob;
import net.minecraft.entity.passive.EntityAnimal;
import net.minecraft.init.Items;
import net.minecraft.item.ItemLead;
import net.minecraft.item.ItemStack;
import net.minecraft.pathfinding.PathNavigateGround;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** Rancher villager: leads animals into pasture, breeds/cooks meat, crafts leather armour. */
@SuppressWarnings({ "null", "deprecation" })
public class EntityRancher extends AbstractVillager {
    public boolean searching = false;
    private final ItemStack[] rancherTools = new ItemStack[] { new ItemStack(Items.LEAD) };

    public static final ItemStack[] rancherSmeltables = new ItemStack[] {
            new ItemStack(Items.COOKED_BEEF), new ItemStack(Items.COOKED_CHICKEN), new ItemStack(Items.COOKED_PORKCHOP) };

    public static final ItemStack[] rancherCraftables = new ItemStack[] {
            new ItemStack(Items.LEATHER_BOOTS), new ItemStack(Items.LEATHER_CHESTPLATE), new ItemStack(Items.LEATHER_HELMET),
            new ItemStack(Items.LEATHER_LEGGINGS), new ItemStack(Items.BOOK) };

    public EntityRancher(World world) {
        super(world);
        this.init();
    }

    public EntityRancher(AbstractVillager villager) {
        super(villager);
        this.init();
    }

    private void init() {
        this.profession = 8;
        this.profName = "Rancher";
        this.currentActivity = EnumActivity.IDLE;
        this.searchRadius = 10;
        this.knownRecipes.addAll(HelpfulVillagers.rancherRecipes);
        this.setTools(this.rancherTools);
        this.getNewGuildHall();
        this.addThisAI();
    }

    private void addThisAI() {
        if (this.getNavigator() instanceof PathNavigateGround) {
            ((PathNavigateGround) this.getNavigator()).setBreakDoors(false);
        }
        this.tasks.addTask(1, new EntityAIAvoidEntity<EntityMob>(this, EntityMob.class, 8.0f, 0.3, 0.35));
        this.tasks.addTask(2, new EntityAIRancher(this));
        this.tasks.addTask(3, new EntityAIRestrictOpenDoor(this));
    }

    public RanchGuildHall getRanch() {
        if (this.homeGuildHall != null) {
            return (RanchGuildHall) this.homeGuildHall;
        }
        return null;
    }

    public boolean nearPasture() {
        if (this.getRanch() == null) {
            return false;
        }
        BlockPos currentPosition = new BlockPos((int) this.posX, (int) this.posY, (int) this.posZ);
        if (this.getRanch().pastureCoords.contains(currentPosition)) {
            return true;
        }
        ArrayList<BlockPos> adjacent = AIHelper.getAdjacentCoords(currentPosition);
        for (BlockPos i : adjacent) {
            if (!this.getRanch().pastureCoords.contains(i)) {
                continue;
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean isValidTool(ItemStack item) {
        return item.getItem() instanceof ItemLead;
    }

    @Override
    protected void dayCheck() {
        super.dayCheck();
        this.searching = false;
        if (this.getRanch() != null) {
            this.getRanch().checkedAnimals.clear();
        }
    }

    @Override
    protected boolean canCraft() {
        return true;
    }

    @Override
    public ArrayList<BlockPos> getValidCoords() {
        return null;
    }

    /**
     * Rancher resources are nearby animals rather than block coordinates. Kept type-safe and separate
     * from {@link #getValidCoords()} (which returns null) so the rancher AI can consume animals directly.
     */
    public ArrayList<EntityAnimal> getValidAnimals() {
        this.updateBoxes();
        ArrayList<EntityAnimal> animals = new ArrayList<EntityAnimal>();
        animals.addAll(this.world.getEntitiesWithinAABB(EntityAnimal.class, this.searchBox));
        return animals;
    }
}
