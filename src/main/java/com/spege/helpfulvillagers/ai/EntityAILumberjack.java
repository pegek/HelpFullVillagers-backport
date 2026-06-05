package com.spege.helpfulvillagers.ai;

import java.util.ArrayDeque;
import java.util.Deque;

import javax.annotation.Nullable;

import com.spege.helpfulvillagers.entity.EntityLumberjack;
import com.spege.helpfulvillagers.main.HelpfulVillagers;
import com.spege.helpfulvillagers.util.AIHelper;

import net.minecraft.block.Block;
import net.minecraft.block.BlockLog;
import net.minecraft.block.BlockNewLog;
import net.minecraft.block.BlockOldLog;
import net.minecraft.block.BlockPlanks;
import net.minecraft.block.BlockSapling;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;
import net.minecraft.util.NonNullList;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * Lumberjack gathering AI — Thrall-inspired rewrite.
 *
 * <p>The gather phase is driven by a three-state sub-machine:
 * <ul>
 *   <li>{@link WoodState#SEARCHING} — throttled radius scan for the nearest log
 *       <em>outside</em> the home village bounds; wanders toward forest if none found.</li>
 *   <li>{@link WoodState#NAVIGATING} — paths to target log with a nav-timeout so unreachable
 *       trees don't block the villager forever.</li>
 *   <li>{@link WoodState#CHOPPING} — mines the log, discovers connected logs (whole-tree BFS,
 *       climbs trunk first), replants matching sapling on the stump.</li>
 * </ul>
 *
 * <p>The outer {@link EntityAIWorker} cycle (IDLE / STORE / CRAFT / RETURN) is unchanged;
 * {@code gather()} still ends with {@code return this.idle()}.
 *
 * <p>Diagnosis notes:
 * Previous implementation was blocked by two filters that prevented any work in a typical
 * village: (1) logs were only registered when {@code !isInRangeOfAnyVillage}, but the guild
 * hall is inside the village; (2) an aggressive adjacency filter (cobblestone/planks/fence/…)
 * rejected almost every cluster near a built-up village. Both are replaced by a deterministic
 * scan that skips logs <em>inside</em> the village's actual bounding box.
 */
@SuppressWarnings({ "null", "deprecation" })
public class EntityAILumberjack extends EntityAIWorker {

    // ---- Constants ---------------------------------------------------------------
    /** Radius (blocks) of each log scan. */
    private static final int SCAN_RANGE = 24;
    /** Ticks between successive scans while no target is found. */
    private static final int SCAN_INTERVAL = 50;
    /** Ticks allowed to path to a target before abandoning it (~10 s). */
    private static final int NAV_TIMEOUT_TICKS = 200;
    /** Squared reach distance — transition NAVIGATING→CHOPPING. */
    private static final double CLOSE_ENOUGH_SQ = 8.0 * 8.0;
    /** Hardness multiplier: block hardness * SPEED_MULT = ticks to break. */
    private static final float SPEED_MULT = 15.0f;
    /** Minimum ticks to break any log (prevents instant-break on hardness=0 edge case). */
    private static final int MIN_MINING_TICKS = 5;
    /** Abandon the current tree after this many consecutive blocks that survive setBlockToAir. */
    private static final int MAX_STUCK_BLOCKS = 3;

    // ---- State machine -----------------------------------------------------------
    private enum WoodState { SEARCHING, NAVIGATING, CHOPPING }

    private final EntityLumberjack lumberjack;
    private WoodState woodState = WoodState.SEARCHING;

    @Nullable private BlockPos targetLog = null;
    private final Deque<BlockPos> connectedLogs = new ArrayDeque<>();

    private int navTimer = 0;
    private int miningTicks = 0;
    private int miningTicksRequired = MIN_MINING_TICKS;
    private int consecutiveStuckBlocks = 0;
    private long lastScanTime = 0L;

    // ---- Debug logging (deduped) -------------------------------------------------
    private String lastDebugMsg = "";

    private void log(String msg) {
        if (this.lumberjack.world.isRemote || msg.equals(this.lastDebugMsg)) return;
        this.lastDebugMsg = msg;
        HelpfulVillagers.logger.info("[HV][LUMBER] id={} state={} pos={},{},{} | {}",
                this.lumberjack.getEntityId(), this.woodState,
                (int) this.lumberjack.posX, (int) this.lumberjack.posY,
                (int) this.lumberjack.posZ, msg);
    }

    // ---- Constructor -------------------------------------------------------------
    public EntityAILumberjack(EntityLumberjack lumberjack) {
        super(lumberjack);
        this.lumberjack = lumberjack;
        this.setMutexBits(1);
    }

    // ---- EntityAIWorker contract -------------------------------------------------

    @Override
    protected boolean gather() {
        switch (this.woodState) {
            case SEARCHING:  this.tickSearching();  break;
            case NAVIGATING: this.tickNavigating(); break;
            case CHOPPING:   this.tickChopping();   break;
        }
        return this.idle();
    }

    // ---- SEARCHING ---------------------------------------------------------------

    private void tickSearching() {
        long now = this.lumberjack.world.getTotalWorldTime();
        // Try queued connected logs first (continue felling the same tree)
        while (!this.connectedLogs.isEmpty()) {
            BlockPos next = this.connectedLogs.poll();
            if (next != null && isLog(this.lumberjack.world.getBlockState(next))) {
                this.log("SEARCHING: continuing tree — next log at " + next);
                this.setTarget(next);
                return;
            }
        }
        // Throttle full scans
        if (now - this.lastScanTime < SCAN_INTERVAL && this.lastScanTime != 0) return;
        this.lastScanTime = now;

        BlockPos nearest = this.findNearestLog();
        if (nearest != null) {
            this.log("SEARCHING: found log at " + nearest
                    + " dist=" + String.format("%.1f", Math.sqrt(this.lumberjack.getDistanceSq(nearest))));
            this.setTarget(nearest);
        } else {
            this.log("SEARCHING: no logs in radius=" + SCAN_RANGE + " outside village — wandering");
            // Wander toward forest
            BlockPos wander = AIHelper.getRandOutsideCoords(this.lumberjack, 20);
            if (wander != null) this.lumberjack.moveTo(wander, this.speed);
        }
    }

    private void setTarget(BlockPos pos) {
        this.targetLog = pos;
        this.navTimer = 0;
        this.woodState = WoodState.NAVIGATING;
    }

    @Nullable
    private BlockPos findNearestLog() {
        BlockPos origin = new BlockPos(this.lumberjack);
        World world = this.lumberjack.world;
        BlockPos best = null;
        double bestDistSq = Double.MAX_VALUE;

        int minY = Math.max(1, origin.getY() - 5);
        int maxY = Math.min(255, origin.getY() + 20);

        BlockPos.MutableBlockPos mpos = new BlockPos.MutableBlockPos();
        for (int x = origin.getX() - SCAN_RANGE; x <= origin.getX() + SCAN_RANGE; x++) {
            for (int z = origin.getZ() - SCAN_RANGE; z <= origin.getZ() + SCAN_RANGE; z++) {
                for (int y = minY; y <= maxY; y++) {
                    mpos.setPos(x, y, z);
                    if (!world.isBlockLoaded(mpos)) continue;
                    if (!isLog(world.getBlockState(mpos))) continue;
                    // Skip logs inside the village — protect decorative/village trees
                    if (this.isInsideVillage(x, y, z)) continue;
                    double distSq = origin.distanceSq(mpos);
                    if (distSq < bestDistSq) {
                        bestDistSq = distSq;
                        best = mpos.toImmutable();
                    }
                }
            }
        }
        return best;
    }

    private boolean isInsideVillage(int x, int y, int z) {
        if (this.lumberjack.homeVillage == null
                || this.lumberjack.homeVillage.actualBounds == null) return false;
        return this.lumberjack.homeVillage.isInsideVillage(x, y, z);
    }

    // ---- NAVIGATING --------------------------------------------------------------

    private void tickNavigating() {
        if (this.targetLog == null || !isLog(this.lumberjack.world.getBlockState(this.targetLog))) {
            this.log("NAVIGATING: target " + this.targetLog + " is no longer a log — back to SEARCHING");
            this.abandonTarget();
            return;
        }

        this.navTimer++;
        if (this.navTimer > NAV_TIMEOUT_TICKS) {
            this.log("NAVIGATING: TIMEOUT after " + this.navTimer + "t at " + this.targetLog + " — abandoning");
            this.abandonTarget();
            return;
        }

        double distSq = this.lumberjack.getDistanceSq(this.targetLog);
        if (distSq <= CLOSE_ENOUGH_SQ) {
            this.log("NAVIGATING: reached " + this.targetLog + " (distSq=" + String.format("%.1f", distSq) + ") — CHOPPING");
            this.startChopping();
            return;
        }

        if (this.lumberjack.getNavigator().noPath()) {
            boolean ok = this.lumberjack.getNavigator().tryMoveToXYZ(
                    this.targetLog.getX() + 0.5, this.targetLog.getY(),
                    this.targetLog.getZ() + 0.5, this.speed);
            if (!ok) {
                this.log("NAVIGATING: pathfinding FAILED to " + this.targetLog + " — skipping log");
                this.abandonTarget();
                return;
            }
        }

        this.lumberjack.getLookHelper().setLookPosition(
                this.targetLog.getX() + 0.5, this.targetLog.getY() + 0.5,
                this.targetLog.getZ() + 0.5, 10.0f, 10.0f);
    }

    private void abandonTarget() {
        this.targetLog = null;
        this.navTimer = 0;
        this.lastScanTime = 0L; // allow immediate rescan
        this.woodState = WoodState.SEARCHING;
        this.lumberjack.getNavigator().clearPath();
    }

    // ---- CHOPPING ----------------------------------------------------------------

    private void startChopping() {
        IBlockState bs = this.lumberjack.world.getBlockState(this.targetLog);
        float hardness = bs.getBlockHardness(this.lumberjack.world, this.targetLog);
        if (hardness < 0) {
            this.log("CHOPPING: block " + this.targetLog + " is unbreakable (hardness=" + hardness + ") — skip");
            this.abandonTarget();
            return;
        }
        this.miningTicksRequired = Math.max(MIN_MINING_TICKS, (int) (hardness * SPEED_MULT));
        this.miningTicks = 0;
        this.woodState = WoodState.CHOPPING;
        this.log("CHOPPING: start block=" + this.targetLog + " hardness=" + hardness
                + " ticksRequired=" + this.miningTicksRequired);
    }

    private void tickChopping() {
        if (this.targetLog == null || !isLog(this.lumberjack.world.getBlockState(this.targetLog))) {
            if (this.targetLog != null) {
                this.log("CHOPPING: target " + this.targetLog + " disappeared — polling queue");
            }
            this.targetLog = null;
            this.pollConnectedOrSearch();
            return;
        }

        // Look at target
        this.lumberjack.getLookHelper().setLookPosition(
                this.targetLog.getX() + 0.5, this.targetLog.getY() + 0.5,
                this.targetLog.getZ() + 0.5, 10.0f, 10.0f);

        // Keep within reach — approach if too far, clear path when very close
        double distSq = this.lumberjack.getDistanceSq(
                this.targetLog.getX() + 0.5, this.targetLog.getY(), this.targetLog.getZ() + 0.5);
        if (distSq > 9.0) {
            this.lumberjack.getNavigator().tryMoveToXYZ(
                    this.targetLog.getX() + 0.5, this.targetLog.getY(),
                    this.targetLog.getZ() + 0.5, this.speed);
        } else if (distSq < 2.25) {
            this.lumberjack.getNavigator().clearPath();
        }

        // Swing every 5 ticks
        if (this.miningTicks % 5 == 0) {
            this.lumberjack.swingArm(EnumHand.MAIN_HAND);
        }
        this.miningTicks++;

        if (this.miningTicks < this.miningTicksRequired) return;

        // Break the log
        this.log("CHOPPING: breaking " + this.targetLog + " after " + this.miningTicks + "t");
        BlockPos broken = this.targetLog;
        IBlockState minedState = this.breakLog(broken);
        this.discoverConnectedLogs(broken);
        this.targetLog = null;

        // Stuck-block detection (e.g. protected zone)
        if (!this.lumberjack.world.isAirBlock(broken)) {
            this.consecutiveStuckBlocks++;
            this.log("CHOPPING: log at " + broken + " survived break (stuck #" + this.consecutiveStuckBlocks + ")");
            if (this.consecutiveStuckBlocks >= MAX_STUCK_BLOCKS) {
                this.log("CHOPPING: too many stuck blocks — aborting tree");
                this.consecutiveStuckBlocks = 0;
                this.connectedLogs.clear();
                this.woodState = WoodState.SEARCHING;
                this.lastScanTime = 0L;
                return;
            }
        } else {
            this.consecutiveStuckBlocks = 0;
            // Replant only after confirming block was removed (so stuck-check air test isn't foiled)
            if (minedState != null) this.tryReplantSapling(broken, minedState);
        }

        this.pollConnectedOrSearch();
    }

    /** Poll next connected log from queue, or go back to SEARCHING. */
    private void pollConnectedOrSearch() {
        while (!this.connectedLogs.isEmpty()) {
            BlockPos next = this.connectedLogs.poll();
            if (next != null && isLog(this.lumberjack.world.getBlockState(next))) {
                this.log("CHOPPING: felling next connected log at " + next
                        + " (" + this.connectedLogs.size() + " still queued)");
                this.setTarget(next);
                return;
            }
        }
        this.log("CHOPPING: no more connected logs — back to SEARCHING");
        this.woodState = WoodState.SEARCHING;
        this.lastScanTime = 0L;
        this.lumberjack.foundTree = false;
    }

    // ---- Block operations --------------------------------------------------------

    /**
     * Breaks the log: plays particle event, collects drops into the lumberjack's inventory,
     * sets block to air, damages the axe. Returns the state that was at pos, or null.
     */
    @Nullable
    private IBlockState breakLog(BlockPos pos) {
        World world = this.lumberjack.world;
        IBlockState bs = world.getBlockState(pos);
        Block block = bs.getBlock();
        world.playEvent(2001, pos, Block.getStateId(bs));
        NonNullList<ItemStack> drops = NonNullList.create();
        block.getDrops(drops, world, pos, bs, 0);
        for (ItemStack drop : drops) {
            this.lumberjack.inventory.addItem(drop);
            this.lumberjack.damageItem();
        }
        world.setBlockToAir(pos);
        this.lumberjack.foundTree = true;
        return bs;
    }

    /**
     * 26-neighbour BFS pass: pushes connected logs into the queue.
     * Blocks above (dy > 0) go to the front so we climb the trunk first.
     */
    private void discoverConnectedLogs(BlockPos origin) {
        World world = this.lumberjack.world;
        int found = 0;
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) continue;
                    BlockPos nb = origin.add(dx, dy, dz);
                    if (isLog(world.getBlockState(nb)) && !this.connectedLogs.contains(nb)) {
                        if (dy > 0) this.connectedLogs.addFirst(nb);
                        else        this.connectedLogs.addLast(nb);
                        found++;
                    }
                }
            }
        }
        this.log("CHOPPING: discoverConnected(" + origin + ") found=" + found
                + " queue=" + this.connectedLogs.size());
    }

    /**
     * Thrall-style stump replanting: plants a matching sapling at {@code pos} if the block
     * below is dirt/grass. Falls back to any sapling in inventory if the exact variant is missing.
     */
    private void tryReplantSapling(BlockPos pos, IBlockState minedState) {
        World world = this.lumberjack.world;
        Block ground = world.getBlockState(pos.down()).getBlock();
        if (ground != Blocks.DIRT && ground != Blocks.GRASS) return;
        if (!world.isAirBlock(pos)) return;

        int preferredMeta = saplingMetaForLog(minedState);
        int slot = (preferredMeta >= 0) ? this.findSaplingSlot(preferredMeta) : -1;
        if (slot < 0) slot = this.findSaplingSlot(-1);
        if (slot < 0) return;

        ItemStack saplingStack = this.lumberjack.inventory.getStackInSlot(slot);
        BlockPlanks.EnumType saplingType = BlockPlanks.EnumType.byMetadata(saplingStack.getMetadata() & 7);
        IBlockState saplingState = Blocks.SAPLING.getDefaultState()
                .withProperty(BlockSapling.TYPE, saplingType);
        world.setBlockState(pos, saplingState, 3);

        this.lumberjack.inventory.decrementSlot(slot);
        this.log("CHOPPING: replanted sapling (meta=" + preferredMeta + ") at " + pos);
    }

    /** Maps a vanilla log state to its matching sapling metadata (0–5), or -1 for modded logs. */
    private static int saplingMetaForLog(IBlockState logState) {
        Block b = logState.getBlock();
        if (b instanceof BlockOldLog) return logState.getValue(BlockOldLog.VARIANT).getMetadata();
        if (b instanceof BlockNewLog) return logState.getValue(BlockNewLog.VARIANT).getMetadata();
        return -1;
    }

    /**
     * Finds a sapling in the lumberjack's inventory.
     * Pass {@code desiredMeta = -1} to accept any sapling variant.
     */
    private int findSaplingSlot(int desiredMeta) {
        Item saplingItem = Item.getItemFromBlock(Blocks.SAPLING);
        for (int i = 0; i < this.lumberjack.inventory.getSizeInventory(); i++) {
            ItemStack s = this.lumberjack.inventory.getStackInSlot(i);
            if (s.isEmpty() || s.getItem() != saplingItem) continue;
            if (desiredMeta < 0 || s.getMetadata() == desiredMeta) return i;
        }
        return -1;
    }

    // ---- Log detection -----------------------------------------------------------

    /**
     * Soft log detection: matches vanilla {@link BlockLog} AND modded blocks whose registry
     * name path contains "log" (covers most mod wood packs).
     */
    private static boolean isLog(@Nullable IBlockState bs) {
        if (bs == null) return false;
        Block b = bs.getBlock();
        if (b instanceof BlockLog) return true;
        ResourceLocation rn = b.getRegistryName();
        return rn != null && rn.getResourcePath().contains("log");
    }
}
