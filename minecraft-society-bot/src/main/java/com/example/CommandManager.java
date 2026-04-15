package com.example;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CommandManager extends ListenerAdapter {
    
    private final Map<String, ICommand> commands = new HashMap<>();

    public void addCommand(ICommand command) {
        commands.put(command.getName(), command);
    }

    @Override
    public void onReady(ReadyEvent event) {
        // This tells Discord what commands our bot has when it turns on
        List<CommandData> commandData = new ArrayList<>();
        for (ICommand command : commands.values()) {
            commandData.add(Commands.slash(command.getName(), command.getDescription())
                    .addOptions(command.getOptions()));
        }
        event.getJDA().updateCommands().addCommands(commandData).queue();
        System.out.println("Slash Commands synced to Discord!");
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        // This routes the command to the right file
        ICommand command = commands.get(event.getName());
        if (command != null) {
            command.execute(event);
        }
    }
}
