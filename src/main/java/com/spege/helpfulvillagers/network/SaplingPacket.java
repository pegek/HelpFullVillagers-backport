package com.spege.helpfulvillagers.network;

import com.spege.helpfulvillagers.entity.EntityLumberjack;

import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.relauncher.Side;

/** Server -> client: syncs a lumberjack's {@code shouldPlant} flag. */
public class SaplingPacket implements IMessage {
    private int id;
    private boolean shouldPlant;

    public SaplingPacket() {
    }

    public SaplingPacket(int id, boolean shouldPlant) {
        this.id = id;
        this.shouldPlant = shouldPlant;
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        buffer.writeInt(this.id);
        buffer.writeBoolean(this.shouldPlant);
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        this.id = buffer.readInt();
        this.shouldPlant = buffer.readBoolean();
    }

    public static class Handler implements IMessageHandler<SaplingPacket, IMessage> {
        @Override
        public IMessage onMessage(final SaplingPacket message, MessageContext ctx) {
            if (ctx.side == Side.CLIENT) {
                Minecraft.getMinecraft().addScheduledTask(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            EntityLumberjack entity = (EntityLumberjack) Minecraft.getMinecraft().world.getEntityByID(message.id);
                            entity.shouldPlant = message.shouldPlant;
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
