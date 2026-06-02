package com.spege.helpfulvillagers.main;

import org.apache.logging.log4j.Logger;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import net.minecraftforge.fml.common.event.FMLServerStoppingEvent;

/**
 * Main mod entry point. Forward-port of "Helpful Villagers" (originally by WeaselNinja, MIT)
 * from Minecraft Forge 1.7.10 to 1.12.2.
 *
 * <p>This is the skeleton stage (step 0): the mod loads with no content. Subsystems
 * (config, network, entities, AI, GUI, village/economy, crafting, fish hook, renderers,
 * command) are wired in over subsequent migration steps — see notes/claude.md section 6.
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

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        logger = event.getModLog();
        // TODO step 2: load Forge Configuration (death/birth message verbosity, infiniteArrows).
        proxy.preInit(event);
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        // TODO step 3: register network channel + packets.
        // TODO step 4: register entities. TODO step 7: build villager recipe tables.
        // TODO step 9: register GUI handler.
        proxy.init(event);
    }

    @Mod.EventHandler
    public void serverStart(FMLServerStartingEvent event) {
        // TODO step 12: register /villagermessages command.
    }

    @Mod.EventHandler
    public void serverStop(FMLServerStoppingEvent event) {
        // TODO step 8: clear village collection state on server stop.
    }
}
