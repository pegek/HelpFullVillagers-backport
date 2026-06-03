package com.spege.helpfulvillagers.village;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

import com.spege.helpfulvillagers.crafting.CraftQueue;
import com.spege.helpfulvillagers.econ.VillageEconomy;
import com.spege.helpfulvillagers.entity.AbstractVillager;
import com.spege.helpfulvillagers.util.AIHelper;

import net.minecraft.block.Block;
import net.minecraft.block.BlockDoor;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityItemFrame;
import net.minecraft.entity.monster.IMob;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagInt;
import net.minecraft.nbt.NBTTagIntArray;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.village.Village;
import net.minecraft.village.VillageDoorInfo;
import net.minecraft.world.World;
import net.minecraftforge.common.DimensionManager;

/**
 * A "helpful" village: an axis-aligned region tracked around its doors, holding the guild halls,
 * craft queue and economy. Persisted via {@link HelpfulVillageCollection} (WorldSavedData).
 *
 * <p>1.12.2 migration: all positions use {@link BlockPos} (the 1.7.10 ChunkCoordinates type is gone),
 * block lookups go through {@link IBlockState}, and door orientation is read from
 * {@link BlockDoor#FACING}. The door-orientation logic is a best-effort translation and should be
 * verified in-game.
 */
@SuppressWarnings("null")
public class HelpfulVillage {
    private static final int LOAD_TIME_MAX = 500;
    public World world;
    private long loadTime;
    public int dimension = Integer.MAX_VALUE;
    public BlockPos initialCenter;
    public BlockPos actualCenter = null;
    public int radius = 0;
    public ArrayList<GuildHall> guildHallList = new ArrayList<GuildHall>();
    public boolean[] unlockedHalls = new boolean[13];
    private int numVillagers = 0;
    private static int totalAdded = 0;
    private long lastAddedVillager = 0L;
    public boolean isAnnihilated = false;
    public AxisAlignedBB villageBounds;
    public AxisAlignedBB actualBounds;
    public ArrayList<BlockPos> villageDoors = new ArrayList<BlockPos>();
    public int minX;
    public int minY;
    public int minZ;
    public int maxX;
    public int maxY;
    public int maxZ;
    public boolean dayCheck;
    public EntityLivingBase lastAggressor;
    public CraftQueue craftQueue = new CraftQueue();
    public VillageEconomy economy = new VillageEconomy(this, false);
    public boolean priceCalcStarted = false;
    public boolean pricesCalculated = false;

    public HelpfulVillage() {
        this.world = null;
    }

    public HelpfulVillage(World world, BlockPos center) {
        this.world = world;
        this.dimension = world.provider.getDimension();
        this.initialCenter = center;
        this.init();
    }

    public HelpfulVillage(World world, Village village) {
        this.world = world;
        this.dimension = world.provider.getDimension();
        this.initialCenter = village.getCenter();
        this.radius = village.getVillageRadius();
        this.init();
    }

    private void init() {
        if (this.world == null) {
            this.world = DimensionManager.getWorld(this.dimension);
        }
        this.loadTime = this.world.getTotalWorldTime();
        if (this.radius <= 0) {
            this.radius = 32;
        }
        this.lastAggressor = null;
        this.dayCheck = true;
        if (!this.world.isRemote) {
            this.initBounds();
        }
    }

    private static AxisAlignedBB box(double x1, double y1, double z1, double x2, double y2, double z2) {
        return new AxisAlignedBB(x1, y1, z1, x2, y2, z2);
    }

    private void initBounds() {
        BlockPos c = this.actualCenter == null ? this.initialCenter : this.actualCenter;
        this.minX = c.getX();
        this.maxX = c.getX();
        this.minY = c.getY();
        this.maxY = c.getY();
        this.minZ = c.getZ();
        this.maxZ = c.getZ();
        this.villageBounds = box(c.getX() - this.radius, c.getY() - this.radius, c.getZ() - this.radius,
                c.getX() + this.radius, c.getY() + this.radius, c.getZ() + this.radius);
        if (this.actualCenter == null) {
            this.actualCenter = this.initialCenter;
        }
        this.actualBounds = box(this.minX, this.minY, this.minZ, this.maxX, this.maxY, this.maxZ);
    }

