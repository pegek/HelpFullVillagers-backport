package com.spege.helpfulvillagers.entity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.spege.helpfulvillagers.ai.EntityAIExitWater;
import com.spege.helpfulvillagers.ai.EntityAIFollowLeader;
import com.spege.helpfulvillagers.ai.EntityAIMoveIndoorsCustom;
import com.spege.helpfulvillagers.ai.EntityAIVillagerMateCustom;
import com.spege.helpfulvillagers.crafting.CraftItem;
import com.spege.helpfulvillagers.crafting.CraftTree;
import com.spege.helpfulvillagers.crafting.VillagerRecipe;
import com.spege.helpfulvillagers.enums.EnumActivity;
import com.spege.helpfulvillagers.enums.EnumMessage;
import com.spege.helpfulvillagers.inventory.InventoryVillager;
import com.spege.helpfulvillagers.main.HelpfulVillagers;
import com.spege.helpfulvillagers.network.CraftItemClientPacket;
import com.spege.helpfulvillagers.network.CraftQueueClientPacket;
import com.spege.helpfulvillagers.network.CustomRecipesPacket;
import com.spege.helpfulvillagers.network.LeaderPacket;
import com.spege.helpfulvillagers.network.PlayerAccountClientPacket;
import com.spege.helpfulvillagers.network.PlayerMessagePacket;
import com.spege.helpfulvillagers.network.UnlockedHallsPacket;
import com.spege.helpfulvillagers.network.VillageSyncPacket;
import com.spege.helpfulvillagers.util.AIHelper;
import com.spege.helpfulvillagers.util.ResourceCluster;
import com.spege.helpfulvillagers.village.GuildHall;
import com.spege.helpfulvillagers.village.HelpfulVillage;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.ai.EntityAIFollowGolem;
import net.minecraft.entity.ai.EntityAILookAtTradePlayer;
import net.minecraft.entity.ai.EntityAIMoveTowardsRestriction;
import net.minecraft.entity.ai.EntityAIOpenDoor;
import net.minecraft.entity.ai.EntityAIPlay;
import net.minecraft.entity.ai.EntityAISwimming;
import net.minecraft.entity.ai.EntityAITradePlayer;
import net.minecraft.entity.ai.EntityAIWander;
import net.minecraft.entity.ai.EntityAIWatchClosest;
import net.minecraft.entity.ai.EntityAIWatchClosest2;
import net.minecraft.entity.ai.RandomPositionGenerator;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.monster.IMob;
import net.minecraft.entity.passive.EntityVillager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemBow;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagInt;
import net.minecraft.nbt.NBTTagIntArray;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.pathfinding.PathNavigateGround;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.tileentity.TileEntityFurnace;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.village.MerchantRecipeList;
import net.minecraft.world.World;

/**
 * Base class for all profession villagers. Extends {@link EntityVillager} but replaces its trade
 * behaviour with the mod's recruit/equip/command/craft/economy systems.
 *
 * <p>1.12.2 migration highlights:
 * <ul>
 *   <li>Profession is a plain field set in each subclass constructor (no DataWatcher needed — it is
 *       available client-side because the concrete entity class is the synced thing).</li>
 *   <li>ChunkCoordinates -> BlockPos; AxisAlignedBB is immutable so the search/pickup boxes are
 *       reassigned each tick rather than mutated.</li>
 *   <li>{@code processInteract(EntityPlayer, EnumHand)}, {@code swingArm(EnumHand)},
 *       {@code setHomePosAndDistance(BlockPos,int)}, {@code setItemStackToSlot(EntityEquipmentSlot,..)},
 *       Vec3 -> Vec3d.</li>
 *   <li>The 1.7.10 {@code setProfession(int)} override (which spawns a replacement entity) is renamed
 *       {@link #changeProfession(int)} to avoid clashing with EntityVillager.setProfession.</li>
 * </ul>
 */
@SuppressWarnings({ "null", "deprecation" })
public abstract class AbstractVillager extends EntityVillager {
    public int profession;
    public String nickname;
    public String profName;
    public InventoryVillager inventory;
    public BlockPos villageCenter;
    public HelpfulVillage homeVillage;
    public GuildHall homeGuildHall;
    public EntityLivingBase leader;
    private int leaderID;
    public int guiCommand;
    public boolean hasTool;
    private boolean isSwinging;
    private int swingTicks;
    private int healthTicks;
    private boolean dayCheck;
    private boolean hasDied;
    public boolean changeGuildHall;
    protected ItemStack[] validTools = new ItemStack[0];
    public AxisAlignedBB searchBox;
    public AxisAlignedBB pickupBox;
    protected int searchRadius;
    protected int pickupRadius;
    private boolean canPickup;
    public ArrayList<VillagerRecipe> knownRecipes = new ArrayList<VillagerRecipe>();
    public ArrayList<VillagerRecipe> customRecipes = new ArrayList<VillagerRecipe>();
    public CraftItem currentCraftItem;
    public ArrayList<CraftTree.Node> craftChain = new ArrayList<CraftTree.Node>();
    public ArrayList<ItemStack> materialsNeeded = new ArrayList<ItemStack>();
    public ArrayList<ItemStack> smeltablesNeeded = new ArrayList<ItemStack>();
    public ItemStack queuedTool = ItemStack.EMPTY;
    public EnumActivity currentActivity;
    public ResourceCluster lastResource;
    public ResourceCluster currentResource;
    public int villagerID;

    public AbstractVillager(World world) {
        super(world);
        this.stepHeight = (float) ((double) this.stepHeight + 0.5);
        this.profession = 0;
        this.nickname = "";
        this.inventory = new InventoryVillager(this);
        this.villageCenter = null;
        this.homeVillage = null;
        this.homeGuildHall = null;
        this.leader = null;
        this.guiCommand = -1;
        this.isSwinging = false;
        this.healthTicks = 0;
        this.searchBox = this.getEntityBoundingBox();
        this.pickupBox = this.getEntityBoundingBox();
        this.searchRadius = 1;
        this.pickupRadius = 1;
        this.canPickup = true;
        this.currentActivity = EnumActivity.IDLE;
        this.lastResource = null;
        this.hasTool = false;
        this.villagerID = 0;
        this.leaderID = 0;
        this.dayCheck = true;
        this.changeGuildHall = false;
        this.hasDied = false;
        this.currentCraftItem = null;
        this.addAI();
    }

