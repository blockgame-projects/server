package com.james090500.network.packets;

import com.james090500.BlockGameServer;
import com.james090500.Player;
import com.james090500.utils.ThreadUtil;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import org.joml.Vector3f;

public class DisconnectPacket implements BlockGamePacket {

    @Override
    public void write(Channel channel) {

    }

    @Override
    public void read(Channel channel, ByteBuf msg) {
        BlockGameServer.getLogger().info(channel.remoteAddress() + " has disconnected");
        ThreadUtil.getMainQueue().add(() -> BlockGameServer.getInstance().getPlayers().remove(channel.id()));

        BlockGameServer.getInstance().getPlayers().forEach((channelId, pl) -> {
            if(!channelId.equals(channel.id())) {
                pl.sendPacket(new EntityUpdatePacket(1, 3, 1, 100, 1));
            }
        });
    }
}
