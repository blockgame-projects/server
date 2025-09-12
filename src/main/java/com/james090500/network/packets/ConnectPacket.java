package com.james090500.network.packets;

import com.james090500.BlockGameServer;
import com.james090500.Player;
import com.james090500.utils.ThreadUtil;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import org.joml.Vector3f;

public class ConnectPacket implements BlockGamePacket {

    @Override
    public void write(Channel channel) {

    }

    @Override
    public void read(Channel channel, ByteBuf msg) {
        BlockGameServer.getLogger().info(channel.remoteAddress() + " has connected");

        // Load Player
        Player player = new Player(channel);
        player.setForceUpdate(true);
        player.setPosition(new Vector3f(0, 100, 0));
        ThreadUtil.getMainQueue().add(() -> BlockGameServer.getInstance().getPlayers().put(channel.id(), player));

        BlockGameServer.getInstance().getPlayers().forEach((channelId, pl) -> {
            if(!channelId.equals(channel.id())) {
                pl.sendPacket(new EntityUpdatePacket(1, 1, 1, 100, 1));
            }
        });
    }
}