    public AbstractVillager(AbstractVillager villager) {
        super(villager.world);
        this.stepHeight = (float) ((double) this.stepHeight + 0.5);
        this.nickname = villager.nickname;
        villager.inventory.dumpCollected(true);
        villager.inventory.dumpCollected(false);
        this.inventory = villager.inventory;
        this.inventory.owner = this;
        this.villageCenter = villager.villageCenter;
        this.homeVillage = villager.homeVillage;
        this.homeGuildHall = null;
        this.leader = villager.leader;
        this.guiCommand = villager.guiCommand;
        this.isSwinging = false;
        this.healthTicks = 0;
        this.searchBox = this.getEntityBoundingBox();
        this.pickupBox = this.getEntityBoundingBox();
        this.searchRadius = 1;
        this.pickupRadius = 1;
        this.canPickup = true;
        this.currentActivity = EnumActivity.IDLE;
        this.lastResource = null;
        this.hasTool = false;
        this.villagerID = 0;
        this.leaderID = 0;
        this.dayCheck = true;
        this.changeGuildHall = false;
        this.hasDied = false;
        this.customRecipes.addAll(villager.customRecipes);
        villager.addCraftItem(villager.currentCraftItem);
        this.currentCraftItem = null;
        for (ItemStack i : villager.inventory.materialsCollected) {
            this.inventory.addItem(i);
        }
        this.inventory.materialsCollected.clear();
        this.setCustomNameTag(villager.getCustomNameTag());
        this.addAI();
    }

    private void addAI() {
        this.tasks.taskEntries.clear();
        // The visual equipment slots mirror the villager's own inventory items; the mod drops the
        // inventory itself on death, so disable vanilla equipment drops to avoid duplicating them.
        for (EntityEquipmentSlot slot : EntityEquipmentSlot.values()) {
            this.setDropChance(slot, 0.0f);
        }
        if (this.getNavigator() instanceof PathNavigateGround) {
            PathNavigateGround nav = (PathNavigateGround) this.getNavigator();
            nav.setBreakDoors(true);
            nav.setEnterDoors(true);
        }
        this.tasks.addTask(1, new EntityAIFollowLeader(this));
        this.tasks.addTask(2, new EntityAIMoveIndoorsCustom(this));
        this.tasks.addTask(0, new EntityAISwimming(this));
        // Actively walk back to dry land when submerged (swimming alone only keeps them afloat).
        this.tasks.addTask(0, new EntityAIExitWater(this));
        this.tasks.addTask(1, new EntityAITradePlayer(this));
        this.tasks.addTask(1, new EntityAILookAtTradePlayer(this));
        this.tasks.addTask(2, new EntityAIVillagerMateCustom(this));
        this.tasks.addTask(4, new EntityAIOpenDoor(this, true));
        this.tasks.addTask(5, new EntityAIMoveTowardsRestriction(this, 0.3));
        this.tasks.addTask(7, new EntityAIFollowGolem(this));
        this.tasks.addTask(8, new EntityAIPlay(this, 0.32));
        this.tasks.addTask(9, new EntityAIWatchClosest2(this, EntityPlayer.class, 3.0f, 1.0f));
        this.tasks.addTask(9, new EntityAIWatchClosest2(this, EntityVillager.class, 5.0f, 0.02f));
        this.tasks.addTask(9, new EntityAIWander(this, 0.3));
        this.tasks.addTask(10, new EntityAIWatchClosest(this, EntityLivingBase.class, 8.0f));
    }

    @Override
    public boolean processInteract(EntityPlayer player, EnumHand hand) {
        if (hand != EnumHand.MAIN_HAND) {
            return false;
        }
        if (player.isSneaking()) {
            return false;
        }
        if (HelpfulVillagers.player_guard.containsKey(player)) {
            AbstractVillager guard = HelpfulVillagers.player_guard.remove(player);
            if (guard.equals(this)) {
                HelpfulVillagers.player_guard.put(player, guard);
            } else {
                guard.setLeader(this);
                if (this.world.isRemote) {
                    String guardName = guard.getCustomNameTag();
                    guardName = guardName == null || guardName.equals("") || guardName.equals(" ")
                            ? "A " + guard.profName : guardName + " the " + guard.profName;
                    String leaderName = this.getCustomNameTag();
                    leaderName = leaderName == null || leaderName.equals("") || leaderName.equals(" ")
                            ? "this " + this.profName : leaderName + " the " + this.profName;
                    String message = guardName + " is now guarding " + leaderName;
                    player.sendMessage(new TextComponentString(message));
                }
                return false;
            }
        }
        if (this.isChild()) {
            return false;
        }
        this.setCustomer(player);
        if (!this.world.isRemote && !this.customRecipes.isEmpty()) {
            HelpfulVillagers.network.sendTo(new CustomRecipesPacket(this.getEntityId(), this.customRecipes), (EntityPlayerMP) player);
            if (this.leader == null) {
                HelpfulVillagers.network.sendTo(new LeaderPacket(this.getEntityId(), -1), (EntityPlayerMP) player);
            } else {
                HelpfulVillagers.network.sendTo(new LeaderPacket(this.getEntityId(), this.leader.getEntityId()), (EntityPlayerMP) player);
            }
        }
        HelpfulVillagers.logger.info("[HV] processInteract: {} id={} opening GUI 0 for player {}",
                this.getClass().getSimpleName(), this.getEntityId(), player.getName());
        player.openGui(HelpfulVillagers.instance, 0, this.world, this.getEntityId(), 0, 0);
        return true;
    }

