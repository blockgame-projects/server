package com.james090500.network.packets;

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;

public class EntityUpdatePacket extends AbstractPacket {

    private int entityId;
    private int action;
    private float x;
    private float y;
    private float z;

    public EntityUpdatePacket(int entityId, int action, float x, float y, float z) {
        this.entityId = entityId;
        this.action = action;
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public EntityUpdatePacket() {

    }

    @Override
    protected void writePayload(ByteBuf out) {
        out.writeInt(24); //id, action, x, y, z,
        out.writeInt(5);
        out.writeInt(entityId);
        out.writeInt(action);
        out.writeFloat(x);
        out.writeFloat(y);
        out.writeFloat(z);
    }

    @Override
    public void read(Channel channel, ByteBuf msg) {

    }
}
