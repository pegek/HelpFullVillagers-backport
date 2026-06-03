package com.spege.helpfulvillagers.network;

import com.spege.helpfulvillagers.main.HelpfulVillagers;

import io.netty.buffer.ByteBuf;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityItemFrame;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.relauncher.Side;

/** Client -> server: registers an item frame the player interacted with for guild-hall detection. */
public class ItemFrameEventPacket implements IMessage {
    private int id;

    public ItemFrameEventPacket() {
    }

    public ItemFrameEventPacket(int id) {
        this.id = id;
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        buffer.writeInt(this.id);
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        this.id = buffer.readInt();
    }

    public static class Handler implements IMessageHandler<ItemFrameEventPacket, IMessage> {
        @Override
        public IMessage onMessage(final ItemFrameEventPacket message, MessageContext ctx) {
            if (ctx.side == Side.SERVER) {
                final EntityPlayerMP player = ctx.getServerHandler().player;
                player.mcServer.addScheduledTask(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            Entity entity = player.world.getEntityByID(message.id);
                            if (entity instanceof EntityItemFrame) {
                                EntityItemFrame frame = (EntityItemFrame) entity;
                                if (!HelpfulVillagers.checkedFrames.contains(frame)) {
                                    HelpfulVillagers.checkedFrames.add(frame);
                                }
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
