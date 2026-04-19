package com.example;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
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

    @Override
    public void onGuildMemberJoin(GuildMemberJoinEvent event) {
        if (event.getUser().isBot()) return;

        Guild guild = event.getGuild();
        String guildId = guild.getId();
        ServerConfig config = MinecraftSocietyBot.getGuildConfig(guildId);

        // 1. Identify which invite was used
        String usedCode = "Unknown";
        String inviterId = null;

        List<Invite> currentInvites = guild.retrieveInvites().complete();
        for (Invite invite : currentInvites) {
            int previousUses = inviteUses.getOrDefault(invite.getCode(), 0);

            if (invite.getUses() > previousUses) {
                usedCode = invite.getCode();
                inviterId = (invite.getInviter() != null) ? invite.getInviter().getId() : null;
            }
            // Update cache for next join
            inviteUses.put(invite.getCode(), invite.getUses());
        }

        // 2. Determine the Invite Source ($INVITE_ALIAS)
        String sourceInfo;
        if (config.inviteAliases != null && config.inviteAliases.containsKey(usedCode)) {
            // Found a user-defined alias (e.g., "Instagram")
            sourceInfo = config.inviteAliases.get(usedCode);
        } else {
            // Fallback: Mention the inviter by ID
            sourceInfo = (inviterId != null) ? "Invited by: <@" + inviterId + ">" : "an unknown link";
        }

        // 3. Select Target Channel
        var channels = guild.getTextChannelsByName("test-welcome", true);
        if (channels.isEmpty()) return;
        var channel = channels.get(0);

        // 4. Calculate Account Age
        long days = Duration.between(event.getUser().getTimeCreated().toInstant(), Instant.now()).toDays();
        String age = (days < 30) ? days + " days" : (days < 365) ? (days / 30) + " months" : (days / 365) + " years";

        // 5. Modular Variable Substitution
        String welcomeTemplate = (config.welcomeMessage != null && !config.welcomeMessage.isEmpty()) 
                ? config.welcomeMessage 
                : "Welcome $USER! Source: $INVITE_ALIAS";

        String finalMessage = welcomeTemplate
                .replace("$USER", event.getUser().getAsMention())
                .replace("$GUILD", guild.getName())
                .replace("$INVITE_ALIAS", sourceInfo);

        // 6. Build the Embed
        EmbedBuilder embed = new EmbedBuilder();
        embed.setColor(Color.GREEN);
        embed.setTitle("🎉 New Society Member!");
        embed.setDescription(
                finalMessage + "\n\n" +
                "**Join Details**\n" +
                "Invite Code: `" + usedCode + "`\n" +
                "Account Age: **" + age + "**"
        );

        embed.setThumbnail(event.getUser().getAvatarUrl());
        embed.setFooter("Minecraft Society • Welcome System", guild.getIconUrl());
        embed.setTimestamp(Instant.now());

        channel.sendMessageEmbeds(embed.build()).queue();
    }

    // ===== BUTTON INTERACTION (UNCHANGED) =====
    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        if (!event.getComponentId().equals("verify_button")) return;
        Guild guild = event.getGuild();
        Member member = event.getMember();
        if (guild == null || member == null) return;

        var roles = guild.getRolesByName("Member", true);
        if (roles.isEmpty()) {
            event.reply("Role 'Member' not found.").setEphemeral(true).queue();
            return;
        }

        guild.addRoleToMember(member, roles.get(0)).queue();
        event.reply("✅ Verification successful!").setEphemeral(true).queue();
    }
}