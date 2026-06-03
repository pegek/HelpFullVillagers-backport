package com.spege.helpfulvillagers.main;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.Logger;

import com.spege.helpfulvillagers.crafting.VillagerRecipe;
import com.spege.helpfulvillagers.entity.AbstractVillager;
import com.spege.helpfulvillagers.entity.EntityArcher;
import com.spege.helpfulvillagers.entity.EntityFarmer;
import com.spege.helpfulvillagers.entity.EntityFishHookCustom;
import com.spege.helpfulvillagers.entity.EntityFisherman;
import com.spege.helpfulvillagers.entity.EntityLumberjack;
import com.spege.helpfulvillagers.entity.EntityMerchant;
import com.spege.helpfulvillagers.entity.EntityMiner;
import com.spege.helpfulvillagers.entity.EntityRancher;
import com.spege.helpfulvillagers.entity.EntityRegularVillager;
import com.spege.helpfulvillagers.entity.EntitySoldier;
import com.spege.helpfulvillagers.network.AddRecipePacket;
import com.spege.helpfulvillagers.network.CraftItemClientPacket;
import com.spege.helpfulvillagers.network.CraftItemServerPacket;
import com.spege.helpfulvillagers.network.CraftQueueClientPacket;
import com.spege.helpfulvillagers.network.CraftQueueServerPacket;
import com.spege.helpfulvillagers.network.CustomRecipesPacket;
import com.spege.helpfulvillagers.network.FishHookPacket;
import com.spege.helpfulvillagers.network.GUICommandPacket;
import com.spege.helpfulvillagers.network.InventoryPacket;
import com.spege.helpfulvillagers.network.ItemFrameEventPacket;
import com.spege.helpfulvillagers.network.ItemPriceClientPacket;
import com.spege.helpfulvillagers.network.ItemPriceServerPacket;
import com.spege.helpfulvillagers.network.LeaderPacket;
import com.spege.helpfulvillagers.network.MessageOptionsPacket;
import com.spege.helpfulvillagers.network.NicknamePacket;
import com.spege.helpfulvillagers.network.PlayerAccountClientPacket;
import com.spege.helpfulvillagers.network.PlayerAccountServerPacket;
import com.spege.helpfulvillagers.network.PlayerCraftMatrixResetPacket;
import com.spege.helpfulvillagers.network.PlayerInventoryPacket;
import com.spege.helpfulvillagers.network.PlayerItemStackPacket;
import com.spege.helpfulvillagers.network.PlayerMessagePacket;
import com.spege.helpfulvillagers.network.ProfessionChangePacket;
import com.spege.helpfulvillagers.network.ResetRecipesPacket;
import com.spege.helpfulvillagers.network.SaplingPacket;
import com.spege.helpfulvillagers.network.SwingPacket;
import com.spege.helpfulvillagers.network.UnlockedHallsPacket;
import com.spege.helpfulvillagers.network.VillageSyncPacket;
import com.spege.helpfulvillagers.village.HelpfulVillage;
import com.spege.helpfulvillagers.village.HelpfulVillageCollection;

import net.minecraft.entity.item.EntityItemFrame;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.CraftingManager;
import net.minecraft.item.crafting.FurnaceRecipes;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import net.minecraftforge.fml.common.event.FMLServerStoppingEvent;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import net.minecraftforge.fml.common.registry.EntityRegistry;
import net.minecraftforge.fml.relauncher.Side;

/**
 * Main mod entry point. Forward-port of "Helpful Villagers" (originally by WeaselNinja, MIT)
 * from Minecraft Forge 1.7.10 to 1.12.2.
 *
 * <p>1.12.2 wiring notes:
 * <ul>
 *   <li>{@code EntityRegistry.registerModEntity} now takes a leading {@link ResourceLocation} registry name.</li>
 *   <li>Recipe tables built from {@link CraftingManager#REGISTRY} and {@link FurnaceRecipes#getSmeltingList()}.</li>
 *   <li>Only the 14 CORE-referenced packets are registered so far (original discriminator ids kept); the
 *       remaining packets, GUI handler, renderers, world-event hooks and the command are wired in later
 *       steps - see notes/CLAUDE.md section 6.</li>
 * </ul>
 */
@Mod(modid = HelpfulVillagers.MODID, name = HelpfulVillagers.NAME, version = HelpfulVillagers.VERSION)
public class HelpfulVillagers {

