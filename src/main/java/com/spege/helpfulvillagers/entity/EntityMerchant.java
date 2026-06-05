package com.spege.helpfulvillagers.entity;

import java.util.ArrayList;

import com.spege.helpfulvillagers.enums.EnumActivity;

import net.minecraft.entity.ai.EntityAIAvoidEntity;
import net.minecraft.entity.ai.EntityAIRestrictOpenDoor;
import net.minecraft.entity.monster.EntityMob;
import net.minecraft.item.ItemStack;
import net.minecraft.pathfinding.PathNavigateGround;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** Merchant villager: runs the village economy/barter; has no job tool and crafts nothing. */
@SuppressWarnings("null")
public class EntityMerchant extends AbstractVillager {
    public EntityMerchant(World world) {
        super(world);
        this.init();
    }

    public EntityMerchant(AbstractVillager villager) {
        super(villager);
        this.init();
    }

    private void init() {
        this.profession = 6;
        this.profName = "Merchant";
        this.currentActivity = EnumActivity.IDLE;
        this.searchRadius = 10;
        this.getNewGuildHall();
        this.addThisAI();
    }

    private void addThisAI() {
        if (this.getNavigator() instanceof PathNavigateGround) {
            ((PathNavigateGround) this.getNavigator()).setBreakDoors(false);
        }
        this.tasks.addTask(1, new EntityAIAvoidEntity<EntityMob>(this, EntityMob.class, 8.0f, 0.3, 0.35));
        this.tasks.addTask(3, new EntityAIRestrictOpenDoor(this));
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
