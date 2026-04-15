package com.example;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
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
        List<CommandData> commandData = new ArrayList<>();
        for (ICommand command : commands.values()) {
            // Build the command and set the permissions dynamically from the command class
            commandData.add(Commands.slash(command.getName(), command.getDescription())
                    .addOptions(command.getOptions())
                    .setDefaultPermissions(command.getPermission()));
        }
        event.getJDA().updateCommands().addCommands(commandData).queue();
        System.out.println("All Slash Commands have been synced to Discord!");
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        ICommand command = commands.get(event.getName());
        if (command != null) {
            command.execute(event);
        }
    }

 // --- NEW: Modular Button Listener ---
    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        // We expect button IDs to look like "commandName:actionData"
        String[] parts = event.getComponentId().split(":", 2); 
        
        if (parts.length > 0) {
            String commandName = parts[0]; // Extracts the prefix (e.g., "event")
            
            // Find the command file that matches the prefix and route the click to it!
            ICommand command = commands.get(commandName);
            if (command != null) {
                command.onButtonInteraction(event);
            }
        }
    }
}