    public static final String MODID = "helpfulvillagers";
    public static final String NAME = "Helpful Villagers";
    public static final String VERSION = "1.3.1-1.12.2";

    @Mod.Instance(MODID)
    public static HelpfulVillagers instance;

    @SidedProxy(
        clientSide = "com.spege.helpfulvillagers.main.ClientProxy",
        serverSide = "com.spege.helpfulvillagers.main.CommonProxy"
    )
    public static CommonProxy proxy;

    public static Logger logger;

    // --- Network ---
    public static SimpleNetworkWrapper network;

    // --- World / runtime state (shared by entities, AI, village + economy systems) ---
    public static HelpfulVillageCollection villageCollection;
    public static ArrayList<HelpfulVillage> villages = new ArrayList<HelpfulVillage>();
    public static ArrayList<EntityItemFrame> checkedFrames = new ArrayList<EntityItemFrame>();
    public static HashMap<EntityPlayer, AbstractVillager> player_guard = new HashMap<EntityPlayer, AbstractVillager>();
    public static HashMap<Integer, AbstractVillager> villager_id = new HashMap<Integer, AbstractVillager>();

    // --- Recipe tables (populated in init via initVillagerRecipes) ---
    public static ArrayList<VillagerRecipe> allCrafting = new ArrayList<VillagerRecipe>();
    public static ArrayList<VillagerRecipe> allSmelting = new ArrayList<VillagerRecipe>();
    public static ArrayList<VillagerRecipe> lumberjackRecipes = new ArrayList<VillagerRecipe>();
    public static ArrayList<VillagerRecipe> farmerRecipes = new ArrayList<VillagerRecipe>();
    public static ArrayList<VillagerRecipe> minerRecipes = new ArrayList<VillagerRecipe>();
    public static ArrayList<VillagerRecipe> fishermanRecipes = new ArrayList<VillagerRecipe>();
    public static ArrayList<VillagerRecipe> rancherRecipes = new ArrayList<VillagerRecipe>();
    public static List<IRecipe> vanillaRecipes = new ArrayList<IRecipe>();

