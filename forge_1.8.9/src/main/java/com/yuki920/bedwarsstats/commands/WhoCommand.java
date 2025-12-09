package com.yuki920.bedwarsstats.commands;

import com.yuki920.bedwarsstats.BedwarsStatsMod;
import net.minecraft.client.Minecraft;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;

public class WhoCommand extends CommandBase {

    @Override
    public String getCommandName() {
        return "who";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/who";
    }

    @Override
    public boolean canCommandSenderUseCommand(ICommandSender sender) {
        return true;
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 0; // Accessible by all players
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        // Notify our event handler that a /who command is about to be processed
        BedwarsStatsMod.playerEventHandler.prepareForWho();

        // Send the original /who command to the server
        Minecraft.getMinecraft().thePlayer.sendChatMessage("/who");
    }
}
