package com.james090500;

import com.james090500.command.CommandHandler;
import com.james090500.network.NettyHandler;
import com.james090500.utils.GameLogger;
import com.james090500.utils.ThreadUtil;
import com.james090500.world.World;
import io.netty.channel.Channel;
import io.netty.channel.ChannelId;
import lombok.Getter;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

@Getter
public class BlockGameServer {

    private final ScheduledExecutorService tickExec = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "game-tick");
        t.setDaemon(false);
        return t;
    });
    private final AtomicBoolean running = new AtomicBoolean(false);

    private final HashMap<ChannelId, Player> players = new HashMap<>();

    @Getter
    private static BlockGameServer instance;

    private final CommandHandler commandHandler;

    @Getter
    private static final Logger logger = GameLogger.get("BlockGameServer");

    private final int port = 28004;
    private final NettyHandler nettyHandler;
    private final World world;

    public BlockGameServer() {
        instance = this;
        this.commandHandler = new CommandHandler();
        this.world = new World("world");
        this.nettyHandler = new NettyHandler(port);
        this.nettyHandler.run();

        running.set(true);
        tickExec.scheduleAtFixedRate(() -> {
            try {
                tick();
            } catch (Throwable t) {
                t.printStackTrace(); // guard so scheduler keeps running
            }
        }, 0, 50, TimeUnit.MILLISECONDS);
    }

    private void tick() {
        if(running.get()) {
            ThreadUtil.runMainQueue();
            this.world.update();
        }
    }

    public void exit() {
        getLogger().info("Server is shutting down...");
        if (!running.getAndSet(false)) return;
        tickExec.shutdownNow();
        this.nettyHandler.exit();

        ThreadUtil.shutdown();
        this.world.exitWorld();
    }

}