    // --- Config (Forge Configuration; layout unchanged from the 1.7.10 original) ---
    public static Configuration config;
    /** Death broadcast verbosity: 0 = Off, 1 = On, 2 = Verbose. */
    public static int deathMessageOption = 1;
    /** Birth broadcast verbosity: 0 = Off, 1 = On, 2 = Verbose. */
    public static int birthMessageOption = 1;
    /** When true, Archers fire without consuming arrows. */
    public static boolean infiniteArrows = false;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        logger = event.getModLog();
        config = new Configuration(event.getSuggestedConfigurationFile());
        config.load();
        deathMessageOption = config.getInt("deathMessage", "general", 1, 0, 2, "0 - Off, 1 - On, 2 - Verbose");
        birthMessageOption = config.getInt("birthMessage", "general", 1, 0, 2, "0 - Off, 1 - On, 2 - Verbose");
        infiniteArrows = config.getBoolean("infiniteArrows", "archer", false,
                "Set to true to allow Archers to shoot without using arrows");
        config.save();
        proxy.preInit(event);
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        instance = this;
        for (IRecipe recipe : CraftingManager.REGISTRY) {
            vanillaRecipes.add(recipe);
        }
        this.registerNetwork();
        this.registerEntities();
        this.initVillagerRecipes();
        NetworkRegistry.INSTANCE.registerGuiHandler(this, new GuiHandler());
        proxy.registerRenderers();
        proxy.init(event);
    }

    private void registerNetwork() {
        network = NetworkRegistry.INSTANCE.newSimpleChannel("HV");
        // Discriminator ids preserved verbatim from the 1.7.10 original (all 27 packets).
        network.registerMessage(SaplingPacket.Handler.class, SaplingPacket.class, 0, Side.CLIENT);
        network.registerMessage(SwingPacket.Handler.class, SwingPacket.class, 1, Side.CLIENT);
        network.registerMessage(ProfessionChangePacket.Handler.class, ProfessionChangePacket.class, 2, Side.SERVER);
        network.registerMessage(LeaderPacket.Handler.class, LeaderPacket.class, 3, Side.CLIENT);
        network.registerMessage(GUICommandPacket.Handler.class, GUICommandPacket.class, 4, Side.SERVER);
        network.registerMessage(UnlockedHallsPacket.Handler.class, UnlockedHallsPacket.class, 5, Side.CLIENT);
        network.registerMessage(InventoryPacket.Handler.class, InventoryPacket.class, 6, Side.CLIENT);
        network.registerMessage(NicknamePacket.Handler.class, NicknamePacket.class, 7, Side.SERVER);
        network.registerMessage(PlayerMessagePacket.Handler.class, PlayerMessagePacket.class, 8, Side.CLIENT);
        network.registerMessage(MessageOptionsPacket.Handler.class, MessageOptionsPacket.class, 9, Side.CLIENT);
        network.registerMessage(ItemFrameEventPacket.Handler.class, ItemFrameEventPacket.class, 10, Side.SERVER);
        network.registerMessage(CraftItemServerPacket.Handler.class, CraftItemServerPacket.class, 11, Side.SERVER);
        network.registerMessage(CraftItemClientPacket.Handler.class, CraftItemClientPacket.class, 12, Side.CLIENT);
        network.registerMessage(CraftQueueServerPacket.Handler.class, CraftQueueServerPacket.class, 13, Side.SERVER);
        network.registerMessage(CraftQueueClientPacket.Handler.class, CraftQueueClientPacket.class, 14, Side.CLIENT);
        network.registerMessage(CustomRecipesPacket.Handler.class, CustomRecipesPacket.class, 15, Side.CLIENT);
        network.registerMessage(AddRecipePacket.Handler.class, AddRecipePacket.class, 16, Side.SERVER);
        network.registerMessage(ResetRecipesPacket.Handler.class, ResetRecipesPacket.class, 17, Side.SERVER);
        network.registerMessage(ItemPriceClientPacket.Handler.class, ItemPriceClientPacket.class, 18, Side.CLIENT);
        network.registerMessage(ItemPriceServerPacket.Handler.class, ItemPriceServerPacket.class, 19, Side.SERVER);
        network.registerMessage(VillageSyncPacket.Handler.class, VillageSyncPacket.class, 20, Side.CLIENT);
        network.registerMessage(PlayerInventoryPacket.Handler.class, PlayerInventoryPacket.class, 21, Side.SERVER);
        network.registerMessage(PlayerItemStackPacket.Handler.class, PlayerItemStackPacket.class, 22, Side.SERVER);
        network.registerMessage(PlayerCraftMatrixResetPacket.Handler.class, PlayerCraftMatrixResetPacket.class, 23, Side.SERVER);
        network.registerMessage(PlayerAccountClientPacket.Handler.class, PlayerAccountClientPacket.class, 24, Side.CLIENT);
        network.registerMessage(PlayerAccountServerPacket.Handler.class, PlayerAccountServerPacket.class, 25, Side.SERVER);
        network.registerMessage(FishHookPacket.Handler.class, FishHookPacket.class, 26, Side.CLIENT);
    }

    private void registerEntities() {
        this.registerEntity(EntityRegularVillager.class, "villager", 0);
        this.registerEntity(EntityLumberjack.class, "lumberjack", 1);
        this.registerEntity(EntityMiner.class, "miner", 2);
        this.registerEntity(EntityFarmer.class, "farmer", 3);
        this.registerEntity(EntitySoldier.class, "soldier", 4);
        this.registerEntity(EntityArcher.class, "archer", 5);
        this.registerEntity(EntityMerchant.class, "merchant", 6);
        this.registerEntity(EntityFisherman.class, "fisherman", 7);
        this.registerEntity(EntityRancher.class, "rancher", 8);
        this.registerEntity(EntityFishHookCustom.class, "fish_hook", 100);
    }

    private void registerEntity(Class<? extends net.minecraft.entity.Entity> clazz, String name, int id) {
        EntityRegistry.registerModEntity(new ResourceLocation(MODID, name), clazz, name, id, this, 50, 2, true);
    }

    @Mod.EventHandler
    public void serverStart(FMLServerStartingEvent event) {
        event.registerServerCommand(new com.spege.helpfulvillagers.command.VillagerMessagesCommand());
    }

    @Mod.EventHandler
    public void serverStop(FMLServerStoppingEvent event) {
        villages.clear();
        villageCollection = null;
    }

    private void initVillagerRecipes() {
        VillagerRecipe recipe;
        List<IRecipe> recipes = vanillaRecipes;
        block2: for (int i = 0; i < recipes.size(); ++i) {
            ItemStack currItem;
            int j;
            ItemStack outputItem = recipes.get(i).getRecipeOutput();
            if (outputItem.isEmpty()) {
                continue;
            }
            try {
                recipe = new VillagerRecipe(recipes.get(i), false);
            } catch (NullPointerException e) {
                continue;
            }
            allCrafting.add(recipe);
            for (j = 0; j < EntityLumberjack.lumberjackCraftables.length; ++j) {
                currItem = EntityLumberjack.lumberjackCraftables[j];
                if (!currItem.getItem().equals(outputItem.getItem()) || recipe.getOutput().isEmpty()
                        || lumberjackRecipes.contains(recipe)) {
                    continue;
                }
                lumberjackRecipes.add(recipe);
                break;
            }
            for (j = 0; j < EntityFarmer.farmerCraftables.length; ++j) {
                currItem = EntityFarmer.farmerCraftables[j];
                if (!currItem.getItem().equals(outputItem.getItem()) || recipe.getOutput().isEmpty()
                        || farmerRecipes.contains(recipe)) {
                    continue;
                }
                farmerRecipes.add(recipe);
                break;
            }
            for (j = 0; j < EntityMiner.minerCraftables.length; ++j) {
                currItem = EntityMiner.minerCraftables[j];
                if (!currItem.getItem().equals(outputItem.getItem()) || recipe.getOutput().isEmpty()
                        || minerRecipes.contains(recipe)) {
                    continue;
                }
                minerRecipes.add(recipe);
                break;
            }
            for (j = 0; j < EntityRancher.rancherCraftables.length; ++j) {
                currItem = EntityRancher.rancherCraftables[j];
                if (!currItem.getItem().equals(outputItem.getItem()) || recipe.getOutput().isEmpty()
                        || rancherRecipes.contains(recipe)) {
                    continue;
                }
                rancherRecipes.add(recipe);
                continue block2;
            }
        }
        block7: for (Map.Entry<ItemStack, ItemStack> entry : FurnaceRecipes.instance().getSmeltingList().entrySet()) {
            ItemStack currItem;
            int i;
            ItemStack outputItem = entry.getValue();
            recipe = new VillagerRecipe(entry.getKey(), outputItem, true);
            allSmelting.add(recipe);
            for (i = 0; i < EntityFarmer.farmerSmeltables.length; ++i) {
                currItem = EntityFarmer.farmerSmeltables[i];
                if (!currItem.getItem().equals(outputItem.getItem()) || recipe.getOutput().isEmpty()
                        || farmerRecipes.contains(recipe)) {
                    continue;
                }
                recipe = new VillagerRecipe(entry.getKey(), outputItem, true);
                farmerRecipes.add(recipe);
                break;
            }
            for (i = 0; i < EntityMiner.minerSmeltables.length; ++i) {
                currItem = EntityMiner.minerSmeltables[i];
                if (!currItem.getItem().equals(outputItem.getItem()) || recipe.getOutput().isEmpty()
                        || minerRecipes.contains(recipe)) {
                    continue;
                }
                recipe = new VillagerRecipe(entry.getKey(), outputItem, true);
                minerRecipes.add(recipe);
                break;
            }
            for (i = 0; i < EntityFisherman.fishermanSmeltables.length; ++i) {
                currItem = EntityFisherman.fishermanSmeltables[i];
                if (!currItem.getItem().equals(outputItem.getItem()) || recipe.getOutput().isEmpty()
                        || fishermanRecipes.contains(recipe)) {
                    continue;
                }
                recipe = new VillagerRecipe(entry.getKey(), outputItem, true);
                fishermanRecipes.add(recipe);
                break;
            }
            for (i = 0; i < EntityRancher.rancherSmeltables.length; ++i) {
                currItem = EntityRancher.rancherSmeltables[i];
                if (!currItem.getItem().equals(outputItem.getItem()) || recipe.getOutput().isEmpty()
                        || rancherRecipes.contains(recipe)) {
                    continue;
                }
                recipe = new VillagerRecipe(entry.getKey(), outputItem, true);
                rancherRecipes.add(recipe);
                continue block7;
            }
        }
        Collections.sort(lumberjackRecipes);
        Collections.sort(farmerRecipes);
        Collections.sort(minerRecipes);
        Collections.sort(fishermanRecipes);
        Collections.sort(rancherRecipes);
    }
}
