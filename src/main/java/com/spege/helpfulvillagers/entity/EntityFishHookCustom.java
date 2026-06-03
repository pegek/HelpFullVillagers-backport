package com.spege.helpfulvillagers.entity;

import java.util.List;

import com.spege.helpfulvillagers.main.HelpfulVillagers;

import io.netty.buffer.ByteBuf;
import net.minecraft.block.material.Material;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.MoverType;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.ItemFishingRod;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumHand;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.storage.loot.LootContext;
import net.minecraft.world.storage.loot.LootTableList;
import net.minecraftforge.fml.common.registry.IEntityAdditionalSpawnData;

/**
 * Custom fishing hook cast by a {@link EntityFisherman}.
 *
 * <p>This is intentionally smaller than vanilla's player fishing hook. The fisherman AI already
 * chooses a target water block, so the hook only animates toward that block, waits for a bite, adds
 * vanilla fishing loot to the villager inventory, damages the rod, then despawns.
 */
@SuppressWarnings("null")
public class EntityFishHookCustom extends Entity implements IEntityAdditionalSpawnData {
    private static final int CAST_TICKS = 12;
    private static final int MIN_WAIT_TICKS = 100;
    private static final int MAX_WAIT_TICKS = 420;
    private static final int BITE_TICKS = 18;
    private static final double MAX_OWNER_DISTANCE_SQ = 1024.0;

    public EntityFisherman owner;
    public int x;
    public int y;
    public int z;

    private int ownerEntityId;
    private int lifeTicks;
    private int waitTicks;
    private int biteTicks;
    private double startX;
    private double startY;
    private double startZ;
    private double targetX;
    private double targetY;
    private double targetZ;

    public EntityFishHookCustom(World world) {
        super(world);
        this.setSize(0.25f, 0.25f);
        this.ignoreFrustumCheck = true;
        this.noClip = true;
    }

    public EntityFishHookCustom(World world, double x, double y, double z, EntityFisherman fisherman) {
        this(world);
        this.owner = fisherman;
        this.ownerEntityId = fisherman != null ? fisherman.getEntityId() : 0;
        this.x = (int) x;
        this.y = (int) y;
        this.z = (int) z;
        this.targetX = this.x + 0.5;
        this.targetY = this.y + 0.15;
        this.targetZ = this.z + 0.5;
        this.setCastStartFromOwner();
        this.setLocationAndAngles(this.startX, this.startY, this.startZ, 0.0f, 0.0f);
        this.waitTicks = this.calculateWaitTicks();
    }

    public EntityFishHookCustom(World world, EntityFisherman fisherman) {
        this(world);
        this.owner = fisherman;
        this.ownerEntityId = fisherman != null ? fisherman.getEntityId() : 0;
        this.setCastStartFromOwner();
        this.setLocationAndAngles(this.startX, this.startY, this.startZ, 0.0f, 0.0f);
        this.targetX = this.posX;
        this.targetY = this.posY;
        this.targetZ = this.posZ;
        this.waitTicks = this.calculateWaitTicks();
    }

    @Override
    protected void entityInit() {
    }

    @Override
    public void onUpdate() {
        super.onUpdate();
        this.resolveOwner();
        if (this.world.isRemote) {
            this.updateClientMotion();
            return;
        }
        if (!this.isOwnerValid()) {
            this.setDead();
            return;
        }
        if (!this.isTargetWater()) {
            this.setDead();
            return;
        }
        ++this.lifeTicks;
        if (this.lifeTicks <= CAST_TICKS) {
            this.updateCastingPosition();
            return;
        }
        this.setLocationAndAngles(this.targetX, this.getBobberY(), this.targetZ, this.rotationYaw, this.rotationPitch);
        if (this.biteTicks > 0) {
            --this.biteTicks;
            if (this.biteTicks <= 0) {
                this.catchFish();
            }
            return;
        }
        --this.waitTicks;
        this.spawnWaitingParticles();
        if (this.waitTicks <= 0) {
            this.startBite();
        }
    }

    @Override
    public void setDead() {
        super.setDead();
        if (this.owner != null && this.owner.fishEntity == this) {
            this.owner.fishEntity = null;
        }
    }

