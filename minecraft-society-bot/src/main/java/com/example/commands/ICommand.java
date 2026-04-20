package com.example.commands;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions; // New Import
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import java.util.List;

public interface ICommand {
    String getName();
    String getDescription();
    List<OptionData> getOptions();
    void execute(SlashCommandInteractionEvent event);

    // By default, commands are enabled for everyone. Override this to restrict permissions
    default DefaultMemberPermissions getPermission() {
        return DefaultMemberPermissions.ENABLED;
    }

    default void onButtonInteraction(ButtonInteractionEvent event) {
        // Optional button logic
    }

    default void onMessageReceived(MessageReceivedEvent event) {
    // By default, do nothing.
    }
}