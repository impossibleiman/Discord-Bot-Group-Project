package com.example.listeners;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRemoveEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

import com.example.managers.StickyRoleManager;
import com.example.model.MemberData;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class LeaveListener extends ListenerAdapter {

    private static final String LEAVE_LOG_CHANNEL_ID = "1487929990413684929"; 

    @Override
    public void onGuildMemberRemove(GuildMemberRemoveEvent event) {
        System.out.println("LEAVE EVENT TRIGGERED");

        String userId = event.getUser().getId();
        Member member = event.getMember();
        MemberData existingMemberData = StickyRoleManager.getMemberData(userId);

        List<String> roleIds = new ArrayList<>();

        if (member != null) {
            roleIds = member.getRoles()
                    .stream()
                    .map(role -> role.getId())
                    .collect(Collectors.toList());
        } else if (existingMemberData != null && existingMemberData.getRoleIds() != null) {
            roleIds = new ArrayList<>(existingMemberData.getRoleIds());
        } else {
            System.out.println("Member is null, could not read roles during leave event.");
        }

        long leaveTimestamp = System.currentTimeMillis();
        StickyRoleManager.saveLeaveData(userId, leaveTimestamp, roleIds);

        MemberData memberData = StickyRoleManager.getMemberData(userId);
        String timeMessage = "Join timestamp not found.";

        if (memberData != null) {
            long joinTimestamp = memberData.getJoinTimestamp();

            if (joinTimestamp > 0) {
                long timeInServer = leaveTimestamp - joinTimestamp;
                timeMessage = formatDuration(timeInServer);
            }
        }

        TextChannel leaveLogChannel = event.getGuild().getTextChannelById(LEAVE_LOG_CHANNEL_ID);

        if (leaveLogChannel != null) {
            String username = event.getUser().getName();
            String mention = "<@" + userId + ">";

            String rolesMessage;
            if (roleIds.isEmpty()) {
                rolesMessage = "No roles saved.";
            } else {
                rolesMessage = roleIds.stream()
                        .map(id -> "<@&" + id + ">")
                        .collect(Collectors.joining(", "));
            }

            EmbedBuilder eb = new EmbedBuilder()
                    .setTitle("Member Left")
                    .setColor(new Color(239, 68, 68))
                    .setThumbnail(event.getUser().getEffectiveAvatarUrl())
                    .addField("User", username + " (" + mention + ")", false)
                    .addField("Time in server", timeMessage, false)
                    .addField("Roles", rolesMessage, false);

            leaveLogChannel.sendMessageEmbeds(eb.build()).queue();
        } else {
            System.out.println("Leave log channel not found.");
        }
    }

    private String formatDuration(long millis) {
        long totalMinutes = millis / (1000L * 60);
        long totalHours = millis / (1000L * 60 * 60);
        long totalDays = millis / (1000L * 60 * 60 * 24);

        long years = totalDays / 365;
        long months = (totalDays % 365) / 30;
        long days = (totalDays % 365) % 30;
        long hours = totalHours % 24;
        long minutes = totalMinutes % 60;

        List<String> parts = new ArrayList<>();
        if (years > 0) parts.add(years + "y");
        if (months > 0) parts.add(months + "mo");
        if (days > 0) parts.add(days + "d");
        if (hours > 0) parts.add(hours + "h");
        if (minutes > 0) parts.add(minutes + "m");

        return parts.isEmpty() ? "< 1m" : String.join(" ", parts);
    }
}