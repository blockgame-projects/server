package com.james090500;

import com.james090500.network.packets.BlockGamePacket;
import com.james090500.network.packets.ChunkPacket;
import com.james090500.world.World;
import io.netty.channel.Channel;
import lombok.Getter;
import lombok.Setter;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

public class Player {

    @Getter
    private final Channel channel;

    @Getter @Setter
    private int lastChunkX;
    @Getter @Setter
    private int lastChunkZ;

    @Getter @Setter
    public Vector3f position;

    @Getter
    private List<World.ChunkPos> loadedChunks = new ArrayList<>();

    public Player(Channel channel) {
        this.channel = channel;
    }

    public void sendPacket(BlockGamePacket blockGamePacket) {
        blockGamePacket.write(channel);
    }
}
