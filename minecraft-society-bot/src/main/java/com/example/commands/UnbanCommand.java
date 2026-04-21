package com.example.commands;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.requests.restaction.pagination.BanPaginationAction;

import java.awt.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

// This command unbans a user by searching the ban list for their username
// We have to search the ban list because banned users are no longer in the server
public class UnbanCommand implements ICommand {

    @Override
    public String getName() {
        return "unban";
    }

    @Override
    public String getDescription() {
        return "Unban a user from the server by their username.";
    }

    @Override
    public List<OptionData> getOptions() {
        List<OptionData> options = new ArrayList<>();

        // The moderator types in the username of the person they want to unban
        options.add(new OptionData(OptionType.STRING, "username", "The username of the user to unban", true));

        // Reason is optional
        options.add(new OptionData(OptionType.STRING, "reason", "Reason for the unban", false));

        return options;
    }

    @Override
    public DefaultMemberPermissions getPermission() {
        // Only members with the Ban Members permission can unban users
        return DefaultMemberPermissions.enabledFor(Permission.BAN_MEMBERS);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {

        // Get the username that was typed in and convert to lowercase for easier comparison
        String inputName = event.getOption("username").getAsString().toLowerCase();

        // Use a default reason if none was provided
        String reason = event.getOption("reason") != null
                ? event.getOption("reason").getAsString()
                : "No reason provided";

        // deferReply so Discord does not time out while we fetch and search the ban list
        event.deferReply(true).queue();

        // Fetch the full list of banned users from the server
        event.getGuild().retrieveBanList().queue(banList -> {

            // Search through the ban list for a username that matches what was typed
            // We use toLowerCase on both sides so the search is not case sensitive
            User matchedUser = null;
            for (Guild.Ban ban : banList) {
                if (ban.getUser().getName().toLowerCase().contains(inputName)) {
                    matchedUser = ban.getUser();
                    break;
                }
            }

            // If no match was found, tell the moderator and stop
            if (matchedUser == null) {
                event.getHook().editOriginal("Could not find a banned user with that name. Check the spelling and try again.").queue();
                return;
            }

            // Store the matched user in a final variable so it can be used inside the lambda below
            final User userToUnban = matchedUser;

            // Unban the user we found
            event.getGuild().unban(userToUnban)
                    .reason(reason)
                    .queue(
                            success -> {
                                // Build a confirmation embed showing who was unbanned
                                EmbedBuilder embed = new EmbedBuilder()
                                        .setTitle("User Unbanned")
                                        .setColor(Color.GREEN)
                                        .addField("User", userToUnban.getName(), true)
                                        .addField("Moderator", event.getUser().getName(), true)
                                        .addField("Reason", reason, false)
                                        .setTimestamp(Instant.now());

                                event.getHook().editOriginalEmbeds(embed.build()).queue();
                            },
                            // If the unban fails, show the error to the moderator
                            error -> event.getHook().editOriginal("Failed to unban: " + error.getMessage()).queue()
                    );

        // If fetching the ban list itself fails, show the error
        }, error -> event.getHook().editOriginal("Failed to retrieve ban list: " + error.getMessage()).queue());
    }
}