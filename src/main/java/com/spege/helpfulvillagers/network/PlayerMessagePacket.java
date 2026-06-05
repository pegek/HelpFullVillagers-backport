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
    private boolean hasEntity;
    private int senderID;
    private int[] coords = new int[3];

    public PlayerMessagePacket() {
    }

    public PlayerMessagePacket(String message, EnumMessage messageType, int senderID) {
        this.message = message;
        this.senderID = senderID;
        this.hasEntity = true;
        switch (messageType) {
            case DEATH:
                this.messageType = 0;
                break;
            case BIRTH:
                this.messageType = 1;
                break;
            case CONSTRUCTION:
                this.messageType = 2;
                break;
            default:
                this.messageType = -1;
        }
    }

    public PlayerMessagePacket(String message, EnumMessage messageType, net.minecraft.util.math.BlockPos pos) {
        this.message = message;
        this.coords[0] = pos.getX();
        this.coords[1] = pos.getY();
        this.coords[2] = pos.getZ();
        this.hasEntity = false;
        switch (messageType) {
            case DEATH:
                this.messageType = 0;
                break;
            case BIRTH:
                this.messageType = 1;
                break;
            case CONSTRUCTION:
                this.messageType = 2;
                break;
            default:
                this.messageType = -1;
        }
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        ByteBufUtils.writeUTF8String(buffer, this.message);
        buffer.writeInt(this.messageType);
        buffer.writeBoolean(this.hasEntity);
        if (this.hasEntity) {
            buffer.writeInt(this.senderID);
        } else {
            buffer.writeInt(this.coords[0]);
            buffer.writeInt(this.coords[1]);
            buffer.writeInt(this.coords[2]);
        }
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        this.message = ByteBufUtils.readUTF8String(buffer);
        this.messageType = buffer.readInt();
        this.hasEntity = buffer.readBoolean();
        if (this.hasEntity) {
            this.senderID = buffer.readInt();
        } else {
            this.coords[0] = buffer.readInt();
            this.coords[1] = buffer.readInt();
            this.coords[2] = buffer.readInt();
        }
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
                            String senderLoc;
                            if (packet.hasEntity) {
                                AbstractVillager sender = (AbstractVillager) mc.world.getEntityByID(packet.senderID);
                                senderLoc = (int) sender.posX + ", " + (int) sender.posY + ", " + (int) sender.posZ;
                            } else {
                                senderLoc = packet.coords[0] + ", " + packet.coords[1] + ", " + packet.coords[2];
                            }
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
                                case 2: {
                                    int option = HelpfulVillagers.constructionMessageOption;
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
