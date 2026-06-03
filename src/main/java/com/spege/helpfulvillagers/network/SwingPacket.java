package com.spege.helpfulvillagers.network;

import com.spege.helpfulvillagers.entity.AbstractVillager;

import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.util.EnumHand;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.relauncher.Side;

/**
 * Server -> client: tells a villager to play its arm-swing animation.
 *
 * <p>1.12.2 note: the client handler schedules its world/entity mutation onto the main client thread
 * via {@code addScheduledTask} (the 1.7.10 original mutated directly on the netty thread).
 */
public class SwingPacket implements IMessage {
    private int id;

    public SwingPacket() {
    }

    public SwingPacket(int id) {
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

    public static class Handler implements IMessageHandler<SwingPacket, IMessage> {
        @Override
        public IMessage onMessage(final SwingPacket message, MessageContext ctx) {
            if (ctx.side == Side.CLIENT) {
                Minecraft.getMinecraft().addScheduledTask(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            AbstractVillager entity = (AbstractVillager) Minecraft.getMinecraft().world.getEntityByID(message.id);
                            entity.swingArm(EnumHand.MAIN_HAND);
                        } catch (NullPointerException e) {
                            // entity not present client-side; ignore
                        }
                    }
                });
            }
            return null;
        }
    }
}