    @Override
    protected void readEntityFromNBT(NBTTagCompound compound) {
        this.x = compound.getInteger("TargetX");
        this.y = compound.getInteger("TargetY");
        this.z = compound.getInteger("TargetZ");
        this.ownerEntityId = compound.getInteger("OwnerEntityId");
        this.lifeTicks = compound.getInteger("LifeTicks");
        this.waitTicks = compound.getInteger("WaitTicks");
        this.biteTicks = compound.getInteger("BiteTicks");
        this.targetX = compound.getDouble("TargetPosX");
        this.targetY = compound.getDouble("TargetPosY");
        this.targetZ = compound.getDouble("TargetPosZ");
        this.startX = compound.getDouble("StartX");
        this.startY = compound.getDouble("StartY");
        this.startZ = compound.getDouble("StartZ");
    }

    @Override
    protected void writeEntityToNBT(NBTTagCompound compound) {
        compound.setInteger("TargetX", this.x);
        compound.setInteger("TargetY", this.y);
        compound.setInteger("TargetZ", this.z);
        compound.setInteger("OwnerEntityId", this.ownerEntityId);
        compound.setInteger("LifeTicks", this.lifeTicks);
        compound.setInteger("WaitTicks", this.waitTicks);
        compound.setInteger("BiteTicks", this.biteTicks);
        compound.setDouble("TargetPosX", this.targetX);
        compound.setDouble("TargetPosY", this.targetY);
        compound.setDouble("TargetPosZ", this.targetZ);
        compound.setDouble("StartX", this.startX);
        compound.setDouble("StartY", this.startY);
        compound.setDouble("StartZ", this.startZ);
    }

    @Override
    public void writeSpawnData(ByteBuf buffer) {
        buffer.writeInt(this.ownerEntityId);
        buffer.writeInt(this.x);
        buffer.writeInt(this.y);
        buffer.writeInt(this.z);
        buffer.writeDouble(this.targetX);
        buffer.writeDouble(this.targetY);
        buffer.writeDouble(this.targetZ);
        buffer.writeDouble(this.startX);
        buffer.writeDouble(this.startY);
        buffer.writeDouble(this.startZ);
        buffer.writeInt(this.lifeTicks);
        buffer.writeInt(this.waitTicks);
        buffer.writeInt(this.biteTicks);
    }

    @Override
    public void readSpawnData(ByteBuf buffer) {
        this.ownerEntityId = buffer.readInt();
        this.x = buffer.readInt();
        this.y = buffer.readInt();
        this.z = buffer.readInt();
        this.targetX = buffer.readDouble();
        this.targetY = buffer.readDouble();
        this.targetZ = buffer.readDouble();
        this.startX = buffer.readDouble();
        this.startY = buffer.readDouble();
        this.startZ = buffer.readDouble();
        this.lifeTicks = buffer.readInt();
        this.waitTicks = buffer.readInt();
        this.biteTicks = buffer.readInt();
        this.resolveOwner();
    }

    public EntityFisherman getOwner() {
        this.resolveOwner();
        return this.owner;
    }

    private void setCastStartFromOwner() {
        if (this.owner == null) {
            this.startX = this.posX;
            this.startY = this.posY;
            this.startZ = this.posZ;
            return;
        }
        this.startX = this.owner.posX;
        this.startY = this.owner.posY + (double) this.owner.getEyeHeight() * 0.75;
        this.startZ = this.owner.posZ;
    }

    private int calculateWaitTicks() {
        int wait = MIN_WAIT_TICKS + this.rand.nextInt(MAX_WAIT_TICKS - MIN_WAIT_TICKS + 1);
        ItemStack rod = this.owner != null ? this.owner.getCurrentItem() : ItemStack.EMPTY;
        if (!rod.isEmpty()) {
            wait -= EnchantmentHelper.getFishingSpeedBonus(rod) * 80;
        }
        return Math.max(40, wait);
    }

    private boolean isOwnerValid() {
        if (this.owner == null || this.owner.isDead || !this.owner.isEntityAlive()) {
            return false;
        }
        ItemStack rod = this.owner.getCurrentItem();
        return !rod.isEmpty()
                && rod.getItem() instanceof ItemFishingRod
                && this.getDistanceSq(this.owner) <= MAX_OWNER_DISTANCE_SQ;
    }