    private void handleGuiCommand() {
        EntityPlayer player = this.getCustomer();
        switch (this.guiCommand) {
            case 0: {
                this.setLeader(player);
                this.currentActivity = EnumActivity.FOLLOW;
                if (this instanceof EntityLumberjack || this instanceof EntityFarmer) {
                    this.lastResource = this.currentResource;
                    this.currentResource = null;
                }
                break;
            }
            case 1: {
                if (this.leader instanceof EntityMiner) {
                    ((EntityMiner) this.leader).beingFollowed = false;
                }
                this.setLeader(null);
                this.currentActivity = EnumActivity.IDLE;
                break;
            }
            case 2: {
                if (!this.isEntityAlive() || this.isChild() || player.isSneaking()) {
                    break;
                }
                if (!this.world.isRemote) {
                    this.inventory.syncInventory();
                }
                player.openGui(HelpfulVillagers.instance, 2, this.world, this.getEntityId(), 0, 0);
                break;
            }
            case 3: {
                if (!this.world.isRemote) {
                    try {
                        if (!(player instanceof EntityPlayerMP)) {
                            throw new Exception();
                        }
                        HelpfulVillagers.network.sendTo(new UnlockedHallsPacket(this.getEntityId(), this.homeVillage.unlockedHalls), (EntityPlayerMP) player);
                    } catch (Exception e) {
                        HelpfulVillagers.network.sendToAll(new UnlockedHallsPacket(this.getEntityId(), this.homeVillage.unlockedHalls));
                    }
                }
                player.openGui(HelpfulVillagers.instance, 1, this.world, this.getEntityId(), 0, 0);
                break;
            }
            case 4: {
                player.openGui(HelpfulVillagers.instance, 3, this.world, this.getEntityId(), 0, 0);
                break;
            }
            case 5: {
                HelpfulVillagers.player_guard.put(player, this);
                if (this.leader instanceof EntityMiner) {
                    ((EntityMiner) this.leader).beingFollowed = false;
                }
                this.setLeader(player);
                this.currentActivity = EnumActivity.FOLLOW;
                break;
            }
            case 6: {
                if (!this.world.isRemote) {
                    HelpfulVillagers.network.sendTo(new CraftItemClientPacket(this.getEntityId(), this.currentCraftItem, this.inventory.materialsCollected, this.materialsNeeded), (EntityPlayerMP) player);
                    try {
                        HelpfulVillagers.network.sendTo(new CraftQueueClientPacket(this.getEntityId(), this.homeVillage.craftQueue.getAll()), (EntityPlayerMP) player);
                    } catch (Exception e) {
                        System.out.println("Packet Error");
                        HelpfulVillagers.network.sendToAll(new CraftQueueClientPacket(this.getEntityId(), this.homeVillage.craftQueue.getAll()));
                    }
                }
                player.openGui(HelpfulVillagers.instance, 4, this.world, this.getEntityId(), 0, 0);
                break;
            }
            case 7: {
                player.openGui(HelpfulVillagers.instance, 6, this.world, this.getEntityId(), 0, 0);
                break;
            }
            case 8: {
                if (!this.world.isRemote) {
                    this.homeVillage.economy.fullSyncClient(this, player);
                    int amount = this.homeVillage.economy.getAccount(player);
                    if (amount < 0) {
                        this.homeVillage.economy.setAccount(player, 0);
                    }
                    HelpfulVillagers.network.sendTo(new PlayerAccountClientPacket(player, this), (EntityPlayerMP) player);
                }
                player.openGui(HelpfulVillagers.instance, 7, this.world, this.getEntityId(), 0, 0);
                break;
            }
            case 9: {
                if (!this.world.isRemote) {
                    player.displayVillagerTradeGui(this);
                }
                break;
            }
            case 10: {
                player.openGui(HelpfulVillagers.instance, 8, this.world, this.getEntityId(), 0, 0);
                break;
            }
        }
        this.guiCommand = -1;
    }

    @Override
    public void onUpdate() {
        super.onUpdate();
        // Debug: log the very first server-side tick so we know our entities are active.
        if (!this.world.isRemote && this.ticksExisted == 1) {
            HelpfulVillagers.logger.info("[HV] First tick: {} id={} at {},{},{} homeVillage={}",
                    this.getClass().getSimpleName(), this.getEntityId(),
                    (int) this.posX, (int) this.posY, (int) this.posZ,
                    this.homeVillage != null ? this.homeVillage.initialCenter : "null");
        }
        if (this.guiCommand >= 0) {
            try {
                this.handleGuiCommand();
            } catch (NullPointerException e) {
                this.guiCommand = -1;
            }
        }
        if (this.leader != null) {
            this.currentActivity = EnumActivity.FOLLOW;
        }
        if (this.dayCheck && this.world.isDaytime()) {
            this.dayCheck = false;
            this.dayCheck();
        } else if (!this.dayCheck && !this.world.isDaytime()) {
            this.dayCheck = true;
        }
        // Throttle expensive per-villager calls to avoid flooding the server thread.
        // getNewHomeVillage: every 40 ticks (2 s) — loops over all villages.
        if (this.ticksExisted % 40 == 0 || this.homeVillage == null) {
            this.getNewHomeVillage();
        }
        // syncVillage (VillageSyncPacket): every 20 ticks (1 s per villager).
        // The 1.7.10 original sent this every tick, causing a severe packet flood.
        if (this.ticksExisted % 20 == 0) {
            this.syncVillage();
        }
        this.getNewGuildHall();
        this.updateBoxes();
        this.updateArmor();
        this.updateSwing();
        this.updateHealth();
        this.updateID();
        this.updateLeader();
        this.pickupItems();
        this.resetTool();
        this.resetArmor();
        this.getCraftItem();
    }

    public void moveTo(BlockPos coords, float speed) {
        try {
            if (!this.getNavigator().tryMoveToXYZ(coords.getX(), coords.getY(), coords.getZ(), speed)) {
                // Direct path failed — try a reachable point biased toward the target so the villager
                // can route around obstacles incrementally instead of giving up.
                // NOTE: the 1.7.10 original also called setHomePosAndDistance(currentPos, 20) here, which
                // hijacked the village home binding — every failed long-distance path re-anchored the
                // villager's "home" to wherever it currently stood, so home followed the villager and the
                // village tether / return-to-hall cycle broke. Home is managed solely by dayCheck() now.
                Vec3d vector = new Vec3d(coords.getX(), coords.getY(), coords.getZ());
                Vec3d tempVec = RandomPositionGenerator.findRandomTargetBlockTowards(this, 10, 3, vector);
                if (tempVec != null) {
                    this.getNavigator().tryMoveToXYZ(tempVec.x, tempVec.y, tempVec.z, speed);
                }
            }
        } catch (NullPointerException nullPointerException) {
            // empty catch block
        }
    }

    public void moveTo(Entity entity, float speed) {
        try {
            if (!this.getNavigator().tryMoveToEntityLiving(entity, speed)) {
                // See moveTo(BlockPos): the home-binding side effect from the 1.7.10 original is removed.
                Vec3d vector = new Vec3d((int) entity.posX, (int) entity.posY, (int) entity.posZ);
                Vec3d tempVec = RandomPositionGenerator.findRandomTargetBlockTowards(this, 10, 3, vector);
                if (tempVec != null) {
                    this.getNavigator().tryMoveToXYZ(tempVec.x, tempVec.y, tempVec.z, speed);
                }
            }
        } catch (NullPointerException nullPointerException) {
            // empty catch block
        }
    }

