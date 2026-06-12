package com.spege.helpfulvillagers.village;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.block.BlockDoor;
import net.minecraft.block.BlockFurnace;
import net.minecraft.block.BlockWorkbench;
import net.minecraft.entity.item.EntityItemFrame;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemAxe;
import net.minecraft.item.ItemBow;
import net.minecraft.item.ItemFishingRod;
import net.minecraft.item.ItemHoe;
import net.minecraft.item.ItemLead;
import net.minecraft.item.ItemPickaxe;
import net.minecraft.item.ItemSpade;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.tileentity.TileEntityFurnace;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * A profession guild hall: an item frame holding a profession marker next to a door, plus the
 * flood-filled interior used to locate chests/furnaces and the villager entrance.
 *
 * <p>1.12.2 migration: ChunkCoordinates -> BlockPos, block-by-coords -> getBlockState,
 * World.isSideSolid(ForgeDirection) -> isSideSolid(BlockPos, EnumFacing). The recursive
 * interior flood-fill (including the original's copy/paste neighbour quirks) is preserved.
 */
@SuppressWarnings("null")
public class GuildHall {
    private HelpfulVillage village;
    public World worldObj;
    public EntityItemFrame itemFrame;
    public BlockPos doorCoords;
    public BlockPos entranceCoords;
    public ArrayList<BlockPos> insideCoords = new ArrayList<BlockPos>();
    /** Companion HashSet for O(1) contains() lookups — always kept in sync with insideCoords. */
    public HashSet<BlockPos> insideCoordsSet = new HashSet<BlockPos>();
    public int typeNum;
    public ArrayList<TileEntityChest> guildChests = new ArrayList<TileEntityChest>();
    public ArrayList<TileEntityFurnace> guildFurnaces = new ArrayList<TileEntityFurnace>();

    public GuildHall() {
    }

    public GuildHall(World world, HelpfulVillage village) {
        this.worldObj = world;
        this.village = village;
        this.itemFrame = null;
        this.doorCoords = null;
        this.entranceCoords = null;
        this.typeNum = -1;
    }

    private Block blockAt(int x, int y, int z) {
        return this.worldObj.getBlockState(new BlockPos(x, y, z)).getBlock();
    }

    private Block blockAt(BlockPos pos) {
        return this.worldObj.getBlockState(pos).getBlock();
    }

    private static boolean isDoor(Block block) {
        return block instanceof BlockDoor && block != Blocks.IRON_DOOR;
    }

    public void findCoords(int profession, List<EntityItemFrame> itemFrames) {
        for (EntityItemFrame itemFrame : itemFrames) {
            if (itemFrame.getDisplayedItem().isEmpty() || !this.matchesProfession(itemFrame, profession)
                    || !this.isNextToDoor(itemFrame)) {
                continue;
            }
            this.itemFrame = itemFrame;
            this.typeNum = profession;
            try {
                this.fillInsideCoords();
                this.findEntranceCoords();
            } catch (StackOverflowError e) {
                this.insideCoords.clear();
                this.insideCoordsSet.clear();
            }
            break;
        }
    }

    private boolean matchesProfession(EntityItemFrame itemFrame, int profession) {
        ItemStack itemStack = itemFrame.getDisplayedItem();
        if (itemStack.isEmpty()) {
            return false;
        }
        switch (profession) {
            case 1:
                return itemStack.getItem() instanceof ItemAxe;
            case 2:
                return itemStack.getItem() instanceof ItemPickaxe;
            case 3:
                return itemStack.getItem() instanceof ItemHoe;
            case 4:
                return itemStack.getItem() instanceof ItemSword;
            case 5:
                return itemStack.getItem() instanceof ItemBow;
            case 6:
                return itemStack.getItem().equals(Items.EMERALD);
            case 7:
                return itemStack.getItem() instanceof ItemFishingRod;
            case 8:
                return itemStack.getItem() instanceof ItemLead;
            case 9:
                return itemStack.getItem() instanceof ItemSpade;
            case 10:
                return itemStack.getItem().equals(Items.EXPERIENCE_BOTTLE);
        }
        return false;
    }

    private boolean isNextToDoor(EntityItemFrame entity) {
        int posX = (int) entity.posX;
        int posY = (int) entity.posY;
        int posZ = (int) entity.posZ;
        boolean doorFlag = false;
        this.doorCoords = new BlockPos(posX, posY, posZ);
        for (int i = 0; i < 3; ++i) {
            for (int j = 0; j < 3; ++j) {
                for (int k = 0; k < 3; ++k) {
                    if (isDoor(this.blockAt(posX + i, posY + j, posZ + k))) {
                        this.doorCoords = new BlockPos(posX + i, posY + j, posZ + k);
                        doorFlag = true;
                    } else if (isDoor(this.blockAt(posX + i, posY + j, posZ - k))) {
                        this.doorCoords = new BlockPos(posX + i, posY + j, posZ - k);
                        doorFlag = true;
                    } else if (isDoor(this.blockAt(posX + i, posY - j, posZ - k))) {
                        this.doorCoords = new BlockPos(posX + i, posY - j, posZ - k);
                        doorFlag = true;
                    } else if (isDoor(this.blockAt(posX + i, posY - j, posZ + k))) {
                        this.doorCoords = new BlockPos(posX + i, posY - j, posZ + k);
                        doorFlag = true;
                    } else if (isDoor(this.blockAt(posX - i, posY + j, posZ + k))) {
                        this.doorCoords = new BlockPos(posX - i, posY + j, posZ + k);
                        doorFlag = true;
                    } else if (isDoor(this.blockAt(posX - i, posY + j, posZ - k))) {
                        this.doorCoords = new BlockPos(posX - i, posY + j, posZ - k);
                        doorFlag = true;
                    } else if (isDoor(this.blockAt(posX - i, posY - j, posZ + k))) {
                        this.doorCoords = new BlockPos(posX - i, posY - j, posZ + k);
                        doorFlag = true;
                    } else if (isDoor(this.blockAt(posX - i, posY - j, posZ - k))) {
                        this.doorCoords = new BlockPos(posX - i, posY - j, posZ - k);
                        doorFlag = true;
                    }
                }
            }
        }
        if (doorFlag) {
            // If the block below the detected door is itself a (wooden) door, the detected
            // position was the upper half; snap doorCoords down to the lower half.
            BlockPos below = this.doorCoords.down();
            if (isDoor(this.blockAt(below))) {
                this.doorCoords = below;
            }
        } else {
            this.doorCoords = null;
        }
        return doorFlag;
    }

    private void fillInsideCoords() {
        this.addInsideCoord(this.doorCoords);
        int dx = this.doorCoords.getX();
        int dy = this.doorCoords.getY();
        int dz = this.doorCoords.getZ();
        switch (this.itemFrame.getRotation()) {
            case 0: {
                BlockPos startCoords = new BlockPos(dx, dy, dz - 1);
                this.addInsideCoord(new BlockPos(dx, dy, dz + 1));
                this.checkZDirection(startCoords, -1);
                break;
            }
            case 1: {
                BlockPos startCoords = new BlockPos(dx + 1, dy, dz);
                this.addInsideCoord(new BlockPos(dx - 1, dy, dz));
                this.checkXDirection(startCoords, 1);
                break;
            }
            case 2: {
                BlockPos startCoords = new BlockPos(dx, dy, dz + 1);
                this.addInsideCoord(new BlockPos(dx, dy, dz - 1));
                this.checkZDirection(startCoords, 1);
                break;
            }
            case 3: {
                BlockPos startCoords = new BlockPos(dx - 1, dy, dz);
                this.addInsideCoord(new BlockPos(dx + 1, dy, dz));
                this.checkXDirection(startCoords, -1);
                break;
            }
            default:
                break;
        }
    }

    /**
     * continueFlag = (not a solid wall on {@code side} AND not glass-like) OR workbench OR furnace.
     * Mirrors the original per-direction expansion condition.
     */
    /** Adds pos to insideCoords and the companion HashSet in one call. */
    private void addInsideCoord(BlockPos pos) {
        this.insideCoords.add(pos);
        this.insideCoordsSet.add(pos);
    }

    private boolean canContinue(BlockPos pos, EnumFacing side) {
        Block b = this.blockAt(pos);
        boolean glassLike = b == Blocks.GLASS || b == Blocks.GLASS_PANE
                || b == Blocks.STAINED_GLASS || b == Blocks.STAINED_GLASS_PANE;
        return (!this.worldObj.isSideSolid(pos, side) && !glassLike)
                || b instanceof BlockWorkbench || b instanceof BlockFurnace;
    }

    private void checkXDirection(BlockPos currentCoords, int direction) {
        boolean continueFlag = false;
        if (!this.insideCoordsSet.contains(currentCoords) && this.isInside(currentCoords)) {
            continueFlag = this.canContinue(currentCoords, direction < 0 ? EnumFacing.WEST : EnumFacing.EAST);
        }
        this.addInsideCoord(currentCoords);
        if (continueFlag) {
            int x = currentCoords.getX();
            int y = currentCoords.getY();
            int z = currentCoords.getZ();
            this.checkXDirection(new BlockPos(x + direction, y, z), direction);
            this.checkYDirection(new BlockPos(x, y - 1, z), -1);
            this.checkYDirection(new BlockPos(x, y + 1, z), 1);
            this.checkZDirection(new BlockPos(x, y, z - 1), -1);
            this.checkZDirection(new BlockPos(x, y, z + 1), 1);
        }
    }

    private void checkYDirection(BlockPos currentCoords, int direction) {
        boolean continueFlag = false;
        if (!this.insideCoordsSet.contains(currentCoords) && this.isInside(currentCoords)) {
            continueFlag = this.canContinue(currentCoords, direction < 0 ? EnumFacing.UP : EnumFacing.DOWN);
        }
        this.addInsideCoord(currentCoords);
        if (continueFlag) {
            int x = currentCoords.getX();
            int y = currentCoords.getY();
            int z = currentCoords.getZ();
            // Fixed: symmetric 6-neighbour flood-fill. The 1.7.10 original's +X neighbour spuriously
            // offset Z by +1, so the interior fill leaked diagonally along one branch.
            this.checkYDirection(new BlockPos(x, y + direction, z), direction);
            this.checkXDirection(new BlockPos(x - 1, y, z), -1);
            this.checkXDirection(new BlockPos(x + 1, y, z), 1);
            this.checkZDirection(new BlockPos(x, y, z - 1), -1);
            this.checkZDirection(new BlockPos(x, y, z + 1), 1);
        }
    }

    private void checkZDirection(BlockPos currentCoords, int direction) {
        boolean continueFlag = false;
        if (!this.insideCoordsSet.contains(currentCoords) && this.isInside(currentCoords)) {
            continueFlag = this.canContinue(currentCoords, direction < 0 ? EnumFacing.SOUTH : EnumFacing.NORTH);
        }
        this.addInsideCoord(currentCoords);
        if (continueFlag) {
            int x = currentCoords.getX();
            int y = currentCoords.getY();
            int z = currentCoords.getZ();
            // Fixed: symmetric 6-neighbour flood-fill. The 1.7.10 original swapped the X/Z methods here
            // (continued Z via checkXDirection and branched X via checkZDirection), so the wall-solidity
            // check (canContinue uses the per-axis face) tested the wrong face and the fill mis-shaped
            // along Z corridors. Each neighbour now uses its own axis method.
            this.checkZDirection(new BlockPos(x, y, z + direction), direction);
            this.checkYDirection(new BlockPos(x, y - 1, z), -1);
            this.checkYDirection(new BlockPos(x, y + 1, z), 1);
            this.checkXDirection(new BlockPos(x - 1, y, z), -1);
            this.checkXDirection(new BlockPos(x + 1, y, z), 1);
        }
    }

    private void findEntranceCoords() {
        int dx = this.doorCoords.getX();
        int dy = this.doorCoords.getY();
        int dz = this.doorCoords.getZ();
        BlockPos[] candidates = new BlockPos[] {
                new BlockPos(dx + 3, dy, dz),
                new BlockPos(dx - 3, dy, dz),
                new BlockPos(dx, dy, dz + 3),
                new BlockPos(dx, dy, dz - 3),
        };
        for (BlockPos candidate : candidates) {
            if (!this.isInside(candidate) && this.worldObj.isAirBlock(candidate)) {
                this.entranceCoords = candidate;
                return;
            }
        }
    }

    private boolean isInside(BlockPos currentCoords) {
        if (!this.worldObj.canSeeSky(currentCoords)) {
            return true;
        }
        for (int i = currentCoords.getY() + 1; i < 256; ++i) {
            if (!this.worldObj.isAirBlock(new BlockPos(currentCoords.getX(), i, currentCoords.getZ()))) {
                return true;
            }
        }
        return false;
    }

    public BlockPos getFrameCoords() {
        return new BlockPos((int) this.itemFrame.posX, (int) this.itemFrame.posY, (int) this.itemFrame.posZ);
    }

    public void checkFrame() {
        if (this.worldObj.isRemote) {
            return;
        }
        if (this.itemFrame == null) {
            this.village.guildHallList.remove(this);
            this.village.unlockedHalls[this.typeNum - 1] = false;
        } else if (!(this.itemFrame.isEntityAlive() && this.matchesProfession(this.itemFrame, this.typeNum))) {
            this.itemFrame = null;
            this.village.guildHallList.remove(this);
            this.village.unlockedHalls[this.typeNum - 1] = false;
        }
    }

    public void checkChests() {
        this.guildChests.clear();
        for (BlockPos currentCoords : this.insideCoords) {
            Block block = this.blockAt(currentCoords);
            if (block != Blocks.CHEST && block != Blocks.TRAPPED_CHEST) {
                continue;
            }
            if (this.worldObj.getTileEntity(currentCoords) instanceof TileEntityChest) {
                TileEntityChest chest = (TileEntityChest) this.worldObj.getTileEntity(currentCoords);
                if (!this.guildChests.contains(chest)) {
                    this.guildChests.add(chest);
                }
            }
        }
    }

    public void checkFurnaces() {
        this.guildFurnaces.clear();
        for (BlockPos currentCoords : this.insideCoords) {
            if (!(this.blockAt(currentCoords) instanceof BlockFurnace)) {
                continue;
            }
            if (this.worldObj.getTileEntity(currentCoords) instanceof TileEntityFurnace) {
                TileEntityFurnace furnace = (TileEntityFurnace) this.worldObj.getTileEntity(currentCoords);
                if (!this.guildFurnaces.contains(furnace)) {
                    this.guildFurnaces.add(furnace);
                }
            }
        }
    }

    public boolean hasWorkbench() {
        for (BlockPos coords : this.insideCoords) {
            if (this.blockAt(coords) instanceof BlockWorkbench) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean equals(Object object) {
        if (object instanceof GuildHall) {
            GuildHall guildHall = (GuildHall) object;
            if (this.typeNum == guildHall.typeNum && this.doorCoords != null
                    && this.doorCoords.equals(guildHall.doorCoords)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public int hashCode() {
        return this.typeNum * 31 + (this.doorCoords != null ? this.doorCoords.hashCode() : 0);
    }

    public int getTypeNum() {
        return this.typeNum;
    }

    public void setTypeNum(int typeNum) {
        this.typeNum = typeNum;
    }

    public boolean typeMatchesName(String name) {
        if (name.contains("Lumberjack")) {
            return this.typeNum == 1;
        }
        if (name.contains("Miner")) {
            return this.typeNum == 2;
        }
        if (name.contains("Farmer")) {
            return this.typeNum == 3;
        }
        if (name.contains("Soldier")) {
            return this.typeNum == 4;
        }
        if (name.contains("Archer")) {
            return this.typeNum == 5;
        }
        if (name.contains("Merchant")) {
            return this.typeNum == 6;
        }
        return false;
    }

    public TileEntityChest getAvailableChest() {
        this.checkChests();
        for (TileEntityChest chest : this.guildChests) {
            int size = chest.getSizeInventory();
            for (int i = 0; i < size; ++i) {
                if (chest.getStackInSlot(i).isEmpty()) {
                    return chest;
                }
            }
        }
        return null;
    }

    public TileEntityFurnace getAvailableFurnace() {
        this.checkFurnaces();
        for (TileEntityFurnace furnace : this.guildFurnaces) {
            if (furnace.getStackInSlot(0).isEmpty()) {
                return furnace;
            }
        }
        return null;
    }
}
