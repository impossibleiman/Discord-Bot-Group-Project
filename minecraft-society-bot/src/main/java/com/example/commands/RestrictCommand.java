package com.example.commands;

import com.example.managers.RestrictionManager;
import com.example.managers.RestrictionManager.RestrictedChannelState;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.PermissionOverride;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

import java.awt.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

// This command restricts a user so they can only see one specific channel
// It works by looping through every text channel and denying view access,
// then granting access only to the channel that was chosen
public class RestrictCommand implements ICommand {

    @Override
    public String getName() {
        return "restrict";
    }

    @Override
    public String getDescription() {
        return "Restrict a user so they can only see one channel.";
    }

    @Override
    public List<OptionData> getOptions() {
        List<OptionData> options = new ArrayList<>();

        // Both the user and the channel they should be restricted to are required
        options.add(new OptionData(OptionType.USER, "user", "The user to restrict", true));
        options.add(new OptionData(OptionType.CHANNEL, "channel", "The only channel this user can see", true));

        return options;
    }

    @Override
    public DefaultMemberPermissions getPermission() {
        // Only members with the Manage Roles permission can run this command
        return DefaultMemberPermissions.enabledFor(Permission.MANAGE_ROLES);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {

        // Get the member that needs to be restricted
        Member target = event.getOption("user").getAsMember();

        // Try to get the selected channel as a text channel
        // This throws an exception if the user picked a voice channel or category instead
        TextChannel allowedChannel;
        try {
            allowedChannel = event.getOption("channel").getAsChannel().asTextChannel();
        } catch (IllegalStateException e) {
            event.reply("Please select a text channel.").setEphemeral(true).queue();
            return;
        }

        // Make sure the user is actually in the server
        if (target == null) {
            event.reply("Could not find that user in this server.").setEphemeral(true).queue();
            return;
        }

        // Stop the moderator from restricting themselves by accident
        if (target.equals(event.getMember())) {
            event.reply("You cannot restrict yourself.").setEphemeral(true).queue();
            return;
        }

        // Make sure the bot has a high enough role to change permissions for this user
        if (!event.getGuild().getSelfMember().canInteract(target)) {
            event.reply("I cannot restrict this user because they have a higher or equal role to me.").setEphemeral(true).queue();
            return;
        }

        // Reply straight away to prevent the interaction from timing out
        // This is important because we are about to do a lot of permission changes
        event.reply("Restricting " + target.getUser().getName() + "...").setEphemeral(true).queue();

        // Get every text channel in the server
        List<TextChannel> allChannels = event.getGuild().getTextChannels();

        // Snapshot current member-specific view/send states so unrestrict can restore exactly.
        RestrictionManager.saveRestriction(
            event.getGuild().getId(),
            target.getId(),
            allowedChannel.getId(),
            buildRestrictionSnapshot(allChannels, target)
        );

        // We use this counter to know when all the permission changes are done
        final int[] completed = {0};
        int total = allChannels.size();

        for (TextChannel channel : allChannels) {
            if (channel.equals(allowedChannel)) {
                // Grant the user permission to view and send messages in the allowed channel
                channel.upsertPermissionOverride(target)
                        .grant(Permission.VIEW_CHANNEL, Permission.MESSAGE_SEND)
                        .queue(v -> {
                            completed[0]++;
                            // Once every channel has been updated, send the success message
                            if (completed[0] == total) sendSuccessMessage(event, target, allowedChannel);
                        });
            } else {
                // Deny the user from seeing every other channel
                channel.upsertPermissionOverride(target)
                        .deny(Permission.VIEW_CHANNEL)
                        .queue(v -> {
                            completed[0]++;
                            if (completed[0] == total) sendSuccessMessage(event, target, allowedChannel);
                        });
            }
        }
    }

    // Sends a confirmation embed once all the permission changes have finished
    private void sendSuccessMessage(SlashCommandInteractionEvent event, Member target, TextChannel allowedChannel) {
        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("User Restricted")
                .setColor(Color.ORANGE)
                .setDescription(target.getUser().getName() + " can now only see " + allowedChannel.getAsMention())
                .addField("User", target.getUser().getName(), true)
                .addField("Moderator", event.getUser().getName(), true)
                .addField("Allowed Channel", allowedChannel.getAsMention(), false)
                .setTimestamp(Instant.now());

        // Edit the original "Restricting..." message to say done
        event.getHook().editOriginal("Done!").queue();

        // Send the embed in the channel for everyone to see
        event.getChannel().sendMessageEmbeds(embed.build()).queue();
    }

    private List<RestrictedChannelState> buildRestrictionSnapshot(List<TextChannel> channels, Member target) {
        List<RestrictedChannelState> snapshot = new ArrayList<>();
        for (TextChannel channel : channels) {
            RestrictedChannelState state = new RestrictedChannelState();
            state.channelId = channel.getId();
            state.viewState = getPermissionState(channel, target, Permission.VIEW_CHANNEL);
            state.sendState = getPermissionState(channel, target, Permission.MESSAGE_SEND);
            snapshot.add(state);
        }
        return snapshot;
    }

    private int getPermissionState(TextChannel channel, Member target, Permission permission) {
        PermissionOverride override = channel.getPermissionOverride(target);
        if (override == null) {
            return 0;
        }

        long mask = permission.getRawValue();
        if ((override.getAllowedRaw() & mask) != 0L) {
            return 1;
        }
        if ((override.getDeniedRaw() & mask) != 0L) {
            return -1;
        }
        return 0;
    }
}