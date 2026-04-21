package com.example.command;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

import java.awt.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

// This command bans a member from the server with an optional reason
public class BanCommand implements ICommand {

    @Override
    public String getName() {
        return "ban";
    }

    @Override
    public String getDescription() {
        return "Ban a member from the server.";
    }

    @Override
    public List<OptionData> getOptions() {
        List<OptionData> options = new ArrayList<>();

        // The user to ban is required
        options.add(new OptionData(OptionType.USER, "user", "The user to ban", true));

        // The reason is optional - if not given we use a default message
        options.add(new OptionData(OptionType.STRING, "reason", "Reason for the ban", false));

        return options;
    }

    @Override
    public DefaultMemberPermissions getPermission() {
        // Only members with the Ban Members permission can run this command
        return DefaultMemberPermissions.enabledFor(Permission.BAN_MEMBERS);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {

        // Get the member object for the user that was selected
        Member target = event.getOption("user").getAsMember();

        // Use a default reason if none was provided
        String reason = event.getOption("reason") != null
                ? event.getOption("reason").getAsString()
                : "No reason provided";

        // Make sure the target is actually in the server
        if (target == null) {
            event.reply("Could not find that user in this server.").setEphemeral(true).queue();
            return;
        }

        // Stop the moderator from banning themselves by accident
        if (target.equals(event.getMember())) {
            event.reply("You cannot ban yourself.").setEphemeral(true).queue();
            return;
        }

        // Check the bot can actually ban this person
        // This fails if the target has a higher or equal role to the bot
        if (!event.getGuild().getSelfMember().canInteract(target)) {
            event.reply("I cannot ban this user because they have a higher or equal role to me.").setEphemeral(true).queue();
            return;
        }

        // Save the name before banning because we lose access to the member object after
        String bannedName = target.getUser().getName();

        // Ban the user - the 0 and TimeUnit.SECONDS means we are not deleting any message history
        event.getGuild().ban(target, 0, TimeUnit.SECONDS)
                .reason(reason)
                .queue(
                        success -> {
                            // Build an embed to show the result of the ban
                            EmbedBuilder embed = new EmbedBuilder()
                                    .setTitle("User Banned")
                                    .setColor(Color.RED)
                                    .addField("User", bannedName, true)
                                    .addField("Moderator", event.getUser().getName(), true)
                                    .addField("Reason", reason, false)
                                    .setTimestamp(Instant.now());

                            event.replyEmbeds(embed.build()).queue();
                        },
                        // If the ban fails for any reason, show the error to the moderator
                        error -> event.reply("Failed to ban: " + error.getMessage()).setEphemeral(true).queue()
                );
    }
}