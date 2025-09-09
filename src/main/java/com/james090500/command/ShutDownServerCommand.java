package com.james090500.command;

import com.james090500.BlockGameServer;
import com.james090500.utils.ThreadUtil;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;

public class ShutDownServerCommand implements Command {

    public LiteralArgumentBuilder<CommandSource> onCommand() {
        return LiteralArgumentBuilder.<CommandSource>literal("stop")
                .executes(c -> {
                    ThreadUtil.getMainQueue().add(() -> BlockGameServer.getInstance().exit());
                    return 1;
                });
    }
}
