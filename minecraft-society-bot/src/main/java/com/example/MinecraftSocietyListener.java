package com.example;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

import java.awt.*;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MinecraftSocietyListener extends ListenerAdapter {

    // Cache to track invite usage: Code -> Use Count
    private final Map<String, Integer> inviteUses = new HashMap<>();
    
    public void updateInviteCache(Guild guild) {
        guild.retrieveInvites().queue(invites -> {
            for (Invite invite : invites) {
                inviteUses.put(invite.getCode(), invite.getUses());
            }
            System.out.println("✅ Invite cache updated for: " + guild.getName());
        });
    }
    
   @Override
public void onGuildMemberJoin(GuildMemberJoinEvent event) {
    if (event.getUser().isBot()) return;

    Guild guild = event.getGuild();
    ServerConfig config = MinecraftSocietyBot.getGuildConfig(guild.getId());

    // 1. Identification Logic
    String usedCode = "Unknown";
    String inviterId = null;
    List<Invite> currentInvites = guild.retrieveInvites().complete();

    for (Invite invite : currentInvites) {
        int previousUses = inviteUses.getOrDefault(invite.getCode(), 0);
        if (invite.getUses() > previousUses) {
            usedCode = invite.getCode();
            inviterId = (invite.getInviter() != null) ? invite.getInviter().getId() : null;
        }
        inviteUses.put(invite.getCode(), invite.getUses());
    }

    // 2. The "Smart" Source Logic
    String inviteSource;
    if (config.inviteAliases != null && config.inviteAliases.containsKey(usedCode)) {
        // We found an alias! Show the Alias and the Code together
        inviteSource = "**" + config.inviteAliases.get(usedCode) + "** (" + usedCode + ")";
    } else {
        // No alias? Fallback to your request: Show the inviter mention
        inviteSource = "`" + usedCode + "`\nInvited by: " + (inviterId != null ? "<@" + inviterId + ">" : "Unknown");
    }

    // 3. Variable Substitution for the Welcome Message
    String welcomeTemplate = (config.welcomeMessage != null && !config.welcomeMessage.isEmpty()) 
            ? config.welcomeMessage 
            : "Welcome $USER to $GUILD!";

    // We replace $INVITE_ALIAS here too so you can use it in the dashboard text box!
    String finalMessage = welcomeTemplate
            .replace("$USER", event.getUser().getAsMention())
            .replace("$GUILD", guild.getName())
            .replace("$INVITE_ALIAS", config.inviteAliases.getOrDefault(usedCode, "a secret link"));

    // 4. Build the Embed
    EmbedBuilder embed = new EmbedBuilder();
    embed.setColor(Color.GREEN);
    embed.setTitle("🎉 New Society Member!");
    embed.setDescription(
            finalMessage + "\n\n" +
            "**Join Details**\n" +
            "Source: " + inviteSource + "\n" + // Uses our smart logic!
            "Account Age: **" + getAccountAge(event.getUser()) + "**"
    );

    embed.setThumbnail(event.getUser().getAvatarUrl());
    embed.setFooter("Minecraft Society • Welcome System", guild.getIconUrl());
    embed.setTimestamp(Instant.now());

    var channels = guild.getTextChannelsByName("test-welcome", true);
    if (!channels.isEmpty()) {
        channels.get(0).sendMessageEmbeds(embed.build()).queue();
    }
}

// Helper for age calculation to keep the code clean
private String getAccountAge(User user) {
    long days = Duration.between(user.getTimeCreated().toInstant(), Instant.now()).toDays();
    if (days < 30) return days + " days";
    if (days < 365) return (days / 30) + " months";
    return (days / 365) + " years";
}
}