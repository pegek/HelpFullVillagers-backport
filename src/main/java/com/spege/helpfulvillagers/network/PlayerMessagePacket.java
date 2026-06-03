package com.spege.helpfulvillagers.network;

import com.spege.helpfulvillagers.entity.AbstractVillager;
import com.spege.helpfulvillagers.enums.EnumMessage;
import com.spege.helpfulvillagers.main.HelpfulVillagers;

import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.relauncher.Side;

/** Server -> client: shows a configurable death/birth chat notification (optionally with location). */
public class PlayerMessagePacket implements IMessage {
    private String message;
    private int messageType;
    private int senderID;

    public PlayerMessagePacket() {
    }

    public PlayerMessagePacket(String message, EnumMessage messageType, int senderID) {
        this.message = message;
        this.senderID = senderID;
        switch (messageType) {
            case DEATH:
                this.messageType = 0;
                break;
            case BIRTH:
                this.messageType = 1;
                break;
            default:
                this.messageType = -1;
        }
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        ByteBufUtils.writeUTF8String(buffer, this.message);
        buffer.writeInt(this.messageType);
        buffer.writeInt(this.senderID);
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        this.message = ByteBufUtils.readUTF8String(buffer);
        this.messageType = buffer.readInt();
        this.senderID = buffer.readInt();
    }

    public static class Handler implements IMessageHandler<PlayerMessagePacket, IMessage> {
        @Override
        public IMessage onMessage(final PlayerMessagePacket packet, MessageContext ctx) {
            if (ctx.side == Side.CLIENT) {
                Minecraft.getMinecraft().addScheduledTask(new Runnable() {
                    @Override
                    public void run() {
                        String message = null;
                        try {
                            Minecraft mc = Minecraft.getMinecraft();
                            AbstractVillager sender = (AbstractVillager) mc.world.getEntityByID(packet.senderID);
                            String senderLoc = (int) sender.posX + ", " + (int) sender.posY + ", " + (int) sender.posZ;
                            switch (packet.messageType) {
                                case 0: {
                                    int option = HelpfulVillagers.deathMessageOption;
                                    if (option == 1) {
                                        message = packet.message;
                                        break;
                                    }
                                    if (option != 2) {
                                        break;
                                    }
                                    message = packet.message + " at " + senderLoc;
                                    break;
                                }
                                case 1: {
                                    int option = HelpfulVillagers.birthMessageOption;
                                    if (option == 1) {
                                        message = packet.message;
                                        break;
                                    }
                                    if (option != 2) {
                                        break;
                                    }
                                    message = packet.message + " at " + senderLoc;
                                    break;
                                }
                            }
                            if (message != null) {
                                mc.player.sendMessage(new TextComponentString(message));
                            }
                        } catch (NullPointerException e) {
                            // ignore
                        }
                    }
                });
            }
            return null;
        }
    }
}