    public void updateVillageBox() {
        if (this.world == null) {
            return;
        }
        Iterator<BlockPos> iterator = this.villageDoors.iterator();
        while (iterator.hasNext()) {
            BlockPos currDoor = iterator.next();
            Block checkBlock = this.world.getBlockState(currDoor).getBlock();
            if (checkBlock instanceof BlockDoor && checkBlock != Blocks.IRON_DOOR) {
                if (this.getDoorFromCoords(currDoor) == null) {
                    iterator.remove();
                }
                continue;
            }
            iterator.remove();
        }
        for (int x = (int) this.villageBounds.minX; x <= this.villageBounds.maxX; ++x) {
            for (int y = (int) this.villageBounds.minY; y <= this.villageBounds.maxY; ++y) {
                for (int z = (int) this.villageBounds.minZ; z <= this.villageBounds.maxZ; ++z) {
                    BlockPos currCoords = new BlockPos(x, y, z);
                    Block checkBlock = this.world.getBlockState(currCoords).getBlock();
                    if (checkBlock instanceof BlockDoor && checkBlock != Blocks.IRON_DOOR
                            && !this.villageDoors.contains(currCoords)) {
                        BlockPos aboveCoords = new BlockPos(x, y + 1, z);
                        BlockPos belowCoords = new BlockPos(x, y - 1, z);
                        if (!(this.villageDoors.contains(aboveCoords) && this.villageDoors.contains(belowCoords))
                                && this.getDoorFromCoords(currCoords) != null) {
                            this.villageDoors.add(currCoords);
                        }
                    }
                }
            }
        }
        double dist = 0;
        this.initBounds();
        for (int i = 0; i < this.villageDoors.size(); ++i) {
            BlockPos currDoor = this.villageDoors.get(i);
            if (currDoor.getX() < this.minX) {
                this.minX = currDoor.getX() - 5;
            } else if (currDoor.getX() > this.maxX) {
                this.maxX = currDoor.getX() + 5;
            }
            if (currDoor.getY() < this.minY) {
                this.minY = currDoor.getY() - 5;
            } else if (currDoor.getY() > this.maxY) {
                this.maxY = currDoor.getY() + 5;
            }
            if (currDoor.getZ() < this.minZ) {
                this.minZ = currDoor.getZ() - 5;
            } else if (currDoor.getZ() > this.maxZ) {
                this.maxZ = currDoor.getZ() + 5;
            }
            dist = Math.max(currDoor.distanceSq(this.actualCenter), dist);
        }
        this.radius = Math.max(32, (int) Math.sqrt(dist) + 5);
        this.actualBounds = box(this.minX, this.minY, this.minZ, this.maxX, this.maxY, this.maxZ);
        this.actualCenter = this.getActualCenter();
        this.villageBounds = box(this.actualCenter.getX() - this.radius, this.actualCenter.getY() - this.radius,
                this.actualCenter.getZ() - this.radius, this.actualCenter.getX() + this.radius,
                this.actualCenter.getY() + this.radius, this.actualCenter.getZ() + this.radius);
    }

    public BlockPos getInitialCenter() {
        return this.initialCenter;
    }

    public BlockPos getActualCenter() {
        int x = (this.minX + this.maxX) / 2;
        int y = (this.minY + this.maxY) / 2;
        int z = (this.minZ + this.maxZ) / 2;
        return new BlockPos(x, y, z);
    }

    public int getActualRadius() {
        int xRadius = Math.abs(this.maxX - this.minX) / 2;
        int zRadius = Math.abs(this.maxZ - this.minZ) / 2;
        return Math.max(xRadius, zRadius);
    }

    public int getPopulation() {
        return this.numVillagers;
    }

    public int getTotalVillagers() {
        return this.world.countEntities(AbstractVillager.class);
    }

    public int getTotalAdded() {
        return totalAdded;
    }

    public long getLastAdded() {
        return this.lastAddedVillager;
    }

    public boolean isFullyLoaded() {
        return this.world.getTotalWorldTime() - this.loadTime > LOAD_TIME_MAX;
    }

    public void addVillager() {
        if (totalAdded < this.world.countEntities(AbstractVillager.class) || !this.isFullyLoaded()) {
            ++this.numVillagers;
            ++totalAdded;
            this.lastAddedVillager = this.world.getTotalWorldTime();
        }
    }

    public void removeVillager() {
        --this.numVillagers;
        --totalAdded;
        if (this.numVillagers <= 0) {
            this.isAnnihilated = true;
        }
    }

    public boolean isInRange(double x, double y, double z) {
        return x < this.villageBounds.maxX && x > this.villageBounds.minX
                && y < this.villageBounds.maxY && y > this.villageBounds.minY
                && z < this.villageBounds.maxZ && z > this.villageBounds.minZ;
    }

