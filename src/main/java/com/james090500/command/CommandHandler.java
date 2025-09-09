package com.james090500.command;

import com.james090500.utils.ThreadUtil;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class CommandHandler {

    CommandDispatcher<CommandSource> dispatcher = new CommandDispatcher<>();

    public CommandHandler() {
        new Thread(() -> {
            try (BufferedReader br = new BufferedReader(new InputStreamReader(System.in))) {
                String line;
                while ((line = br.readLine()) != null) {
                    String finalLine = line;
                    ThreadUtil.getMainQueue().add(() -> handleInput(finalLine));
                    if(finalLine.equalsIgnoreCase("stop")) break;
                }
            } catch (IOException ignored) {}
        }).start();

        registerCommand(new ShutDownServerCommand());
    }

    private void registerCommand(Command command) {
        dispatcher.register(command.onCommand());
    }

    private void handleInput(String input) {
        try {
            CommandSource source = new CommandSource();
            final ParseResults<CommandSource> parse = dispatcher.parse(input, source);
            final int result = dispatcher.execute(parse);
        } catch (CommandSyntaxException e) {
            throw new RuntimeException(e);
        }
    }
}