    public BlockPos getCoords() {
        return new BlockPos((int) this.posX, (int) this.posY, (int) this.posZ);
    }

    public ArrayList<BlockPos> getSurroundingCoords() {
        ArrayList<BlockPos> coords = new ArrayList<BlockPos>();
        for (int x = (int) this.posX - 1; x <= (int) this.posX + 1; ++x) {
            for (int y = (int) this.posY; y <= (int) this.posY + 1; ++y) {
                for (int z = (int) this.posZ - 1; z <= (int) this.posZ + 1; ++z) {
                    coords.add(new BlockPos(x, y, z));
                }
            }
        }
        return coords;
    }

    public int getDirection() {
        return MathHelper.floor((double) (this.rotationYaw * 4.0f / 360.0f) + 0.5) & 3;
    }

    /**
     * Changes this villager's profession by spawning a fresh entity of the target profession class
     * (carrying inventory/state over via the copy constructor) and removing this one. Renamed from
     * the 1.7.10 {@code setProfession(int)} override to avoid clashing with EntityVillager.
     */
    public void changeProfession(int par1) {
        if (this.profession == par1) {
            return;
        }
        if (this instanceof EntityFisherman) {
            EntityFisherman fisherman = (EntityFisherman) this;
            if (fisherman.fishEntity != null) {
                fisherman.fishEntity.setDead();
            }
        }
        AbstractVillager replacement = null;
        boolean requireVillagerLeader = true;
        switch (par1) {
            case 0:
                replacement = new EntityRegularVillager(this);
                break;
            case 1:
                replacement = new EntityLumberjack(this);
                break;
            case 2:
                replacement = new EntityMiner(this);
                break;
            case 3:
                replacement = new EntityFarmer(this);
                break;
            case 4:
                replacement = new EntitySoldier(this);
                requireVillagerLeader = false;
                break;
            case 5:
                replacement = new EntityArcher(this);
                requireVillagerLeader = false;
                break;
            case 6:
                replacement = new EntityMerchant(this);
                break;
            case 7:
                replacement = new EntityFisherman(this);
                break;
            case 8:
                replacement = new EntityRancher(this);
                break;
            case 9:
                replacement = new EntityBuilder(this);
                break;
            default:
                return;
        }
        replacement.setLocationAndAngles(this.posX, this.posY, this.posZ, this.rotationYaw, this.rotationPitch);
        if (!this.world.isRemote) {
            this.world.spawnEntity(replacement);
            // Soldiers/archers may follow non-villager leaders; other professions only sync a
            // villager-to-non-villager leader (matches the 1.7.10 per-case conditions).
            if (replacement.leader != null && (!requireVillagerLeader || !(replacement.leader instanceof AbstractVillager))) {
                HelpfulVillagers.network.sendToAll(new LeaderPacket(replacement.getEntityId(), replacement.leader.getEntityId()));
            }
        }
        this.setDead();
    }

    @Override
    public int getProfession() {
        return this.profession;
    }

    public void setLeader(EntityLivingBase leader) {
        this.leader = leader;
        if (leader != null && leader instanceof AbstractVillager) {
            if (this.profession == 4 || this.profession == 5) {
                AbstractVillager villager = (AbstractVillager) leader;
                this.leaderID = villager.villagerID;
                if (villager instanceof EntityMiner) {
                    ((EntityMiner) villager).beingFollowed = true;
                }
            } else {
                this.leader = null;
                this.leaderID = -1;
            }
        } else {
            this.leaderID = -1;
        }
    }

    public EntityLivingBase getLeader() {
        return this.leader;
    }

    /**
     * Regular (profession 0) villagers fall back to the vanilla trade system (their forge profession
     * is assigned at spawn in {@link EntityRegularVillager}), so the "Trade" dialog button opens normal
     * villager trades. Worker professions have no vanilla trades — they use the custom barter/economy
     * system instead — so they return null here.
     */
    @Override
    public MerchantRecipeList getRecipes(EntityPlayer player) {
        if (this.profession == 0) {
            return super.getRecipes(player);
        }
        return null;
    }

    public void takeItemFromPlayer(EntityPlayer player) {
        this.inventory.setCurrentItem(player.inventory.getCurrentItem());
        player.inventory.setInventorySlotContents(player.inventory.currentItem, ItemStack.EMPTY);
    }

    public void giveItemToPlayer(EntityPlayer player) {
        if (!this.inventory.getCurrentItem().isEmpty()) {
            player.inventory.addItemStackToInventory(this.inventory.getCurrentItem());
            this.inventory.setCurrentItem(ItemStack.EMPTY);
        }
    }

    public ItemStack[] getValidTools() {
        return this.validTools;
    }

    public abstract boolean isValidTool(ItemStack stack);

    protected void setTools(ItemStack[] items) {
        this.validTools = new ItemStack[items.length];
        System.arraycopy(items, 0, this.validTools, 0, this.validTools.length);
    }

    protected abstract boolean canCraft();

    public boolean canCraft(CraftItem craftItem) {
        return this.canCraft(craftItem.getItem());
    }

    public boolean canCraft(ItemStack item) {
        if (item.isEmpty()) {
            return false;
        }
        for (VillagerRecipe i : this.knownRecipes) {
            if (i.getOutput().getItem().equals(item.getItem())) {
                return true;
            }
        }
        return false;
    }

    public VillagerRecipe getRecipe(ItemStack item) {
        if (item.isEmpty()) {
            return null;
        }
        for (VillagerRecipe i : this.knownRecipes) {
            if (i.getOutput().getDisplayName().equals(item.getDisplayName())) {
                return i;
            }
        }
        return null;
    }

    public void addCustomRecipe(VillagerRecipe recipe) {
        if (!this.customRecipes.contains(recipe) && !this.knownRecipes.contains(recipe)) {
            this.customRecipes.add(recipe);
            this.knownRecipes.add(recipe);
            Collections.sort(this.knownRecipes);
        }
    }