    public boolean isInsideVillage(double x, double y, double z) {
        if (this.actualBounds == null) {
            return false;
        }
        return x < this.actualBounds.maxX && x > this.actualBounds.minX
                && y < this.actualBounds.maxY && y > this.actualBounds.minY
                && z < this.actualBounds.maxZ && z > this.actualBounds.minZ;
    }

    public VillageDoorInfo findNearestDoorUnrestricted(int x, int y, int z) {
        VillageDoorInfo targetDoor = null;
        Iterator<BlockPos> iterator = this.villageDoors.iterator();
        int dist = Integer.MAX_VALUE;
        while (iterator.hasNext()) {
            BlockPos currDoor = iterator.next();
            int currDist = (int) Math.sqrt(currDoor.distanceSq(x, y, z));
            if (currDist >= dist) {
                continue;
            }
            dist = currDist;
            targetDoor = this.getDoorFromCoords(currDoor);
        }
        return targetDoor;
    }

    /**
     * Builds a {@link VillageDoorInfo} describing the door at the given position, including the
     * "inside" direction inferred from which side has more sky exposure. Door axis comes from
     * {@link BlockDoor#FACING} on the lower half. Best-effort translation of the 1.7.10 metadata
     * logic — verify in-game.
     */
    private VillageDoorInfo getDoorFromCoords(BlockPos pos) {
        IBlockState state = this.world.getBlockState(pos);
        if (!(state.getBlock() instanceof BlockDoor)) {
            return null;
        }
        BlockPos lower = state.getValue(BlockDoor.HALF) == BlockDoor.EnumDoorHalf.UPPER ? pos.down() : pos;
        IBlockState lowerState = this.world.getBlockState(lower);
        if (!(lowerState.getBlock() instanceof BlockDoor)) {
            return null;
        }
        EnumFacing facing = lowerState.getValue(BlockDoor.FACING);
        int x = pos.getX();
        int y = pos.getY();
        int z = pos.getZ();
        if (facing.getAxis() == EnumFacing.Axis.Z) {
            int i1 = 0;
            for (int j1 = -5; j1 < 0; ++j1) {
                if (this.world.canSeeSky(new BlockPos(x, y, z + j1))) {
                    --i1;
                }
            }
            for (int j1 = 1; j1 <= 5; ++j1) {
                if (this.world.canSeeSky(new BlockPos(x, y, z + j1))) {
                    ++i1;
                }
            }
            if (i1 != 0) {
                return new VillageDoorInfo(pos, 0, i1 > 0 ? -2 : 2, 0);
            }
        } else {
            int i1 = 0;
            for (int j1 = -5; j1 < 0; ++j1) {
                if (this.world.canSeeSky(new BlockPos(x + j1, y, z))) {
                    --i1;
                }
            }
            for (int j1 = 1; j1 <= 5; ++j1) {
                if (this.world.canSeeSky(new BlockPos(x + j1, y, z))) {
                    ++i1;
                }
            }
            if (i1 != 0) {
                return new VillageDoorInfo(pos, i1 > 0 ? -2 : 2, 0, 0);
            }
        }
        return null;
    }

    public int getVillageRadius() {
        return this.radius;
    }

    public EntityLivingBase findNearestVillageAggressor(EntityLivingBase entity) {
        if (this.actualBounds == null) {
            return null;
        }
        if (this.lastAggressor != null && this.lastAggressor instanceof IMob && this.lastAggressor.isEntityAlive()) {
            return this.lastAggressor;
        }
        List<IMob> entities = this.world.getEntitiesWithinAABB(IMob.class, this.actualBounds);
        double d0 = Double.MAX_VALUE;
        EntityLivingBase target = null;
        for (IMob curr : entities) {
            double d1 = entity.getDistanceSq((Entity) curr);
            if (d1 < d0) {
                d0 = d1;
                target = (EntityLivingBase) curr;
            }
        }
        return target;
    }

    public GuildHall lookForExistingHall(int profession) {
        ArrayList<GuildHall> matchedHalls = new ArrayList<GuildHall>();
        for (GuildHall guildHall : this.guildHallList) {
            if (guildHall.getTypeNum() == profession) {
                matchedHalls.add(guildHall);
            }
        }
        if (matchedHalls.size() > 0) {
            Random gen = new Random();
            return matchedHalls.get(gen.nextInt(matchedHalls.size()));
        }
        return null;
    }

    public void checkHalls() {
        for (int i = 0; i < this.guildHallList.size(); ++i) {
            this.guildHallList.get(i).checkFrame();
        }
    }

