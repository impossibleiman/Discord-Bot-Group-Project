package com.example.listeners;

import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRemoveEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

import com.example.managers.StickyRoleManager;
import com.example.model.MemberData;

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

        List<String> roleIds = new ArrayList<>();

        if (member != null) {
            roleIds = member.getRoles()
                    .stream()
                    .map(role -> role.getId())
                    .collect(Collectors.toList());
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

                long totalMinutes = timeInServer / (1000L * 60);
                long totalHours = timeInServer / (1000L * 60 * 60);
                long totalDays = timeInServer / (1000L * 60 * 60 * 24);

                long years = totalDays / 365;
                long months = (totalDays % 365) / 30;
                long days = (totalDays % 365) % 30;
                long hours = totalHours % 24;
                long minutes = totalMinutes % 60;

                timeMessage = years + " year(s), "
                        + months + " month(s), "
                        + days + " day(s), "
                        + hours + " hour(s), "
                        + minutes + " minute(s)";
            }
        }

        System.out.println(event.getUser().getName() + " left the server.");
        System.out.println("Time in server: " + timeMessage);
        System.out.println("Roles saved: " + roleIds.size());

        TextChannel leaveLogChannel = event.getGuild().getTextChannelById(LEAVE_LOG_CHANNEL_ID);

        if (leaveLogChannel != null) {
            String message = "**Member Left**\n"
                    + "User: " + event.getUser().getAsTag() + "\n"
                    + "Time in server: " + timeMessage + "\n"
                    + "Roles saved: " + roleIds.size();

            leaveLogChannel.sendMessage(message).queue();
        } else {
            System.out.println("Leave log channel not found.");
        }
    }
}