    public void replaceCustomRecipe(VillagerRecipe recipe) {
        int index = -1;
        for (int i = 0; i < this.customRecipes.size(); ++i) {
            if (this.customRecipes.get(i).getOutput().getDisplayName().equals(recipe.getOutput().getDisplayName())) {
                index = i;
                break;
            }
        }
        if (index >= 0) {
            ArrayList<VillagerRecipe> temp = new ArrayList<VillagerRecipe>(this.customRecipes);
            this.resetRecipes();
            this.customRecipes.addAll(temp);
            this.customRecipes.set(index, recipe);
            this.knownRecipes.addAll(this.customRecipes);
            Collections.sort(this.knownRecipes);
        } else {
            System.out.println("Recipe Not Found: Replace");
            this.addCustomRecipe(recipe);
        }
    }

    public void deleteCustomRecipe(VillagerRecipe recipe) {
        int index = -1;
        for (int i = 0; i < this.customRecipes.size(); ++i) {
            if (this.customRecipes.get(i).getOutput().getDisplayName().equals(recipe.getOutput().getDisplayName())) {
                index = i;
                break;
            }
        }
        if (index >= 0) {
            ArrayList<VillagerRecipe> temp = new ArrayList<VillagerRecipe>(this.customRecipes);
            this.resetRecipes();
            this.customRecipes.addAll(temp);
            this.customRecipes.remove(index);
            this.knownRecipes.addAll(this.customRecipes);
            Collections.sort(this.knownRecipes);
        } else {
            System.out.println("Recipe Not Found: Delete");
        }
    }

    public void resetRecipes() {
        this.customRecipes.clear();
        this.knownRecipes.clear();
        switch (this.profession) {
            case 1:
                this.knownRecipes.addAll(HelpfulVillagers.lumberjackRecipes);
                break;
            case 2:
                this.knownRecipes.addAll(HelpfulVillagers.minerRecipes);
                break;
            case 3:
                this.knownRecipes.addAll(HelpfulVillagers.farmerRecipes);
                break;
        }
    }

    public boolean isMetadataSensitive(ItemStack item) {
        if (item.isEmpty()) {
            return false;
        }
        VillagerRecipe recipe = this.getRecipe(item);
        if (recipe == null) {
            return false;
        }
        return recipe.getMetadataSensitivity();
    }

    private void resetTool() {
        ItemStack current = this.getCurrentItem();
        if (!current.isEmpty() && !this.isValidTool(current)) {
            this.hasTool = false;
            if (!this.inventory.isFull()) {
                this.inventory.addItem(this.inventory.getCurrentItem());
            } else {
                EntityItem worldItem = new EntityItem(this.world, this.posX, this.posY, this.posZ, current);
                this.world.spawnEntity(worldItem);
            }
            this.inventory.setCurrentItem(ItemStack.EMPTY);
        } else if (current.isEmpty()) {
            this.hasTool = false;
            int index = this.inventory.containsItem();
            if (index >= 0) {
                this.inventory.swapEquipment(index, 0);
                this.hasTool = true;
            }
        } else {
            this.hasTool = true;
        }
    }

