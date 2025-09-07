package com.james090500.network;

import com.james090500.BlockGameServer;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.nio.NioServerSocketChannel;

public class NettyHandler {

    private final int port;

    public NettyHandler(int port) {
        this.port = port;
    }

    public void run() {
        EventLoopGroup bossGroup = new MultiThreadIoEventLoopGroup(NioIoHandler.newFactory());
        EventLoopGroup workerGroup = new MultiThreadIoEventLoopGroup(NioIoHandler.newFactory());

        ServerBootstrap bootstrap = new ServerBootstrap()
                .group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel.class)
                .childHandler(new NettyInitializer());

        ChannelFuture bindFuture = bootstrap.bind(port);
        bindFuture.addListener((ChannelFuture future) -> {
            if (future.isSuccess()) {
                BlockGameServer.getLogger().info("Server started on port " + port);
                future.channel().closeFuture().addListener(cf -> {
                    BlockGameServer.getLogger().info("Server shutting down");
                });
            } else {
                BlockGameServer.getLogger().severe(future.cause().getLocalizedMessage());
                future.cause().printStackTrace();
            }
        });
    }
}