    private boolean isTargetWater() {
        return this.world.getBlockState(new BlockPos(this.x, this.y, this.z)).getMaterial() == Material.WATER;
    }

    private void resolveOwner() {
        if (this.owner != null || this.ownerEntityId <= 0 || this.world == null) {
            return;
        }
        Entity entity = this.world.getEntityByID(this.ownerEntityId);
        if (entity instanceof EntityFisherman) {
            this.owner = (EntityFisherman) entity;
            this.owner.fishEntity = this;
        }
    }

    private void updateCastingPosition() {
        float progress = (float) this.lifeTicks / (float) CAST_TICKS;
        double nextX = this.startX + (this.targetX - this.startX) * progress;
        double nextY = this.startY + (this.targetY - this.startY) * progress;
        double nextZ = this.startZ + (this.targetZ - this.startZ) * progress;
        this.motionX = nextX - this.posX;
        this.motionY = nextY - this.posY;
        this.motionZ = nextZ - this.posZ;
        this.move(MoverType.SELF, this.motionX, this.motionY, this.motionZ);
    }

    private void updateClientMotion() {
        if (this.lifeTicks < CAST_TICKS) {
            ++this.lifeTicks;
        }
        this.move(MoverType.SELF, this.motionX, this.motionY, this.motionZ);
    }

    private double getBobberY() {
        return this.targetY + MathHelper.sin((float) (this.ticksExisted + this.getEntityId()) * 0.16f) * 0.04f;
    }

    private void startBite() {
        this.biteTicks = BITE_TICKS;
        this.motionY = -0.18;
        this.world.playSound(null, this.posX, this.posY, this.posZ, SoundEvents.ENTITY_BOBBER_SPLASH,
                SoundCategory.NEUTRAL, 0.35f, 0.8f + this.rand.nextFloat() * 0.4f);
        this.spawnSplashParticles(10);
    }

    private void catchFish() {
        if (this.owner == null || this.world.isRemote) {
            this.setDead();
            return;
        }
        this.owner.swingArm(EnumHand.MAIN_HAND);
        for (ItemStack stack : this.generateFishingLoot()) {
            if (!stack.isEmpty()) {
                this.owner.inventory.addItem(stack);
            }
        }
        this.owner.damageItem();
        this.owner.inventory.syncInventory();
        HelpfulVillagers.logger.info("[HV] FishHook: {} caught fish at {},{},{}",
                this.owner.getName(), this.x, this.y, this.z);
        this.setDead();
    }

    private List<ItemStack> generateFishingLoot() {
        float luck = 0.0f;
        if (this.owner != null) {
            ItemStack rod = this.owner.getCurrentItem();
            if (!rod.isEmpty()) {
                luck += EnchantmentHelper.getFishingLuckBonus(rod);
            }
        }
        LootContext.Builder builder = new LootContext.Builder((WorldServer) this.world).withLuck(luck);
        return this.world.getLootTableManager()
                .getLootTableFromLocation(LootTableList.GAMEPLAY_FISHING)
                .generateLootForPools(this.rand, builder.build());
    }

    private void spawnWaitingParticles() {
        if (this.waitTicks % 20 != 0 || !(this.world instanceof WorldServer)) {
            return;
        }
        if (this.rand.nextFloat() >= 0.35f) {
            return;
        }
        double angle = this.rand.nextDouble() * Math.PI * 2.0;
        double radius = 0.4 + this.rand.nextDouble() * 1.8;
        ((WorldServer) this.world).spawnParticle(EnumParticleTypes.WATER_WAKE,
                this.targetX + Math.sin(angle) * radius, this.y + 1.0, this.targetZ + Math.cos(angle) * radius,
                1, 0.0, 0.0, 0.0, 0.0);
    }

    private void spawnSplashParticles(int count) {
        if (!(this.world instanceof WorldServer)) {
            return;
        }
        ((WorldServer) this.world).spawnParticle(EnumParticleTypes.WATER_SPLASH,
                this.posX, this.y + 1.0, this.posZ, count, 0.25, 0.0, 0.25, 0.1);
        ((WorldServer) this.world).spawnParticle(EnumParticleTypes.WATER_BUBBLE,
                this.posX, this.y + 0.8, this.posZ, count, 0.25, 0.0, 0.25, 0.05);
    }
}
