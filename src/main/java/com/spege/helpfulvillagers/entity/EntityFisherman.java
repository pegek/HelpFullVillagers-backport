package com.spege.helpfulvillagers.entity;

import java.util.ArrayList;

import com.spege.helpfulvillagers.ai.EntityAIFisherman;
import com.spege.helpfulvillagers.enums.EnumActivity;
import com.spege.helpfulvillagers.main.HelpfulVillagers;

import net.minecraft.block.Block;
import net.minecraft.entity.ai.EntityAIAvoidEntity;
import net.minecraft.entity.ai.EntityAIRestrictOpenDoor;
import net.minecraft.entity.monster.EntityMob;
import net.minecraft.entity.passive.EntitySquid;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemFishingRod;
import net.minecraft.item.ItemStack;
import net.minecraft.util.DamageSource;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * Fisherman villager: casts a custom fish hook over water, harvests fish and squid, cooks fish.
 *
 * <p>1.12.2 note: the 1.7.10 client-only {@code getItemIcon} override (which swapped the rod texture
 * to the "cast" variant while fishing) has no equivalent — held-item rendering is handled by the
 * entity renderer now, so it is dropped here.
 */
@SuppressWarnings({ "null", "deprecation" })
public class EntityFisherman extends AbstractVillager {
    public EntityFishHookCustom fishEntity;
    public ArrayList<EntitySquid> harvestedSquids = new ArrayList<EntitySquid>();
    private final ItemStack[] fishermanTools = new ItemStack[] { new ItemStack(Items.FISHING_ROD) };
    public static final ItemStack[] fishermanSmeltables = new ItemStack[] { new ItemStack(Items.COOKED_FISH) };

    public EntityFisherman(World world) {
        super(world);
        this.init();
    }

    public EntityFisherman(AbstractVillager villager) {
        super(villager);
        this.init();
    }

    private void init() {
        this.profession = 7;
        this.profName = "Fisherman";
        this.currentActivity = EnumActivity.IDLE;
        this.searchRadius = 10;
        this.knownRecipes.addAll(HelpfulVillagers.fishermanRecipes);
        this.fishEntity = null;
        this.setTools(this.fishermanTools);
        this.getNewGuildHall();
        this.addThisAI();
    }

    private void addThisAI() {
        // No setBreakDoors(false): see EntitySoldier.addThisAI — it would wall off closed doors.
        this.tasks.addTask(1, new EntityAIAvoidEntity<EntityMob>(this, EntityMob.class, 8.0f, 0.3, 0.35));
        this.tasks.addTask(2, new EntityAIFisherman(this));
        this.tasks.addTask(3, new EntityAIRestrictOpenDoor(this));
    }

    @Override
    public void onUpdate() {
        super.onUpdate();
        if (!this.world.isRemote && this.fishEntity != null && this.currentActivity != EnumActivity.GATHER) {
            this.fishEntity.setDead();
            this.fishEntity = null;
        }
    }

    @Override
    public boolean isValidTool(ItemStack item) {
        return item.getItem() instanceof ItemFishingRod;
    }

    @Override
    protected boolean canCraft() {
        return true;
    }

    @Override
    public ArrayList<BlockPos> getValidCoords() {
        this.updateBoxes();
        ArrayList<BlockPos> coords = new ArrayList<BlockPos>();
        AxisAlignedBB box = this.searchBox;
        for (int x = (int) box.minX; x <= box.maxX; ++x) {
            for (int y = (int) box.minY; y <= box.maxY; ++y) {
                for (int z = (int) box.minZ; z <= box.maxZ; ++z) {
                    Block block = this.world.getBlockState(new BlockPos(x, y, z)).getBlock();
                    if (block == Blocks.WATER) {
                        coords.add(new BlockPos(x, y, z));
                    }
                }
            }
        }
        return coords;
    }

    @Override
    protected void dayCheck() {
        super.dayCheck();
        this.harvestedSquids.clear();
    }

    @Override
    public void onDeath(DamageSource src) {
        super.onDeath(src);
        if (this.fishEntity != null) {
            this.fishEntity.setDead();
        }
    }
}
