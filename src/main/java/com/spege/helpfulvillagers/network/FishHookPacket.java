package com.spege.helpfulvillagers.network;

import com.spege.helpfulvillagers.entity.EntityFishHookCustom;
import com.spege.helpfulvillagers.entity.EntityFisherman;

import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.relauncher.Side;

/** Server -> client: spawns or removes a fisherman's custom fishing hook entity. */
public class FishHookPacket implements IMessage {
    private int id;
    private boolean dead;
    private int x;
    private int y;
    private int z;

    public FishHookPacket() {
    }

    public FishHookPacket(int id, boolean dead, int x, int y, int z) {
        this.id = id;
        this.dead = dead;
        this.x = x;
        this.y = y;
        this.z = z;
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        buffer.writeInt(this.id);
        buffer.writeBoolean(this.dead);
        buffer.writeInt(this.x);
        buffer.writeInt(this.y);
        buffer.writeInt(this.z);
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        this.id = buffer.readInt();
        this.dead = buffer.readBoolean();
        this.x = buffer.readInt();
        this.y = buffer.readInt();
        this.z = buffer.readInt();
    }

    public static class Handler implements IMessageHandler<FishHookPacket, IMessage> {
        @Override
        public IMessage onMessage(final FishHookPacket message, MessageContext ctx) {
            if (ctx.side == Side.CLIENT) {
                Minecraft.getMinecraft().addScheduledTask(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            EntityFisherman fisherman = (EntityFisherman) Minecraft.getMinecraft().world.getEntityByID(message.id);
                            if (message.dead) {
                                fisherman.fishEntity.setDead();
                                fisherman.fishEntity = null;
                            } else {
                                fisherman.fishEntity = new EntityFishHookCustom(fisherman.world, message.x, message.y, message.z, fisherman);
                                fisherman.world.spawnEntity(fisherman.fishEntity);
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
