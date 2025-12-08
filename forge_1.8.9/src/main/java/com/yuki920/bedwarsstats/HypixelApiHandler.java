package com.yuki920.bedwarsstats;

import com.yuki920.bedwarsstats.config.BedwarsStatsConfig;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class HypixelApiHandler {
    private static final Gson GSON = new Gson();
    private static final Map<String, JsonObject> playerData = new ConcurrentHashMap<>();
    private static final String HYPIXEL_API_URL = "https://api.hypixel.net/player?uuid=";
    private static final String HYPIXEL_KEY_API_URL = "https://api.hypixel.net/key";

    public static void checkApiKeyValidity() {
        CompletableFuture.runAsync(() -> {
            try {
                String apiKey = BedwarsStatsConfig.getApiKey();
                if (apiKey == null || apiKey.isEmpty()) return;
                String response = sendHttpRequest(HYPIXEL_KEY_API_URL, apiKey);
                if (response == null) return;
                JsonObject json = GSON.fromJson(response, JsonObject.class);
                if (json != null && !json.get("success").getAsBoolean()) {
                    sendMessageToPlayer(EnumChatFormatting.RED + "[BedwarsStats] Your Hypixel API key appears to be invalid or expired!");
                    sendMessageToPlayer(EnumChatFormatting.YELLOW + "Run " + EnumChatFormatting.GREEN + "/bwm settings apikey <key> " + EnumChatFormatting.YELLOW + "to set a new one.");
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    public static void clearPlayerData() {
        playerData.clear();
    }

    public static JsonObject getPlayerData(String username) {
        return playerData.get(username.toLowerCase());
    }

    public static void processPlayer(String username) {
        BedwarsStatsConfig.BedwarsMode mode = BedwarsStatsConfig.BedwarsMode.fromString(BedwarsStatsConfig.getBedwarsMode());
        processPlayer(username, mode);
    }

    public static void processPlayer(String username, BedwarsStatsConfig.BedwarsMode mode) {
        CompletableFuture.runAsync(() -> {
            try {
                String myNick = BedwarsStatsConfig.getMyNick();
                if (myNick != null && !myNick.isEmpty() && myNick.equalsIgnoreCase(username)) {
                    Minecraft client = Minecraft.getMinecraft();
                    if (client.thePlayer != null) {
                        String uuid = client.thePlayer.getUniqueID().toString();
                        fetchAndStoreStats(uuid, username, mode);
                    }
                } else {
                    String mojangUrl = "https://api.mojang.com/users/profiles/minecraft/" + username;
                    String mojangResponse = sendHttpRequest(mojangUrl, null);
                    if (mojangResponse == null) {
                        markPlayerAsNicked(username);
                        return;
                    }
                    JsonObject mojangJson = GSON.fromJson(mojangResponse, JsonObject.class);
                    if (mojangJson == null || !mojangJson.has("id")) {
                        markPlayerAsNicked(username);
                        return;
                    }
                    String uuid = mojangJson.get("id").getAsString();
                    fetchAndStoreStats(uuid, username, mode);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    private static void fetchAndStoreStats(String uuid, String displayUsername, BedwarsStatsConfig.BedwarsMode mode) throws Exception {
        String apiKey = BedwarsStatsConfig.getApiKey();
        if (apiKey == null || apiKey.isEmpty()) {
            sendMessageToPlayer(EnumChatFormatting.RED + "Hypixel API Key not set!");
            return;
        }
        String hypixelUrl = HYPIXEL_API_URL + uuid;
        String hypixelResponse = sendHttpRequest(hypixelUrl, apiKey);
        if (hypixelResponse == null) {
            markPlayerAsNicked(displayUsername); // Assume nicked if API fails
            return;
        }
        JsonObject hypixelJson = GSON.fromJson(hypixelResponse, JsonObject.class);

        if (hypixelJson != null && !hypixelJson.get("success").getAsBoolean()) {
            if (hypixelJson.has("cause") && hypixelJson.get("cause").getAsString().equals("Invalid API key")) {
                sendMessageToPlayer(EnumChatFormatting.RED + "Your Hypixel API key is invalid!");
            }
            markPlayerAsNicked(displayUsername); // Assume nicked on API error
            return;
        }

        if (hypixelJson == null || !hypixelJson.has("player") || hypixelJson.get("player").isJsonNull()) {
            markPlayerAsNicked(displayUsername);
            return;
        }
        JsonObject player = hypixelJson.getAsJsonObject("player");

        // Ensure displayname is consistent, as API might return a different case
        player.addProperty("displayname", displayUsername);

        playerData.put(displayUsername.toLowerCase(), player);
    }

    private static void markPlayerAsNicked(String username) {
        JsonObject nickedPlayer = new JsonObject();
        nickedPlayer.addProperty("displayname", username);
        nickedPlayer.addProperty("nicked", true);
        playerData.put(username.toLowerCase(), nickedPlayer);
    }

    private static String formatStats(JsonObject player, BedwarsStatsConfig.BedwarsMode mode) {
        String prefix = mode.getApiPrefix();
        String username = player.get("displayname").getAsString();
        String rankColor = getRankColor(player);
        String rankPrefixText = BedwarsStatsConfig.getShowRankPrefix() ? getRankPrefix(player) : "";

        if (!player.has("stats") || player.get("stats").isJsonNull() || !player.getAsJsonObject("stats").has("Bedwars")) {
            return rankPrefixText + rankColor + username + EnumChatFormatting.GRAY + ": No Bedwars stats found.";
        }

        JsonObject bedwars = player.getAsJsonObject("stats").getAsJsonObject("Bedwars");
        int stars = getInt(player.getAsJsonObject("achievements"), "bedwars_level");
        int wins = getInt(bedwars, prefix + "wins_bedwars");
        int losses = getInt(bedwars, prefix + "losses_bedwars");
        int finalKills = getInt(bedwars, prefix + "final_kills_bedwars");
        int finalDeaths = getInt(bedwars, prefix + "final_deaths_bedwars");
        int winstreak = getInt(bedwars, prefix + "winstreak");

        double wlr = (losses == 0) ? wins : (double) wins / losses;
        double fkdr = (finalDeaths == 0) ? finalKills : (double) finalKills / finalDeaths;

        String prestige = PrestigeFormatter.formatPrestige(stars);

        StringBuilder statsBuilder = new StringBuilder();
        String[] displayOrder = BedwarsStatsConfig.getDisplayOrder().split(",");
        for (int i = 0; i < displayOrder.length; i++) {
            try {
                BedwarsStatsConfig.Stat stat = BedwarsStatsConfig.Stat.valueOf(displayOrder[i].trim().toUpperCase());
                switch (stat) {
                    case WINS: statsBuilder.append(String.format("Wins %s%s%s", getWinsColor(wins), formatNumber(wins), EnumChatFormatting.RESET)); break;
                    case FINALS: statsBuilder.append(String.format("Finals %s%s%s", getFinalsColor(finalKills), formatNumber(finalKills), EnumChatFormatting.RESET)); break;
                    case FKDR: statsBuilder.append(String.format("FKDR %s%.2f%s", getFkdrColor(fkdr), fkdr, EnumChatFormatting.RESET)); break;
                    case WLR: statsBuilder.append(String.format("WLR %s%.2f%s", getWlrColor(wlr), wlr, EnumChatFormatting.RESET)); break;
                    case WS: statsBuilder.append(String.format("WS %s%d%s", getWsColor(winstreak), winstreak, EnumChatFormatting.RESET)); break;
                }
                if (i < displayOrder.length - 1) statsBuilder.append(" | ");
            } catch (IllegalArgumentException e) { /* Ignore invalid stat names */ }
        }

        return String.format("%s %s%s%s%s %s[%s]%s: %s",
                prestige, rankPrefixText, rankColor, username, EnumChatFormatting.RESET, EnumChatFormatting.GRAY, mode.getDisplayName(), EnumChatFormatting.RESET, statsBuilder.toString());
    }

    private static String getRankColor(JsonObject player) {
        String rank = player.has("rank") && !player.get("rank").getAsString().equals("NORMAL") ? player.get("rank").getAsString() : null;
        if (rank != null) {
            switch (rank) {
                case "YOUTUBER": return "§c";
                case "STAFF": return "§c";
            }
        }
        String monthlyPackageRank = player.has("monthlyPackageRank") && !player.get("monthlyPackageRank").getAsString().equals("NONE") ? player.get("monthlyPackageRank").getAsString() : null;
        String newPackageRank = player.has("newPackageRank") && !player.get("newPackageRank").getAsString().equals("NONE") ? player.get("newPackageRank").getAsString() : null;
        String displayRank = monthlyPackageRank != null ? monthlyPackageRank : newPackageRank;
        if (displayRank == null) return "§7";
        switch (displayRank) {
            case "VIP": case "VIP_PLUS": return "§a";
            case "MVP": case "MVP_PLUS": return "§b";
            case "SUPERSTAR": return "§6";
            default: return "§7";
        }
    }

    private static String getRankPrefix(JsonObject player) {
        String rank = player.has("rank") && !player.get("rank").getAsString().equals("NORMAL") ? player.get("rank").getAsString() : null;
        String monthlyPackageRank = player.has("monthlyPackageRank") && !player.get("monthlyPackageRank").getAsString().equals("NONE") ? player.get("monthlyPackageRank").getAsString() : null;
        String newPackageRank = player.has("newPackageRank") && !player.get("newPackageRank").getAsString().equals("NONE") ? player.get("newPackageRank").getAsString() : null;
        String rankPlusColorStr = player.has("rankPlusColor") ? player.get("rankPlusColor").getAsString() : null;

        if (rank != null) {
            switch (rank) {
                case "YOUTUBER": return "§c[§fYOUTUBE§c] ";
                case "STAFF": return "§c[§6ዞ§c] ";
            }
        }
        String displayRank = monthlyPackageRank != null ? monthlyPackageRank : newPackageRank;
        if (displayRank == null) return ""; // No rank, no prefix
        String plusColor = "§c";
        if (rankPlusColorStr != null) {
            try {
                plusColor = EnumChatFormatting.valueOf(rankPlusColorStr).toString();
            } catch (IllegalArgumentException e) {
                plusColor = "§c";
            }
        }
        switch (displayRank) {
            case "VIP": return getRankColor(player) + "[VIP] ";
            case "VIP_PLUS": return getRankColor(player) + "[VIP" + "§6" + "+" + getRankColor(player) + "] ";
            case "MVP": return getRankColor(player) + "[MVP] ";
            case "MVP_PLUS": return getRankColor(player) + "[MVP" + plusColor + "+" + getRankColor(player) + "] ";
            case "SUPERSTAR": return getRankColor(player) + "[MVP" + plusColor + "++" + getRankColor(player) + "] ";
            default: return "";
        }
    }

    private static String formatNumber(int number) { return String.format("%,d", number); }
    private static String getFkdrColor(double fkdr) { if (fkdr >= 20) return "§5"; if (fkdr >= 15) return "§d"; if (fkdr >= 10) return "§4"; if (fkdr >= 8)  return "§c"; if (fkdr >= 6)  return "§6"; if (fkdr >= 4)  return "§e"; if (fkdr >= 2)  return "§2"; if (fkdr >= 1)  return "§a"; if (fkdr >= 0.5) return "§f"; return "§7"; }
    private static String getWlrColor(double wlr) { if (wlr >= 10) return "§5"; if (wlr >= 8)  return "§d"; if (wlr >= 6)  return "§4"; if (wlr >= 5)  return "§c"; if (wlr >= 4)  return "§6"; if (wlr >= 3)  return "§e"; if (wlr >= 2)  return "§2"; if (wlr >= 1)  return "§a"; if (wlr >= 0.5) return "§f"; return "§7"; }
    private static String getWinsColor(int wins) { if (wins >= 50000) return "§5"; if (wins >= 25000) return "§d"; if (wins >= 10000) return "§4"; if (wins >= 5000) return "§c"; if (wins >= 2500) return "§6"; if (wins >= 1000) return "§e"; if (wins >= 500) return "§2"; if (wins >= 250) return "§a"; if (wins >= 50) return "§f"; return "§7"; }
    private static String getFinalsColor(int finals) { if (finals >= 100000) return "§5"; if (finals >= 50000) return "§d"; if (finals >= 25000) return "§4"; if (finals >= 10000) return "§c"; if (finals >= 5000) return "§6"; if (finals >= 2500) return "§e"; if (finals >= 1000) return "§2"; if (finals >= 500) return "§a"; if (finals >= 100) return "§f"; return "§7"; }
    private static String getWsColor(int ws) { if (ws >= 300) return "§5"; if (ws >= 200) return "§d"; if (ws >= 100) return "§4"; if (ws >= 60) return "§c"; if (ws >= 40) return "§6"; if (ws >= 20) return "§e"; if (ws >= 10) return "§2"; if (ws >= 5) return "§a"; if (ws >= 2) return "§f"; return "§7"; }
    private static int getInt(JsonObject obj, String memberName) { return obj.has(memberName) ? obj.get(memberName).getAsInt() : 0; }

    private static String sendHttpRequest(String urlString, String apiKey) throws Exception {
        URL url = new URL(urlString);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        if (apiKey != null) connection.setRequestProperty("API-Key", apiKey);
        if (connection.getResponseCode() == 200) {
            BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            String inputLine;
            StringBuilder content = new StringBuilder();
            while ((inputLine = in.readLine()) != null) content.append(inputLine);
            in.close();
            return content.toString();
        }
        return null;
    }

    private static void sendMessageToPlayer(String message) {
        Minecraft.getMinecraft().addScheduledTask(() -> {
            if (Minecraft.getMinecraft().thePlayer != null) {
                Minecraft.getMinecraft().thePlayer.addChatMessage(new ChatComponentText(message));
            }
        });
    }
}
