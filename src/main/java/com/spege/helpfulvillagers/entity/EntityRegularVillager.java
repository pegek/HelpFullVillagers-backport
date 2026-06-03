package com.spege.helpfulvillagers.entity;

import java.util.ArrayList;

import net.minecraft.entity.ai.EntityAIAvoidEntity;
import net.minecraft.entity.ai.EntityAIRestrictOpenDoor;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.monster.EntityZombie;
import net.minecraft.item.ItemStack;
import net.minecraft.pathfinding.PathNavigateGround;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** A plain (profession 0) villager: no job, drops any held tool, flees zombies. */
@SuppressWarnings("null")
public class EntityRegularVillager extends AbstractVillager {
    private boolean dropFlag;

    public EntityRegularVillager(World world) {
        super(world);
        this.init();
    }

    public EntityRegularVillager(AbstractVillager villager) {
        super(villager);
        this.init();
    }

    private void init() {
        this.profession = 0;
        this.profName = "Villager";
        this.homeGuildHall = null;
        this.dropFlag = false;
        if (!this.world.isRemote && this.homeVillage != null) {
            BlockPos center = this.homeVillage.getActualCenter();
            this.setHomePosAndDistance(center, (int) ((float) this.homeVillage.getVillageRadius() / 0.6f));
        }
    }

    @SuppressWarnings("unused")
    private void addThisAI() {
        if (this.getNavigator() instanceof PathNavigateGround) {
            ((PathNavigateGround) this.getNavigator()).setBreakDoors(false);
        }
        this.tasks.addTask(1, new EntityAIAvoidEntity<EntityZombie>(this, EntityZombie.class, 8.0f, 0.3, 0.35));
        this.tasks.addTask(3, new EntityAIRestrictOpenDoor(this));
    }

    @Override
    public boolean shouldReturn() {
        return true;
    }

    @Override
    public void onUpdate() {
        super.onUpdate();
        if (!this.dropFlag) {
            ItemStack current = this.getCurrentItem();
            if (!current.isEmpty()) {
                if (!this.inventory.isFull()) {
                    this.inventory.addItem(this.inventory.getCurrentItem());
                } else {
                    EntityItem worldItem = new EntityItem(this.world, this.posX, this.posY, this.posZ, current);
                    this.world.spawnEntity(worldItem);
                }
                this.inventory.setCurrentItem(ItemStack.EMPTY);
            }
            this.dropFlag = true;
        }
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
}
