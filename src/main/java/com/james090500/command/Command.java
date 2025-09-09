package com.james090500.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;

public interface Command {

    LiteralArgumentBuilder<CommandSource> onCommand();

}
