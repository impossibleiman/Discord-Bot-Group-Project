package com.example.commands;

import com.example.managers.RestrictionManager;
import com.example.managers.RestrictionManager.RestrictedChannelState;
import com.example.managers.RestrictionManager.RestrictionRecord;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.requests.restaction.PermissionOverrideAction;

import java.awt.Color;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class UnrestrictCommand implements ICommand {

    @Override
    public String getName() {
        return "unrestrict";
    }

    @Override
    public String getDescription() {
        return "Restore a restricted user's previous channel access.";
    }

    @Override
    public List<OptionData> getOptions() {
        List<OptionData> options = new ArrayList<>();
        options.add(new OptionData(OptionType.USER, "user", "The user to unrestrict", true));
        return options;
    }

    @Override
    public DefaultMemberPermissions getPermission() {
        return DefaultMemberPermissions.enabledFor(Permission.MANAGE_ROLES);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Member target = event.getOption("user").getAsMember();
        if (target == null) {
            event.reply("Could not find that user in this server.").setEphemeral(true).queue();
            return;
        }

        if (target.equals(event.getMember())) {
            event.reply("You cannot unrestrict yourself.").setEphemeral(true).queue();
            return;
        }

        if (!event.getGuild().getSelfMember().canInteract(target)) {
            event.reply("I cannot unrestrict this user because they have a higher or equal role to me.").setEphemeral(true).queue();
            return;
        }

        RestrictionRecord record = RestrictionManager.getRestriction(event.getGuild().getId(), target.getId());
        if (record == null || record.channels == null || record.channels.isEmpty()) {
            event.reply("No saved restriction record was found for this user.").setEphemeral(true).queue();
            return;
        }

        event.reply("Unrestricting " + target.getUser().getName() + "...").setEphemeral(true).queue();

        final int[] completed = {0};
        final int[] failed = {0};
        int total = record.channels.size();

        for (RestrictedChannelState channelState : record.channels) {
            TextChannel channel = event.getGuild().getTextChannelById(channelState.channelId);
            if (channel == null) {
                completed[0]++;
                if (completed[0] == total) {
                    sendSuccessMessage(event, target, record, failed[0]);
                }
                continue;
            }

            PermissionOverrideAction action = channel.upsertPermissionOverride(target);
            action = applyPermissionState(action, Permission.VIEW_CHANNEL, channelState.viewState);
            action = applyPermissionState(action, Permission.MESSAGE_SEND, channelState.sendState);
            action.queue(
                    success -> {
                        completed[0]++;
                        if (completed[0] == total) {
                            sendSuccessMessage(event, target, record, failed[0]);
                        }
                    },
                    failure -> {
                        failed[0]++;
                        completed[0]++;
                        if (completed[0] == total) {
                            sendSuccessMessage(event, target, record, failed[0]);
                        }
                    }
            );
        }
    }

    private PermissionOverrideAction applyPermissionState(PermissionOverrideAction action, Permission permission, int state) {
        if (state > 0) {
            return action.grant(permission);
        }
        if (state < 0) {
            return action.deny(permission);
        }
        return action.clear(permission);
    }

    private void sendSuccessMessage(SlashCommandInteractionEvent event, Member target, RestrictionRecord record, int failedCount) {
        RestrictionManager.clearRestriction(event.getGuild().getId(), target.getId());

        String allowedChannelText = "Unknown";
        if (record.allowedChannelId != null) {
            TextChannel allowedChannel = event.getGuild().getTextChannelById(record.allowedChannelId);
            if (allowedChannel != null) {
                allowedChannelText = allowedChannel.getAsMention();
            }
        }

        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("User Unrestricted")
                .setColor(Color.GREEN)
                .setDescription("Restored saved channel access for " + target.getAsMention())
                .addField("User", target.getUser().getName(), true)
                .addField("Moderator", event.getUser().getName(), true)
                .addField("Previously Allowed Channel", allowedChannelText, false)
                .addField("Channels Restored", String.valueOf(record.channels.size()), true)
                .addField("Channel Restore Failures", String.valueOf(failedCount), true)
                .setTimestamp(Instant.now());

        event.getHook().editOriginal("Done!").queue();
        event.getChannel().sendMessageEmbeds(embed.build()).queue();
    }
}
