package com.example.commands;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;

import java.util.ArrayList;
import java.util.List;

// This command deletes a given number of messages from the channel it is used in
public class Purgecommand implements ICommand {

    @Override
    public String getName() {
        return "purge";
    }

    @Override
    public String getDescription() {
        return "Delete a number of messages from this channel (1-100).";
    }

    @Override
    public List<OptionData> getOptions() {
        List<OptionData> options = new ArrayList<>();

        // The user has to provide a number telling the bot how many messages to delete
        options.add(new OptionData(OptionType.INTEGER, "amount", "Number of messages to delete (1-100)", true));

        return options;
    }

    @Override
    public DefaultMemberPermissions getPermission() {
        // Only members with the Manage Messages permission can run this command
        return DefaultMemberPermissions.enabledFor(Permission.MESSAGE_MANAGE);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {

        // Read the number the user entered
        int amount = (int) event.getOption("amount").getAsLong();

        // Discord only allows bulk deleting between 1 and 100 messages at a time
        if (amount < 1 || amount > 100) {
            event.reply("Amount must be between 1 and 100.").setEphemeral(true).queue();
            return;
        }
        //without this discord will timeout after 3 seconds
        event.deferReply(true).queue(); //FIX FOR TIMEOUT 



        // Get the channel the command was used in so we can fetch messages from it
        TextChannel channel = event.getChannel().asTextChannel();

        // Fetch the most recent messages then delete them
        channel.getHistory().retrievePast(amount).queue(messages -> {

            // If there were no messages to fetch, tell the user and stop
            if (messages.isEmpty()) {
                event.getHook().editOriginal("No messages found to delete.").queue();
                return;
            }

            // Discord's bulk delete requires at least 2 messages
            // so if only 1 was fetched we have to delete it individually
            if (messages.size() == 1) {
                messages.get(0).delete().queue();
            } else {
                channel.deleteMessages(messages).queue();
            }

            // Update the reply to confirm how many messages were deleted
            event.getHook().editOriginal("Deleted " + messages.size() + " messages.").queue();
        });
    }
}