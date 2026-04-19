package com.example;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.actionrow.ActionRow;

import java.awt.*;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MinecraftSocietyListener extends ListenerAdapter {

    private final Map<String, Integer> inviteUses = new HashMap<>();

    // ===== WELCOME + INVITE TRACKING =====
    @Override
    public void onGuildMemberJoin(GuildMemberJoinEvent event) {
        if (event.getUser().isBot()) return;

        Guild guild = event.getGuild();
        String guildId = guild.getId();

        // 1. Fetch Modular Config from Main class
        ServerConfig config = MinecraftSocietyBot.getGuildConfig(guildId);

        // 2. Invite Tracking Logic
        String inviteCode = "Unknown";
        String inviter = "Unknown";
        List<Invite> invites = guild.retrieveInvites().complete();

        for (Invite invite : invites) {
            int previousUses = inviteUses.getOrDefault(invite.getCode(), 0);
            if (invite.getUses() > previousUses) {
                inviteCode = invite.getCode();
                inviter = invite.getInviter() != null ? invite.getInviter().getAsTag() : "Unknown";
            }
            inviteUses.put(invite.getCode(), invite.getUses());
        }

        // 3. Channel Selection
        var channels = guild.getTextChannelsByName("test-welcome", true);
        if (channels.isEmpty()) return;
        var channel = channels.get(0);

        // 4. Calculate Account Age
        long days = Duration.between(event.getUser().getTimeCreated().toInstant(), Instant.now()).toDays();
        String age = (days < 30) ? days + " days" : (days < 365) ? (days / 30) + " months" : (days / 365) + " years";

        // 5. Variable Substitution ($USER, $GUILD)
        String welcomeTemplate = (config.welcomeMessage != null && !config.welcomeMessage.isEmpty()) 
                ? config.welcomeMessage 
                : "Welcome $USER to $GUILD!"; // Fallback default

        String formattedMessage = welcomeTemplate
                .replace("$USER", event.getUser().getAsMention())
                .replace("$GUILD", guild.getName());

        // 6. Build the Modular Embed
        EmbedBuilder embed = new EmbedBuilder();
        embed.setColor(Color.GREEN);
        embed.setTitle("🎉 New Society Member!"); 
        embed.setDescription(
                formattedMessage + "\n\n" +
                "**Invite Stats**\n" +
                "Joined via: `" + inviteCode + "`\n" +
                "Invited by: **" + inviter + "**\n\n" +
                "**Account Age**\n" + age
        );

        embed.setThumbnail(event.getUser().getAvatarUrl());
        embed.setFooter("Minecraft Society • Welcome System", guild.getIconUrl());
        embed.setTimestamp(Instant.now());

        channel.sendMessageEmbeds(embed.build()).queue();
    }

    // ===== VERIFY BUTTON CLICK (Unchanged) =====
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
        event.reply("✅ You now have full access!").setEphemeral(true).queue();
    }

    // ===== VERIFY EMBED (Unchanged) =====
    public void sendVerifyEmbed(Guild guild) {
        var channels = guild.getTextChannelsByName("verify", true);
        if (channels.isEmpty()) return;

        var channel = channels.get(0);
        EmbedBuilder embed = new EmbedBuilder();
        embed.setColor(Color.GREEN);
        embed.setTitle("✅ Verification Required");
        embed.setDescription(
                "Welcome to **Minecraft Society**!\n\n" +
                "Click the button below to unlock the server 🚀\n\n" +
                "🔒 You currently have limited access"
        );
        embed.setFooter("Minecraft Society • Verification", guild.getIconUrl());
        embed.setTimestamp(Instant.now());

        channel.sendMessageEmbeds(embed.build())
                .addComponents(ActionRow.of(Button.success("verify_button", "Enter Server 🚀")))
                .queue();
    }
}