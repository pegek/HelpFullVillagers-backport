package com.spege.helpfulvillagers.command;

import java.util.Arrays;
import java.util.List;

import com.spege.helpfulvillagers.enums.EnumMessage;
import com.spege.helpfulvillagers.main.HelpfulVillagers;
import com.spege.helpfulvillagers.network.MessageOptionsPacket;

import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;

/**
 * /villagermessages <death|birth|all> <on|off|verbose>
 *
 * <p>1.12.2 migration: CommandBase now uses {@code execute(MinecraftServer, ICommandSender, String[])}
 * and throws {@link CommandException}; {@code TextComponentString} + {@link TextFormatting};
 * {@code sendTo} takes an {@link EntityPlayerMP}.
 */
public class VillagerMessagesCommand extends CommandBase {

    @Override
    public String getName() {
        return "villagermessages";
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/villagermessages <death|birth|all> <on|off|verbose>";
    }

    @Override
    public boolean checkPermission(MinecraftServer server, ICommandSender sender) {
        return sender instanceof EntityPlayer;
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        TextComponentString errorText = new TextComponentString(this.getUsage(sender));
        errorText.getStyle().setColor(TextFormatting.RED);
        if (args.length != 2 || !(sender instanceof EntityPlayerMP)) {
            sender.sendMessage(errorText);
            return;
        }
        EntityPlayerMP player = (EntityPlayerMP) sender;
        String type = args[0];
        String level = args[1];
        int option;
        if (level.equals("on")) {
            option = 1;
        } else if (level.equals("off")) {
            option = 0;
        } else if (level.equals("verbose")) {
            option = 2;
        } else {
            sender.sendMessage(errorText);
            return;
        }
        if (type.equals("birth")) {
            HelpfulVillagers.network.sendTo(new MessageOptionsPacket(EnumMessage.BIRTH, option), player);
            sender.sendMessage(new TextComponentString("Birth messages set: " + level));
        } else if (type.equals("death")) {
            HelpfulVillagers.network.sendTo(new MessageOptionsPacket(EnumMessage.DEATH, option), player);
            sender.sendMessage(new TextComponentString("Death messages set: " + level));
        } else if (type.equals("all")) {
            HelpfulVillagers.network.sendTo(new MessageOptionsPacket(EnumMessage.BIRTH, option), player);
            HelpfulVillagers.network.sendTo(new MessageOptionsPacket(EnumMessage.DEATH, option), player);
            sender.sendMessage(new TextComponentString("All messages set: " + level));
        } else {
            sender.sendMessage(errorText);
        }
    }

    @Override
    public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args,
            net.minecraft.util.math.BlockPos targetPos) {
        if (args.length == 1) {
            return getListOfStringsMatchingLastWord(args, "death", "birth", "all");
        }
        if (args.length == 2) {
            return getListOfStringsMatchingLastWord(args, "on", "off", "verbose");
        }
        return Arrays.asList();
    }
}
