package com.example;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Invite;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.json.JSONObject;

import java.awt.Color;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class MinecraftSocietyListener extends ListenerAdapter {

    // The Invite Cache: Maps GuildID -> (InviteCode -> Uses)
    private final Map<String, Map<String, Integer>> inviteCache = new ConcurrentHashMap<>();

    // Call this when the bot starts (we already added this to your main file!)
    public void updateInviteCache(Guild guild) {
        if (guild.getSelfMember().hasPermission(net.dv8tion.jda.api.Permission.MANAGE_SERVER)) {
            guild.retrieveInvites().queue(invites -> {
                Map<String, Integer> cache = new HashMap<>();
                for (Invite invite : invites) cache.put(invite.getCode(), invite.getUses());
                inviteCache.put(guild.getId(), cache);
            });
        }
    }

    @Override
    public void onGuildMemberJoin(GuildMemberJoinEvent event) {
        Guild guild = event.getGuild();

        // 1. Find the best channel to send the welcome message
        TextChannel welcomeChannel = null;
        for (TextChannel c : guild.getTextChannels()) {
            if (c.getName().toLowerCase().contains("welcome")) {
                welcomeChannel = c; break;
            }
        }
        if (welcomeChannel == null) welcomeChannel = guild.getSystemChannel();
        if (welcomeChannel == null) return; // No suitable channel found
        
        final TextChannel targetChannel = welcomeChannel;

        // 2. Fetch current invites to find which one was used
        if (guild.getSelfMember().hasPermission(net.dv8tion.jda.api.Permission.MANAGE_SERVER)) {
            guild.retrieveInvites().queue(currentInvites -> {
                Map<String, Integer> cachedInvites = inviteCache.getOrDefault(guild.getId(), new HashMap<>());
                Invite usedInvite = null;

                for (Invite invite : currentInvites) {
                    Integer cachedUses = cachedInvites.get(invite.getCode());
                    if (cachedUses != null && invite.getUses() > cachedUses) {
                        usedInvite = invite;
                        break;
                    }
                }

                updateInviteCache(guild); // Update the cache for the next person
                sendWelcomeMessage(event, targetChannel, usedInvite);
            });
        } else {
            sendWelcomeMessage(event, targetChannel, null); // Send without invite data if missing perms
        }
    }

    private void sendWelcomeMessage(GuildMemberJoinEvent event, TextChannel channel, Invite usedInvite) {
        Guild guild = event.getGuild();
        ServerConfig config = MinecraftSocietyBot.getGuildConfig(guild.getId());
        
        if (config.welcomeMessage == null || config.welcomeMessage.trim().isEmpty()) return;

        JSONObject embedData;
        try { embedData = new JSONObject(config.welcomeMessage); } 
        catch (Exception e) { embedData = new JSONObject().put("desc", config.welcomeMessage); }

        // Determine Invite Variables
        String inviteVar = "Unknown";
        String inviterVar = "";

        if (usedInvite != null) {
            String code = usedInvite.getCode();
            String alias = config.inviteAliases != null ? config.inviteAliases.get(code) : null;

            if (alias != null) {
                // If it's a magic invite, show both
                inviteVar = alias + " (discord.gg/" + code + ")";
                inviterVar = ""; // Hide inviter as requested
            } else {
                // If it's a normal invite
                inviteVar = "discord.gg/" + code;
                if (usedInvite.getInviter() != null) {
                    inviterVar = "\nInvited by: " + usedInvite.getInviter().getAsMention();
                }
            }
        }

        // Native Discord Timestamp (Displays local time for each user)
        String timeVar = "<t:" + (System.currentTimeMillis() / 1000) + ":f>";

        // Replace Variables
        String desc = embedData.optString("desc", "")
            .replace("$USER", event.getUser().getAsMention())
            .replace("$GUILD", guild.getName())
            .replace("$MEMBER_COUNT", String.valueOf(guild.getMemberCount()))
            .replace("$INVITE", inviteVar)
            .replace("$INVITER", inviterVar)
            .replace("$TIME", timeVar);

        EmbedBuilder eb = new EmbedBuilder();
        eb.setDescription(desc);

        String title = embedData.optString("title", "");
        if (!title.isEmpty()) eb.setTitle(title);

        String colorHex = embedData.optString("color", "");
        if (!colorHex.isEmpty()) {
            try { eb.setColor(Color.decode(colorHex)); } catch (Exception ignored) {}
        }

        String thumb = embedData.optString("thumb", "");
        if (!thumb.isEmpty()) eb.setThumbnail(thumb);

        String image = embedData.optString("image", "");
        if (!image.isEmpty()) eb.setImage(image);

        String footer = embedData.optString("footer", "")
            .replace("$MEMBER_COUNT", String.valueOf(guild.getMemberCount()));
        if (!footer.isEmpty()) eb.setFooter(footer);

        channel.sendMessageEmbeds(eb.build()).queue();
    }
}