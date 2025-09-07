package com.james090500.network.packets;

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;

public class ChunkPacket extends AbstractPacket {
    private int chunkX;
    private int chunkZ;
    private byte[] chunkData; // immutable reference (beware large arrays)

    public ChunkPacket() {}

    public ChunkPacket(int chunkX, int chunkZ, byte[] chunkData) {
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        this.chunkData = chunkData;
    }

    @Override
    protected void writePayload(ByteBuf out) {
        out.writeInt(12 + chunkData.length); //id, x, z, chunkData
        out.writeInt(3);
        out.writeInt(chunkX); //X
        out.writeInt(chunkZ); //Z
        out.writeBytes(chunkData); //Data
    }

    @Override
    public void read(Channel channel, ByteBuf msg) {

    }
}
