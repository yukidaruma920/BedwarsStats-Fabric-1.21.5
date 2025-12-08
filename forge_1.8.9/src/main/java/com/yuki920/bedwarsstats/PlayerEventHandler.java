package com.yuki920.bedwarsstats;

import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.IChatComponent;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.client.event.RenderPlayerListEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.network.FMLNetworkEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PlayerEventHandler {

    private boolean whoCommandTriggered = false;
    private boolean isDisplayOverridden = false;
    private boolean isInBedwarsGame = false;
    private final Map<UUID, IChatComponent> originalDisplayNames = new ConcurrentHashMap<>();
    // Pattern to extract the player name, ignoring team prefixes, colors, etc.
    private static final Pattern NAME_PATTERN = Pattern.compile("^(?:§[0-9a-fk-or])*(?:\\[[A-Z+]+] )?(?:§[0-9a-fk-or])*(\\w+).*");

    public void prepareForWho() {
        if (isInBedwarsGame) {
            BedwarsStatsMod.LOGGER.info("'/who' command detected. Clearing previous stats and preparing to fetch new ones.");
            whoCommandTriggered = true;
            isDisplayOverridden = true; // Mark that we are now overriding the display
            resetDisplayNames(); // Clear any previous overrides
            HypixelApiHandler.clearPlayerData();
        }
    }

    @SubscribeEvent
    public void onChat(ClientChatReceivedEvent event) {
        String text = event.message.getUnformattedText();

        if (text.startsWith("{\"server\":\"")) {
            String serverName = text.split("\"")[3];
            boolean wasInGame = isInBedwarsGame;
            isInBedwarsGame = serverName.startsWith("mini");
            if (wasInGame && !isInBedwarsGame) {
                reset();
            }
            return;
        }

        if (whoCommandTriggered && text.startsWith("ONLINE: ")) {
            String playersPart = text.substring("ONLINE: ".length());
            String[] playerNames = playersPart.split(", ");
            for (String name : playerNames) {
                HypixelApiHandler.processPlayer(name.trim());
            }
            whoCommandTriggered = false;
        }
    }

    @SubscribeEvent
    public void onRenderPlayerList(RenderPlayerListEvent.Pre event) {
        if (!isInBedwarsGame || !isDisplayOverridden) return;

        for (NetworkPlayerInfo playerInfo : Minecraft.getMinecraft().getNetHandler().getPlayerInfoMap()) {
            IChatComponent displayNameComponent = playerInfo.getDisplayName();
            String currentDisplayName = (displayNameComponent != null) ? displayNameComponent.getFormattedText() : playerInfo.getGameProfile().getName();

            Matcher matcher = NAME_PATTERN.matcher(currentDisplayName);
            String username = playerInfo.getGameProfile().getName(); // Fallback
            if (matcher.matches()) {
                username = matcher.group(1);
            }

            JsonObject playerData = HypixelApiHandler.getPlayerData(username);

            if (playerData != null) {
                originalDisplayNames.putIfAbsent(playerInfo.getGameProfile().getId(), displayNameComponent);

                String teamPrefix = currentDisplayName.substring(0, currentDisplayName.indexOf(username));
                String statsString = formatStatsForTab(playerData);

                playerInfo.setDisplayName(new ChatComponentText(teamPrefix + username + statsString));
            }
        }
    }

    @SubscribeEvent
    public void onServerJoin(FMLNetworkEvent.ClientConnectedToServerEvent event) {
        reset();
    }

    @SubscribeEvent
    public void onServerLeave(FMLNetworkEvent.ClientDisconnectionFromServerEvent event) {
        reset();
    }

    private void reset() {
        BedwarsStatsMod.LOGGER.info("Resetting Bedwars stats display state.");
        whoCommandTriggered = false;
        isDisplayOverridden = false;
        isInBedwarsGame = false;
        resetDisplayNames();
        HypixelApiHandler.clearPlayerData();
    }

    private void resetDisplayNames() {
        if (Minecraft.getMinecraft().getNetHandler() == null) return;
        try {
            for (NetworkPlayerInfo playerInfo : Minecraft.getMinecraft().getNetHandler().getPlayerInfoMap()) {
                UUID uuid = playerInfo.getGameProfile().getId();
                if (originalDisplayNames.containsKey(uuid)) {
                    playerInfo.setDisplayName(originalDisplayNames.get(uuid));
                }
            }
        } catch (Exception e) {
            // Can sometimes throw ConcurrentModificationException, ignore it.
        }
        originalDisplayNames.clear();
    }

    private String formatStatsForTab(JsonObject playerData) {
        if (playerData.has("nicked") && playerData.get("nicked").getAsBoolean()) {
            return " §7(Nicked)";
        }

        if (!playerData.has("stats") || playerData.get("stats").isJsonNull() || !playerData.getAsJsonObject("stats").has("Bedwars") || !playerData.has("achievements")) {
            return ""; // No stats, don't append anything
        }

        JsonObject bedwars = playerData.getAsJsonObject("stats").getAsJsonObject("Bedwars");
        int stars = playerData.getAsJsonObject("achievements").has("bedwars_level") ? playerData.getAsJsonObject("achievements").get("bedwars_level").getAsInt() : 0;

        int finalKills = bedwars.has("final_kills_bedwars") ? bedwars.get("final_kills_bedwars").getAsInt() : 0;
        int finalDeaths = bedwars.has("final_deaths_bedwars") ? bedwars.get("final_deaths_bedwars").getAsInt() : 0;
        int wins = bedwars.has("wins_bedwars") ? bedwars.get("wins_bedwars").getAsInt() : 0;
        int losses = bedwars.has("losses_bedwars") ? bedwars.get("losses_bedwars").getAsInt() : 0;

        double fkdr = (finalDeaths == 0) ? finalKills : (double) finalKills / finalDeaths;
        double wlr = (losses == 0) ? wins : (double) wins / losses;

        String prestige = PrestigeFormatter.formatPrestige(stars); // This returns "[100✫]"
        String fkdrStr = String.format("%.2f", fkdr);
        String wlrStr = String.format("%.2f", wlr);

        return String.format(" %s | %s | %s", prestige, fkdrStr, wlrStr);
    }
}
