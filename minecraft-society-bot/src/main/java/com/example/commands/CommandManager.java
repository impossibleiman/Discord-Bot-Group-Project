package com.example.commands;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.EntitySelectInteractionEvent;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

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

    // Route Entity Selects
    @Override
    public void onEntitySelectInteraction(EntitySelectInteractionEvent event) {
        String[] parts = event.getComponentId().split(":", 3); // command:action:userId
        if (parts.length > 0) {
            ICommand command = commands.get(parts[0]);
            if (command instanceof ReactionRoleCommand) {
                ((ReactionRoleCommand) command).handleEntitySelect(event);
            }
        }
    }

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        // Increase split limit to 3 to correctly handle "command:action:userId"
        String[] parts = event.getComponentId().split(":", 3); 
        
        if (parts.length > 0) {
            String commandName = parts[0]; 
            ICommand command = commands.get(commandName);
            if (command != null) {
                command.onButtonInteraction(event);
            }
        }
    }

    // Message Received
    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        if (event.getAuthor().isBot()) return;
        
        // Pass the message to every command that might have an active session
        for (ICommand command : commands.values()) {
            command.onMessageReceived(event);
        }
    }
}