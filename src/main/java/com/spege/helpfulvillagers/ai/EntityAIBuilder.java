package com.spege.helpfulvillagers.ai;

import java.util.Iterator;

import com.spege.helpfulvillagers.entity.EntityBuilder;
import com.spege.helpfulvillagers.enums.EnumConstructionType;
import com.spege.helpfulvillagers.util.AIHelper;
import com.spege.helpfulvillagers.util.ConstructionSite;

import net.minecraft.init.Blocks;
import net.minecraft.util.math.BlockPos;

public class EntityAIBuilder extends EntityAIWorker {
    private EntityBuilder builder;

    public EntityAIBuilder(EntityBuilder builder) {
        super(builder);
        this.builder = builder;
        this.currentTime = 0;
        this.previousTime = 0;
        this.harvestTime = 0.0f;
    }

    @Override
    protected boolean gather() {
        if (this.builder.insideHall()) {
            BlockPos exit = this.builder.homeGuildHall.entranceCoords;
            if (exit == null) {
                exit = AIHelper.getRandInsideCoords(this.builder);
            }
            this.builder.moveTo(exit, this.speed);
        } else if (this.builder.currentSite == null) {
            this.findSite();
        } else {
            this.target = this.builder.currentSite.getCenter();
            if (!this.builder.searchBox.intersects(this.builder.currentSite.getBounds())) {
                this.moveToSite();
            } else {
                this.builder.getNavigator().clearPath();
                this.work();
            }
        }
        return this.idle();
    }

    private void findSite() {
        Iterator<ConstructionSite> i = this.builder.homeVillage.constructionSites.iterator();
        while (i.hasNext()) {
            ConstructionSite site = i.next();
            if (!site.isFinished()) {
                this.builder.currentSite = site;
                break;
            }
            i.remove();
        }
    }

    private void moveToSite() {
        this.builder.moveTo(this.target, this.speed);
    }

    private void work() {
        boolean shouldSwing = false;
        if (this.builder.getNavigator().noPath()) {
            this.builder.getLookHelper().setLookPosition((double) this.target.getX(), (double) this.target.getY(), (double) this.target.getZ(), 10.0f, 10.0f);
            shouldSwing = true;
            if (this.previousTime <= 0) {
                this.previousTime = this.builder.ticksExisted;
                this.harvestTime = this.getHarvestTime();
            }
        } else {
            shouldSwing = false;
        }
        if (this.previousTime > 0) {
            this.currentTime = this.builder.ticksExisted;
            if (!this.builder.currentSite.isFinished()) {
                if ((float) (this.currentTime - this.previousTime) >= this.harvestTime) {
                    this.previousTime = this.currentTime;
                    this.harvestTime = this.getHarvestTime();
                    this.builder.currentSite.doJob(this.builder);
                }
            } else {
                this.builder.currentSite = null;
                this.target = null;
                this.previousTime = 0;
                this.currentTime = 0;
            }
        }
        if (shouldSwing) {
            this.builder.swingArm(net.minecraft.util.EnumHand.MAIN_HAND);
        } else {
            this.previousTime = 0;
        }
    }

    private float getHarvestTime() {
        if (this.builder.getHeldItemMainhand().isEmpty()) {
            return 45.0f;
        }
        if (this.builder.currentSite.getJobType() == EnumConstructionType.RECORD) {
            return 5.0f;
        }
        // approximate dig speed calculation
        return 60.0f / this.builder.getHeldItemMainhand().getItem().getDestroySpeed(this.builder.getHeldItemMainhand(), Blocks.DIRT.getDefaultState());
    }
}
