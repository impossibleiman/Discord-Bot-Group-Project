package com.example.listeners;

import net.dv8tion.jda.api.events.guild.member.GuildMemberRoleAddEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRoleRemoveEvent;
import net.dv8tion.jda.api.events.guild.member.GenericGuildMemberEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

import com.example.managers.StickyRoleManager;

import java.util.List;
import java.util.stream.Collectors;

public class RoleUpdateListener extends ListenerAdapter {

    public RoleUpdateListener() {
        System.out.println("RoleUpdateListener loaded");
    }

    @Override
    public void onGuildMemberRoleAdd(GuildMemberRoleAddEvent event) {
        System.out.println("ROLE ADD EVENT TRIGGERED");
        saveRoles(event);
    }

    @Override
    public void onGuildMemberRoleRemove(GuildMemberRoleRemoveEvent event) {
        System.out.println("ROLE REMOVE EVENT TRIGGERED");
        saveRoles(event);
    }

    private void saveRoles(GenericGuildMemberEvent event) {
        String userId = event.getMember().getId();

        List<String> roleIds = event.getMember().getRoles()
                .stream()
                .map(role -> role.getId())
                .collect(Collectors.toList());

        StickyRoleManager.saveLeaveData(
                userId,
                System.currentTimeMillis(),
                roleIds
        );

        System.out.println("Roles updated and saved for user: " + userId);
        System.out.println("Current role count: " + roleIds.size());
    }
}