    /** True when all four armour slots (28-31) are occupied. */
    public boolean isFullyArmored() {
        for (int i = 28; i < 32; ++i) {
            if (this.inventory.getStackInSlot(i).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    // --- Resupply hooks (EntityAIGuardResupply) -------------------------------------------------
    // Generic so the resupply AI works on any profession; combat professions override what they need.

    /** True when this villager is out of combat ammunition and should restock at the guild hall. */
    public boolean needsCombatAmmo() {
        return false;
    }

    /** The ammunition item to pull from guild chests when {@link #needsCombatAmmo()}. */
    public ItemStack getCombatAmmoItem() {
        return ItemStack.EMPTY;
    }

    /** True when this villager wants an offhand item (e.g. a soldier's shield) from the guild hall. */
    public boolean needsOffhandEquipment() {
        return false;
    }

    /** Items the villager keeps when depositing loot at the guild hall (e.g. an archer's arrows). */
    public boolean shouldKeepInInventory(ItemStack stack) {
        return false;
    }

    private void resetArmor() {
        for (int i = 28; i < 32; ++i) {
            ItemStack item = this.inventory.getStackInSlot(i);
            if (this.inventory.isItemValidForSlot(i, item)) {
                continue;
            }
            this.inventory.addItem(item);
            this.inventory.setInventorySlotContents(i, ItemStack.EMPTY);
        }
    }

    public ItemStack getCurrentItem() {
        return this.inventory.getCurrentItem();
    }

    public void damageItem() {
        ItemStack current = this.getCurrentItem();
        if (current.isEmpty()) {
            return;
        }
        current.setItemDamage(current.getItemDamage() + 1);
        if (current.getItemDamage() >= current.getMaxDamage()) {
            this.inventory.setCurrentItem(ItemStack.EMPTY);
        }
    }

    @Override
    public void swingArm(EnumHand hand) {
        if (this.world.isRemote) {
            if (!this.isSwinging || this.swingTicks >= 4 || this.swingTicks < 0) {
                this.swingTicks = -1;
                this.isSwinging = true;
            }
        } else {
            HelpfulVillagers.network.sendToAll(new com.spege.helpfulvillagers.network.SwingPacket(this.getEntityId()));
        }
    }

    private void updateSwing() {
        if (this.isSwinging) {
            ++this.swingTicks;
            if (this.swingTicks >= 8) {
                this.swingTicks = 0;
                this.isSwinging = false;
            }
        } else {
            this.swingTicks = 0;
        }
        this.swingProgress = (float) this.swingTicks / 8.0f;
    }

    public boolean isSwinging() {
        return this.isSwinging;
    }

    public void updateBoxes() {
        this.searchBox = new AxisAlignedBB(
                this.posX - this.searchRadius, this.posY - this.searchRadius, this.posZ - this.searchRadius,
                this.posX + this.searchRadius, this.posY + this.searchRadius, this.posZ + this.searchRadius);
        this.pickupBox = new AxisAlignedBB(
                this.posX - this.pickupRadius, this.posY - this.pickupRadius, this.posZ - this.pickupRadius,
                this.posX + this.pickupRadius, this.posY + this.pickupRadius, this.posZ + this.pickupRadius);
    }

    public void setPickupRadius(int radius) {
        this.pickupRadius = radius;
        this.updateBoxes();
    }

    protected void pickupItems() {
        if (this.isChild() || !this.canPickup) {
            return;
        }
        if (this.inventory.isFull()) {
            return;
        }
        List<EntityItem> items = this.world.getEntitiesWithinAABB(EntityItem.class, this.pickupBox);
        for (EntityItem currentItem : items) {
            if (currentItem.isDead) {
                continue;
            }
            this.inventory.addItem(currentItem.getItem());
            currentItem.setDead();
        }
    }

    /**
     * Pushes the villager's held tool + worn armour into the vanilla equipment slots so they render
     * on the model.
     *
     * <p>Server-side ONLY: the vanilla entity tracker syncs these six slots to every watching client
     * automatically (SPacketEntityEquipment). Running this on the client too (onUpdate fires on both
     * sides) made the client overwrite the tracker-synced equipment with its own — often stale/empty —
     * inventory copy, so nothing rendered. We also no longer push a per-tick InventoryPacket here (that
     * was a packet flood); the inventory GUI gets a full sync via {@link InventoryVillager#syncInventory}
     * when it is opened.
     */
    private void updateArmor() {
        if (this.world.isRemote) {
            return;
        }
        this.setItemStackToSlot(EntityEquipmentSlot.MAINHAND, this.inventory.getCurrentItem());
        this.setItemStackToSlot(EntityEquipmentSlot.HEAD, this.inventory.getStackInSlot(28));
        this.setItemStackToSlot(EntityEquipmentSlot.CHEST, this.inventory.getStackInSlot(29));
        this.setItemStackToSlot(EntityEquipmentSlot.LEGS, this.inventory.getStackInSlot(30));
        this.setItemStackToSlot(EntityEquipmentSlot.FEET, this.inventory.getStackInSlot(31));
    }

    private void updateHealth() {
        ++this.healthTicks;
        if (this.healthTicks == 60) {
            this.heal(0.5f);
            this.healthTicks = 0;
        }
    }

    private void updateLeader() {
        if (!this.world.isRemote) {
            if (this.leader != null) {
                EntityLivingBase temp = (EntityLivingBase) this.world.getEntityByID(this.leader.getEntityId());
                this.setLeader(temp);
            } else if (this.leaderID > 0) {
                AbstractVillager temp = HelpfulVillagers.villager_id.get(this.leaderID);
                this.setLeader(temp);
            }
        }
    }

    private void updateID() {
        if (!this.world.isRemote && this.ticksExisted > this.getRNG().nextInt(10) && this.villagerID <= 0) {
            int newKey = Math.abs(this.getRNG().nextInt());
            while (HelpfulVillagers.villager_id.containsKey(newKey)) {
                newKey = Math.abs(this.getRNG().nextInt());
            }
            this.villagerID = newKey;
            HelpfulVillagers.villager_id.put(this.villagerID, this);
        }
    }

    protected void dayCheck() {
        if (this.changeGuildHall) {
            this.homeGuildHall = null;
            this.changeGuildHall = false;
        }
        if (this.homeVillage != null) {
            BlockPos center = this.homeVillage.getActualCenter();
            this.setHomePosAndDistance(center, this.homeVillage.getActualRadius());
        }
    }

    public boolean shouldReproduce() {
        if (this.homeVillage != null) {
            return Math.abs(this.world.getTotalWorldTime() - this.homeVillage.getLastAdded()) >= 1000L;
        }
        return false;
    }

    @Override
    public boolean attackEntityFrom(DamageSource src, float par2) {
        for (int i = 28; i < 32; ++i) {
            ItemStack armorPiece = this.inventory.getStackInSlot(i);
            if (armorPiece.isEmpty()) {
                continue;
            }
            armorPiece.setItemDamage((int) ((float) armorPiece.getItemDamage() + par2));
            if (armorPiece.getItemDamage() >= armorPiece.getMaxDamage()) {
                this.inventory.setInventorySlotContents(i, ItemStack.EMPTY);
            }
        }
        if (!this.world.isRemote && this.homeVillage != null
                && this.homeVillage.isInsideVillage(this.getCoords().getX(), this.getCoords().getY(), this.getCoords().getZ())) {
            Entity entity = src.getTrueSource();
            if (entity instanceof EntityLivingBase) {
                EntityLivingBase attacker = (EntityLivingBase) entity;
                if (attacker instanceof IMob && attacker.isEntityAlive()) {
                    this.homeVillage.lastAggressor = attacker;
                    this.homeVillage.lastAggressorTime = this.world.getTotalWorldTime();
                }
            }
        }
        return super.attackEntityFrom(src, par2);
    }

    public void getNewHomeVillage() {
        // Server-authoritative: villages live in the static HelpfulVillagers.villages list, which is
        // shared across the integrated client+server threads in single-player. Running this on the
        // client made a client villager join a server-created village and call
        // village.world.countEntities() on the WorldServer from the client thread, iterating the
        // server's live entity list concurrently -> ConcurrentModificationException. The client gets
        // its homeVillage container from the sync packets (VillageSync/UnlockedHalls/CraftQueue/...).
        if (this.world.isRemote) {
            return;
        }
        if (this.hasDied) {
            return;
        }
        if (this.homeVillage != null && this.homeVillage.isAnnihilated) {
            this.homeVillage = null;
        }
        if (this.homeVillage == null) {
            if (this.villageCenter != null) {
                for (int i = 0; i < HelpfulVillagers.villages.size(); ++i) {
                    HelpfulVillage v = HelpfulVillagers.villages.get(i);
                    if (v.isAnnihilated || !v.initialCenter.equals(this.villageCenter)) {
                        continue;
                    }
                    this.homeVillage = v;
                    this.homeVillage.addVillager();
                    return;
                }
            }
            double closestDist = 100.0;
            HelpfulVillage closestVillage = null;
            for (int i = 0; i < HelpfulVillagers.villages.size(); ++i) {
                HelpfulVillage currVillage = HelpfulVillagers.villages.get(i);
                if (currVillage.isAnnihilated) {
                    continue;
                }
                BlockPos center = currVillage.getActualCenter();
                double dist = Math.sqrt(this.getDistanceSq(center.getX(), center.getY(), center.getZ()));
                if (currVillage.isInsideVillage(this.posX, this.posY, this.posZ) || dist < closestDist) {
                    closestDist = dist;
                    closestVillage = currVillage;
                }
            }
            if (closestVillage != null) {
                this.homeVillage = closestVillage;
                this.villageCenter = this.homeVillage.initialCenter;
                this.homeVillage.addVillager();
            } else {
                int x = (int) this.posX;
                int z = (int) this.posZ;
                int y = this.world.getHeight(x, z);
                this.homeVillage = new HelpfulVillage(this.world, new BlockPos(x, y, z));
                this.villageCenter = this.homeVillage.initialCenter;
                HelpfulVillagers.villages.add(this.homeVillage);
                this.homeVillage.addVillager();
            }
        }
    }

    private void syncVillage() {
        if (!this.world.isRemote && this.homeVillage != null) {
            HelpfulVillagers.network.sendToAll(new VillageSyncPacket(this.homeVillage, this));
        }
    }

    public void returnToOrigin() {
        if (this.homeVillage == null) {
            this.getNavigator().tryMoveToXYZ(this.posX, this.posY, this.posZ, 0.3);
        } else {
            BlockPos center = this.homeVillage.getActualCenter();
            this.getNavigator().tryMoveToXYZ(center.getX(), center.getY(), center.getZ(), 0.3);
        }
    }

    public void getNewGuildHall() {
        if (!this.world.isRemote && this.homeVillage != null) {
            if (this.profession == 0) {
                this.homeGuildHall = null;
            } else if (this.homeGuildHall == null) {
                this.homeGuildHall = this.homeVillage.lookForExistingHall(this.profession);
            } else if (this.homeGuildHall.itemFrame == null) {
                this.homeGuildHall = this.homeVillage.lookForExistingHall(this.profession);
            }
        }
    }

    public void checkGuildHall() {
        if (this.currentActivity == EnumActivity.IDLE && this.homeGuildHall != null) {
            if (!this.homeVillage.guildHallList.contains(this.homeGuildHall)) {
                this.homeGuildHall = null;
            }
        }
    }

    public boolean nearHall() {
        if (this.homeGuildHall == null) {
            return false;
        }
        int px = (int) this.posX, py = (int) this.posY, pz = (int) this.posZ;
        // Check the villager's own position and all 26 neighbours using O(1) HashSet lookups.
        // Avoids allocating an ArrayList of 27 BlockPos per call (was called multiple times per tick).
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (this.homeGuildHall.insideCoordsSet.contains(new BlockPos(px + dx, py + dy, pz + dz))) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public boolean insideHall() {
        BlockPos currentPosition = new BlockPos((int) this.posX, (int) this.posY, (int) this.posZ);
        return this.homeGuildHall != null && this.homeGuildHall.insideCoordsSet.contains(currentPosition);
    }

    @Override
    public void onDeath(DamageSource src) {
        super.onDeath(src);
        if (!this.world.isRemote && this.homeVillage != null) {
            this.homeVillage.removeVillager();
        }
        this.hasDied = true;
        this.canPickup = false;
        this.inventory.dumpInventory();
        if (this.currentCraftItem != null) {
            this.addCraftItem(this.currentCraftItem);
        }
        this.sendDeathMessage(src);
    }

    private void sendDeathMessage(DamageSource src) {
        if (this.world.isRemote) {
            return;
        }
        EntityLivingBase lastTarget;
        String name = this.getCustomNameTag();
        name = name == null || name.equals("") || name.equals(" ") ? "A " + this.profName : name + " the " + this.profName;
        String cause = src.getDamageType();
        Entity attacker = src.getTrueSource();
        String attackerName = attacker != null ? attacker.getName() : "Unknown";
        EntityLivingBase aiAttacker = this.getRevengeTarget();
        cause = cause.equals("anvil") ? " was squashed by an anvil" : (cause.equals("cactus") ? (aiAttacker != null && aiAttacker.isEntityAlive() ? " walked into a cactus whilst trying to escape " + aiAttacker.getName() : " was pricked to death") : (cause.equals("arrow") ? (attackerName.equals("arrow") ? " was shot by an arrow" : " was shot by " + attackerName) : (cause.equals("drown") ? (aiAttacker != null && aiAttacker.isEntityAlive() ? " drowned whilst trying to escape " + aiAttacker.getName() : " drowned") : (cause.equals("explosion") ? " blew up" : (cause.equals("explosion.player") ? " was blown up by " + attackerName : (cause.equals("fall") ? (aiAttacker != null && aiAttacker.isEntityAlive() ? " was doomed to fall by " + aiAttacker.getName() : " fell from a high place") : (cause.equals("inFire") ? ((lastTarget = this.getLastAttackedEntity()) != null && lastTarget.isEntityAlive() ? " walked into a fire whilst fighting " + lastTarget.getName() : (aiAttacker != null && aiAttacker.isEntityAlive() ? " walked into a fire whilst trying to escape " + aiAttacker.getName() : " went up in flames")) : (cause.equals("onFire") ? ((lastTarget = this.getLastAttackedEntity()) != null && lastTarget.isEntityAlive() ? " was burnt to a crisp whilst fighting " + lastTarget.getName() : (aiAttacker != null && aiAttacker.isEntityAlive() ? " was burnt to a crisp whilst trying to escape " + aiAttacker.getName() : " burned to death")) : (cause.equals("mob") ? " was slain by a " + attackerName : (cause.equals("player") ? " was slain by " + attackerName : (cause.equals("fireball") ? " was fireballed by a " + attackerName : (cause.equals("indirectMagic") ? " was killed by " + attackerName + " using magic" : (cause.equals("magic") ? " was killed by magic" : (cause.equals("inWall") ? " suffocated in a wall" : (cause.equals("lava") ? (aiAttacker != null && aiAttacker.isEntityAlive() ? " tried to swim in lava while trying to escape " + aiAttacker.getName() : " tried to swim in lava") : (cause.equals("outOfWorld") ? (aiAttacker != null && aiAttacker.isEntityAlive() ? " was knocked into the void by " + aiAttacker.getName() : " fell out of the world") : (cause.equals("wither") ? " withered away" : (cause.equals("fallingBlock") ? " was squashed by a falling block" : " died"))))))))))))))))));
        String message = name + cause;
        HelpfulVillagers.network.sendToAll(new PlayerMessagePacket(message, EnumMessage.DEATH, this.getEntityId()));
    }

    public boolean shouldReturn() {
        return !this.inventory.isFull() && this.hasTool && this.world.isDaytime() && this.currentCraftItem == null;
    }

    public abstract ArrayList<BlockPos> getValidCoords();

    public float getAttackDamage() {
        ItemStack held = this.getCurrentItem();
        if (!held.isEmpty() && held.getItem() instanceof ItemSword) {
            return ((ItemSword) held.getItem()).getAttackDamage() + 4.0f;
        }
        if (!held.isEmpty() && held.getItem() instanceof ItemBow) {
            return 4.0f;
        }
        if (this instanceof EntityFarmer) {
            return Math.abs(((EntityFarmer) this).getHarvestTime() / 10 - 6);
        }
        return 1.0f;
    }

    public void addCraftItem(CraftItem item) {
        if (this.homeVillage != null && item != null) {
            if (item.getPriority() <= 0) {
                this.homeVillage.craftQueue.addVillagerItem(item);
            } else if (item.getPriority() >= 1) {
                this.homeVillage.craftQueue.addPlayerItem(item);
            }
        }
    }

    public void getCraftItem() {
        if (!this.world.isRemote && this.homeVillage != null && this.canCraft() && this.currentCraftItem == null) {
            this.homeVillage.craftQueue.getCraftItem(this);
        }
    }

    public void resetCraftItem() {
        this.currentCraftItem = null;
        this.materialsNeeded.clear();
        this.smeltablesNeeded.clear();
        this.inventory.materialsCollected.clear();
        this.craftChain.clear();
    }

    public void lookForItem(ItemStack item) {
        ItemStack tempItem = item.copy();
        for (int i = 0; i < this.homeVillage.guildHallList.size(); ++i) {
            GuildHall hall = this.homeVillage.guildHallList.get(i);
            hall.checkChests();
            for (int j = 0; j < hall.guildChests.size(); ++j) {
                TileEntityChest chest = hall.guildChests.get(j);
                if (AIHelper.takeItemFromChest(tempItem, chest, this)) {
                    return;
                }
            }
            hall.checkFurnaces();
            for (int j = 0; j < hall.guildFurnaces.size(); ++j) {
                TileEntityFurnace furnace = hall.guildFurnaces.get(j);
                if (AIHelper.takeItemFromFurnace(tempItem, furnace, this)) {
                    return;
                }
            }
        }
    }

    public boolean storeCraftedItem() {
        if (this.currentCraftItem.getPriority() == 0) {
            for (GuildHall hall : this.homeVillage.guildHallList) {
                if (!hall.typeMatchesName(this.currentCraftItem.getName())) {
                    continue;
                }
                for (TileEntityChest chest : hall.guildChests) {
                    for (int i = 0; i < chest.getSizeInventory(); ++i) {
                        if (chest.getStackInSlot(i).isEmpty()) {
                            chest.setInventorySlotContents(i, this.currentCraftItem.getItem());
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    @Override
    public void writeEntityToNBT(NBTTagCompound compound) {
        super.writeEntityToNBT(compound);
        if (this.villageCenter != null) {
            int[] villageCoords = new int[] { this.villageCenter.getX(), this.villageCenter.getY(), this.villageCenter.getZ() };
            compound.setTag("Village", new NBTTagIntArray(villageCoords));
        }
        compound.setTag("Inventory", this.inventory.writeToNBT(new NBTTagList()));
        if (this.lastResource != null) {
            compound.setTag("Resource", this.lastResource.writeToNBT(new NBTTagList()));
        }
        compound.setTag("VillagerID", new NBTTagInt(this.villagerID));
        compound.setTag("LeaderID", new NBTTagInt(this.leaderID));
        if (this.currentCraftItem != null) {
            NBTTagCompound craftCompound = new NBTTagCompound();
            this.currentCraftItem.writeToNBT(craftCompound);
            compound.setTag("Craft Item", craftCompound);
        }
        compound.setTag("CustomSize", new NBTTagInt(this.customRecipes.size()));
        for (int i = 0; i < this.customRecipes.size(); ++i) {
            compound.setTag("CustomRecipe" + i, this.customRecipes.get(i).writeToNBT(new NBTTagList()));
        }
        if (!this.queuedTool.isEmpty()) {
            NBTTagCompound queuedCompound = new NBTTagCompound();
            this.queuedTool.writeToNBT(queuedCompound);
            compound.setTag("Queued Tool", queuedCompound);
        }
    }

    @Override
    public void readEntityFromNBT(NBTTagCompound compound) {
        super.readEntityFromNBT(compound);
        int[] village = compound.getIntArray("Village");
        if (village.length > 0) {
            this.villageCenter = new BlockPos(village[0], village[1], village[2]);
            if (HelpfulVillagers.villageCollection == null || HelpfulVillagers.villageCollection.isEmpty()) {
                boolean addVillage = true;
                for (int i = 0; i < HelpfulVillagers.villages.size(); ++i) {
                    HelpfulVillage currVillage = HelpfulVillagers.villages.get(i);
                    if (!currVillage.initialCenter.equals(this.villageCenter)) {
                        continue;
                    }
                    this.homeVillage = currVillage;
                    this.homeVillage.addVillager();
                    addVillage = false;
                    break;
                }
                if (addVillage) {
                    this.homeVillage = new HelpfulVillage(this.world, this.villageCenter);
                    HelpfulVillagers.villages.add(this.homeVillage);
                    this.homeVillage.addVillager();
                }
            }
        }
        this.inventory.readFromNBT(compound.getTagList("Inventory", 10));
        if (compound.hasKey("Resource")) {
            this.lastResource = new ResourceCluster(this.world);
            this.lastResource.readFromNBT(compound.getTagList("Resource", 10));
        }
        this.villagerID = compound.getInteger("VillagerID");
        HelpfulVillagers.villager_id.put(this.villagerID, this);
        this.leaderID = compound.getInteger("LeaderID");
        if (compound.hasKey("Craft Item")) {
            this.currentCraftItem = CraftItem.loadCraftItemFromNBT(compound.getCompoundTag("Craft Item"));
        }
        int size = compound.getInteger("CustomSize");
        for (int i = 0; i < size; ++i) {
            VillagerRecipe recipe = new VillagerRecipe();
            recipe.readFromNBT(compound.getTagList("CustomRecipe" + i, 10));
            this.customRecipes.add(recipe);
        }
        this.knownRecipes.addAll(this.customRecipes);
        Collections.sort(this.knownRecipes);
        if (compound.hasKey("Queued Tool")) {
            this.queuedTool = new ItemStack(compound.getCompoundTag("Queued Tool"));
        }
    }

    public ITextComponent buildName() {
        String name = this.getCustomNameTag();
        if (name == null || name.equals("") || name.equals(" ")) {
            return new TextComponentString("A " + this.profName);
        }
        return new TextComponentString(name + " the " + this.profName);
    }
}
