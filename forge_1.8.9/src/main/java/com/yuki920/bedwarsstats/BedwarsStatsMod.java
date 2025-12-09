package com.yuki920.bedwarsstats;

import com.yuki920.bedwarsstats.commands.BwmCommand;
import net.minecraftforge.client.ClientCommandHandler;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import com.yuki920.bedwarsstats.config.BedwarsStatsConfig;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.yuki920.bedwarsstats.commands.WhoCommand;

@Mod(modid = BedwarsStatsMod.MOD_ID, version = BedwarsStatsMod.VERSION, name = BedwarsStatsMod.NAME, guiFactory = "com.yuki920.bedwarsstats.config.BedwarsStatsGuiFactory")
public class BedwarsStatsMod {
    public static final String MOD_ID = "bedwarsstats";
    public static final String VERSION = "1.4.2";
    public static final String NAME = "Bedwars Stats";

    public static final Logger LOGGER = LogManager.getLogger(MOD_ID);

    public static PlayerEventHandler playerEventHandler;

    @EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        BedwarsStatsConfig.syncConfig(event.getSuggestedConfigurationFile());
    }

    @EventHandler
    public void init(FMLInitializationEvent event) {
        LOGGER.info("Bedwars Stats Mod Initializing...");

        // Register event handlers
        playerEventHandler = new PlayerEventHandler();
        MinecraftForge.EVENT_BUS.register(playerEventHandler);

        // Register commands
        ClientCommandHandler.instance.registerCommand(new BwmCommand());
        ClientCommandHandler.instance.registerCommand(new WhoCommand());
    }
}
