package com.spege.helpfulvillagers.ai;

import com.spege.helpfulvillagers.entity.EntityCleric;
import com.spege.helpfulvillagers.enums.EnumActivity;
import com.spege.helpfulvillagers.main.HelpfulVillagers;
import com.spege.helpfulvillagers.util.AIHelper;

import net.minecraft.entity.ai.EntityAIBase;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;

/**
 * Cleric restock: converts collected hostile-mob drops into essence at the guild's brewing stand
 * (1 item = 1 essence, capped) and deposits the surplus into the guild chest. Mirrors the
 * navigation shape of {@link EntityAIGuardResupply}: far from the hall it sets
 * {@link EnumActivity#RETURN} (handled by {@link EntityAIMoveIndoorsCustom}) and re-triggers on
 * arrival; work runs on a 20-tick throttle.
 */
@SuppressWarnings("null")
public class EntityAIClericRestock extends EntityAIBase {
    /** Start restocking below this much essence (provided there is anything to convert). */
    private static final int LOW_ESSENCE = 10;
    private static final int WORK_INTERVAL = 20;

    private final EntityCleric cleric;
    private final float speed;
    private int workCooldown;

    public EntityAIClericRestock(EntityCleric cleric) {
        this.cleric = cleric;
        this.speed = 0.6f;
        this.setMutexBits(3);
    }

    @Override
    public boolean shouldExecute() {
        if (this.cleric.world.isRemote || this.cleric.isChild()) {
            return false;
        }
        EnumActivity activity = this.cleric.currentActivity;
        if (activity != EnumActivity.IDLE && activity != EnumActivity.STORE) {
            return false;
        }
        if (this.cleric.homeGuildHall == null) {
            return false;
        }
        boolean needsEssence = this.cleric.essence < LOW_ESSENCE && this.cleric.countEssenceItems() > 0;
        return needsEssence || this.cleric.inventory.isFull();
    }

    @Override
    public void startExecuting() {
        this.cleric.currentActivity = EnumActivity.STORE;
        this.workCooldown = 0;
        HelpfulVillagers.logger.info("[HV] ClericRestock: id={} starts (essence={} convertibles={} full={})",
                this.cleric.getEntityId(), this.cleric.essence, this.cleric.countEssenceItems(),
                this.cleric.inventory.isFull());
    }

    @Override
    public boolean shouldContinueExecuting() {
        if (this.cleric.currentActivity != EnumActivity.STORE) {
            // RETURN = handed off to EntityAIMoveIndoorsCustom; IDLE = done/preempted.
            return false;
        }
        if (this.cleric.homeGuildHall == null) {
            return false;
        }
        boolean canConvert = this.cleric.countEssenceItems() > 0 && this.cleric.essence < EntityCleric.ESSENCE_CAP;
        return canConvert || !this.cleric.inventory.isEmpty();
    }

    @Override
    public void resetTask() {
        if (this.cleric.currentActivity == EnumActivity.STORE) {
            this.cleric.currentActivity = EnumActivity.IDLE;
        }
    }

    @Override
    public void updateTask() {
        if (--this.workCooldown > 0) {
            return;
        }
        this.workCooldown = WORK_INTERVAL;
        if (!this.cleric.nearHall()) {
            this.cleric.currentActivity = EnumActivity.RETURN;
            return;
        }
        this.cleric.homeGuildHall.checkClericFacilities();
        BlockPos stand = this.cleric.homeGuildHall.brewingStandPos;
        if (stand == null) {
            HelpfulVillagers.logger.info("[HV] ClericRestock: id={} hall has no brewing stand, idling",
                    this.cleric.getEntityId());
            this.cleric.currentActivity = EnumActivity.IDLE;
            return;
        }
        if (this.cleric.countEssenceItems() > 0 && this.cleric.essence < EntityCleric.ESSENCE_CAP) {
            if (AIHelper.findDistance((int) this.cleric.posX, stand.getX()) > 2
                    || AIHelper.findDistance((int) this.cleric.posY, stand.getY()) > 2
                    || AIHelper.findDistance((int) this.cleric.posZ, stand.getZ()) > 2) {
                this.cleric.moveTo(stand, this.speed);
                return;
            }
            this.convertDrops(stand);
            return;
        }
        this.depositSurplus();
    }

    /** Turns whitelisted drops in the main inventory into essence, with brew sound + particles. */
    private void convertDrops(BlockPos stand) {
        int converted = 0;
        for (int i = 0; i < this.cleric.inventory.getSizeInventory()
                && this.cleric.essence < EntityCleric.ESSENCE_CAP; ++i) {
            ItemStack stack = this.cleric.inventory.getStackInSlot(i);
            if (stack.isEmpty() || !EntityCleric.ESSENCE_ITEMS.contains(stack.getItem())) {
                continue;
            }
            int take = Math.min(stack.getCount(), EntityCleric.ESSENCE_CAP - this.cleric.essence);
            this.cleric.essence += take;
            converted += take;
            stack.shrink(take);
            if (stack.getCount() <= 0) {
                this.cleric.inventory.setMainContents(i, ItemStack.EMPTY);
            }
        }
        if (converted > 0) {
            this.cleric.world.playSound(null, stand.getX() + 0.5, stand.getY() + 0.5, stand.getZ() + 0.5,
                    SoundEvents.BLOCK_BREWING_STAND_BREW, SoundCategory.BLOCKS, 0.8f, 1.0f);
            ((WorldServer) this.cleric.world).spawnParticle(EnumParticleTypes.SPELL_WITCH,
                    stand.getX() + 0.5, stand.getY() + 1.0, stand.getZ() + 0.5, 10, 0.3, 0.4, 0.3, 0.02);
            this.cleric.inventory.syncInventory();
            HelpfulVillagers.logger.info("[HV] ClericRestock: id={} converted {} drops -> essence={}",
                    this.cleric.getEntityId(), converted, this.cleric.essence);
        }
    }

    /** Drops everything left (non-convertible loot, overflow) into the guild chest. */
    private void depositSurplus() {
        if (this.cleric.inventory.isEmpty()) {
            this.cleric.currentActivity = EnumActivity.IDLE;
            return;
        }
        TileEntityChest chest = this.cleric.homeGuildHall.getAvailableChest();
        if (chest == null) {
            this.cleric.changeGuildHall = true;
            return;
        }
        if (AIHelper.findDistance((int) this.cleric.posX, chest.getPos().getX()) > 2
                || AIHelper.findDistance((int) this.cleric.posY, chest.getPos().getY()) > 2
                || AIHelper.findDistance((int) this.cleric.posZ, chest.getPos().getZ()) > 2) {
            this.cleric.moveTo(chest.getPos(), this.speed);
            return;
        }
        try {
            this.cleric.inventory.dumpInventory(chest);
        } catch (NullPointerException e) {
            // 1.7.10 closed the chest here (no-arg closeInventory); no 1.12.2 analogue.
        }
        HelpfulVillagers.logger.info("[HV] ClericRestock: id={} deposited surplus into guild chest",
                this.cleric.getEntityId());
    }
}
