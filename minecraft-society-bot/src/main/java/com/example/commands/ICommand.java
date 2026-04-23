package com.example.commands;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions; // New Import
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.EntitySelectInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
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

    default void onStringSelectInteraction(StringSelectInteractionEvent event) {
        // Optional string select logic
    }

    default void onChannelSelectInteraction(EntitySelectInteractionEvent event) {
        // Optional channel select logic
    }

    default void onAutoCompleteInteraction(CommandAutoCompleteInteractionEvent event) {
        // Optional autocomplete logic
    }

    default void onMessageReceived(MessageReceivedEvent event) {
    // By default, do nothing.
    }
}