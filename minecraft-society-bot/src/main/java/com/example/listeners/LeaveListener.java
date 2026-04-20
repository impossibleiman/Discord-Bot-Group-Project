package com.example.listeners;

import com.example.MinecraftSocietyBot;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRemoveEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

import com.example.managers.StickyRoleManager;
import com.example.model.MemberData;
import com.example.model.ServerConfig;
import org.json.JSONObject;

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

            String userVar = username + " (" + mention + ")";

            ServerConfig config = MinecraftSocietyBot.getGuildConfig(event.getGuild().getId());
            JSONObject leaveEmbedData;

            try {
                leaveEmbedData = new JSONObject(config.leaveMessage);
            } catch (Exception e) {
                leaveEmbedData = new JSONObject();
            }

            String title = replaceLeaveVars(leaveEmbedData.optString("title", "Member Left"), userVar, timeMessage, rolesMessage);
            String description = replaceLeaveVars(
                    leaveEmbedData.optString("desc", "$USER left the server.\nTime in server: $TIME_IN_SERVER\nRoles: $ROLES"),
                    userVar,
                    timeMessage,
                    rolesMessage
            );
            String footer = replaceLeaveVars(leaveEmbedData.optString("footer", ""), userVar, timeMessage, rolesMessage);
            String thumbnailUrl = leaveEmbedData.optString("thumb", "").trim();
            String imageUrl = leaveEmbedData.optString("image", "").trim();
            Color embedColor = parseColorSafely(leaveEmbedData.optString("color", ""), new Color(239, 68, 68));

            EmbedBuilder eb = new EmbedBuilder()
                    .setColor(embedColor)
                    .setDescription(description);

            if (!title.isEmpty()) {
                eb.setTitle(title);
            }

            if (!thumbnailUrl.isEmpty()) {
                eb.setThumbnail(thumbnailUrl);
            } else {
                eb.setThumbnail(event.getUser().getEffectiveAvatarUrl());
            }

            if (!imageUrl.isEmpty()) {
                eb.setImage(imageUrl);
            }

            if (!footer.isEmpty()) {
                eb.setFooter(footer);
            }

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

    private String replaceLeaveVars(String input, String user, String timeInServer, String roles) {
        return input
                .replaceAll("(?i)\\$USER\\b", user)
                .replaceAll("(?i)\\$TIME_IN_SERVER\\b", timeInServer)
                .replaceAll("(?i)\\$ROLES\\b", roles)
                .replaceAll("(?i)\\$TIME\\b", "<t:" + (System.currentTimeMillis() / 1000L) + ":f>");
    }

    private Color parseColorSafely(String hex, Color fallback) {
        if (hex == null || hex.trim().isEmpty()) {
            return fallback;
        }

        String clean = hex.trim();
        if (clean.startsWith("#")) {
            clean = clean.substring(1);
        }

        if (clean.length() != 6) {
            return fallback;
        }

        try {
            return new Color(Integer.parseInt(clean, 16));
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }
}