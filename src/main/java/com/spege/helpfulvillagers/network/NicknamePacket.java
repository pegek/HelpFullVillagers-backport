package com.spege.helpfulvillagers.network;

import com.spege.helpfulvillagers.entity.AbstractVillager;

import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.relauncher.Side;

/** Client -> server: sets a villager's custom nickname. */
public class NicknamePacket implements IMessage {
    private int id;
    private String name;

    public NicknamePacket() {
    }

    public NicknamePacket(int id, String name) {
        this.id = id;
        this.name = name;
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        buffer.writeInt(this.id);
        ByteBufUtils.writeUTF8String(buffer, this.name);
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        this.id = buffer.readInt();
        this.name = ByteBufUtils.readUTF8String(buffer);
    }

    public static class Handler implements IMessageHandler<NicknamePacket, IMessage> {
        @Override
        public IMessage onMessage(final NicknamePacket message, MessageContext ctx) {
            if (ctx.side == Side.SERVER) {
                final EntityPlayerMP player = ctx.getServerHandler().player;
                player.mcServer.addScheduledTask(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            AbstractVillager entity = (AbstractVillager) player.world.getEntityByID(message.id);
                            entity.setCustomNameTag(message.name);
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