    public void findHalls() {
        if (this.world == null) {
            return;
        }
        for (int i = 1; i <= 13; ++i) {
            GuildHall adder = i == 8 ? new RanchGuildHall(this.world, this) : new GuildHall(this.world, this);
            List<EntityItemFrame> itemFrames = this.world.getEntitiesWithinAABB(EntityItemFrame.class, this.villageBounds);
            adder.findCoords(i, itemFrames);
            if (adder.typeNum <= 0 || this.containsHall(adder) || adder.insideCoords.size() <= 0) {
                continue;
            }
            if (adder instanceof RanchGuildHall) {
                ((RanchGuildHall) adder).findPastureCoords();
            }
            this.guildHallList.add(adder);
            this.unlockedHalls[adder.typeNum - 1] = true;
        }
    }

    public boolean containsHall(GuildHall hall) {
        for (int i = 0; i < this.guildHallList.size(); ++i) {
            if (hall.equals(this.guildHallList.get(i))) {
                return true;
            }
        }
        return false;
    }

    public TileEntityChest searchHallsForItem(ItemStack item) {
        this.checkHalls();
        for (int i = 0; i < this.guildHallList.size(); ++i) {
            GuildHall hall = this.guildHallList.get(i);
            hall.checkChests();
            for (int j = 0; j < hall.guildChests.size(); ++j) {
                TileEntityChest chest = hall.guildChests.get(j);
                if (AIHelper.chestContains(chest, item) >= 0) {
                    return chest;
                }
            }
        }
        return null;
    }

    public void mergeVillage(HelpfulVillage otherVillage) {
        this.villageDoors.addAll(otherVillage.villageDoors);
        double dist = 0;
        this.initBounds();
        for (int i = 0; i < this.villageDoors.size(); ++i) {
            BlockPos currDoor = this.villageDoors.get(i);
            if (currDoor.getX() < this.minX) {
                this.minX = currDoor.getX() - 5;
            } else if (currDoor.getX() > this.maxX) {
                this.maxX = currDoor.getX() + 5;
            }
            if (currDoor.getY() < this.minY) {
                this.minY = currDoor.getY() - 5;
            } else if (currDoor.getY() > this.maxY) {
                this.maxY = currDoor.getY() + 5;
            }
            if (currDoor.getZ() < this.minZ) {
                this.minZ = currDoor.getZ() - 5;
            } else if (currDoor.getZ() > this.maxZ) {
                this.maxZ = currDoor.getZ() + 5;
            }
            dist = Math.max(currDoor.distanceSq(this.actualCenter), dist);
        }
        this.radius = Math.max(32, (int) Math.sqrt(dist) + 5);
        this.actualBounds = box(this.minX, this.minY, this.minZ, this.maxX, this.maxY, this.maxZ);
        this.actualCenter = this.getActualCenter();
        this.villageBounds = box(this.actualCenter.getX() - this.radius, this.actualCenter.getY() - this.radius,
                this.actualCenter.getZ() - this.radius, this.actualCenter.getX() + this.radius,
                this.actualCenter.getY() + this.radius, this.actualCenter.getZ() + this.radius);
        this.craftQueue.mergeQueue(otherVillage.craftQueue);
    }

    public void writeVillageDataToNBT(NBTTagCompound nbttagcompound) {
        nbttagcompound.setTag("Dimension", new NBTTagInt(this.dimension));
        int[] villageCoords = new int[] { this.initialCenter.getX(), this.initialCenter.getY(), this.initialCenter.getZ() };
        nbttagcompound.setTag("Center", new NBTTagIntArray(villageCoords));
        nbttagcompound.setTag("Crafts", this.craftQueue.writeToNBT(new NBTTagList()));
        if (this.pricesCalculated) {
            nbttagcompound.setTag("Economy", this.economy.writeToNBT(new NBTTagList()));
        }
    }

    public void readVillageDataFromNBT(NBTTagCompound nbttagcompound1) {
        this.dimension = nbttagcompound1.getInteger("Dimension");
        int[] village = nbttagcompound1.getIntArray("Center");
        this.initialCenter = new BlockPos(village[0], village[1], village[2]);
        this.init();
        NBTTagList nbttaglist = nbttagcompound1.getTagList("Crafts", 10);
        this.craftQueue.readFromNBT(nbttaglist);
        if (nbttagcompound1.hasKey("Economy")) {
            this.priceCalcStarted = true;
            nbttaglist = nbttagcompound1.getTagList("Economy", 10);
            this.economy.readFromNBT(nbttaglist);
            this.pricesCalculated = true;
        }
    }
}
