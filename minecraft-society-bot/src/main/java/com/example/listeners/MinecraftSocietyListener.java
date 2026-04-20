package com.example.listeners;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Invite;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.json.JSONObject;

import com.example.MinecraftSocietyBot;
import com.example.model.ServerConfig;

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

        TextChannel welcomeChannel = null;
        for (TextChannel c : guild.getTextChannels()) {
            if (c.getName().toLowerCase().contains("welcome")) {
                welcomeChannel = c; break;
            }
        }
        if (welcomeChannel == null) welcomeChannel = guild.getSystemChannel();
        if (welcomeChannel == null) return; 
        
        final TextChannel targetChannel = welcomeChannel;

        if (guild.getSelfMember().hasPermission(net.dv8tion.jda.api.Permission.MANAGE_SERVER)) {
            guild.retrieveInvites().queue(currentInvites -> {
                Map<String, Integer> cachedInvites = inviteCache.getOrDefault(guild.getId(), new HashMap<>());
                Invite usedInvite = null;

                for (Invite invite : currentInvites) {
                    // THE FIX: If the invite is brand new, default its previous uses to 0.
                    // Now, a new invite going from 0 to 1 will successfully trigger!
                    int cachedUses = cachedInvites.getOrDefault(invite.getCode(), 0);
                    if (invite.getUses() > cachedUses) {
                        usedInvite = invite;
                        break;
                    }
                }

                updateInviteCache(guild); 
                sendWelcomeMessage(event, targetChannel, usedInvite);
            });
        } else {
            sendWelcomeMessage(event, targetChannel, null); 
        }
    }

    private void sendWelcomeMessage(GuildMemberJoinEvent event, TextChannel channel, Invite usedInvite) {
        Guild guild = event.getGuild();
        ServerConfig config = MinecraftSocietyBot.getGuildConfig(guild.getId());
        
        if (config.welcomeMessage == null || config.welcomeMessage.trim().isEmpty()) return;

        JSONObject embedData;
        try { embedData = new JSONObject(config.welcomeMessage); } 
        catch (Exception e) { embedData = new JSONObject().put("desc", config.welcomeMessage); }

        String inviteVar = "Unknown";
        String inviterVar = "Unknown";

        if (usedInvite != null) {
            String code = usedInvite.getCode();
            String alias = config.inviteAliases != null ? config.inviteAliases.get(code) : null;

            if (alias != null) {
                // Magic Link
                inviteVar = alias + " (discord.gg/" + code + ")";
                inviterVar = "Magic Link"; 
            } else {
                // Normal Link
                inviteVar = "discord.gg/" + code;
                if (usedInvite.getInviter() != null) {
                    inviterVar = usedInvite.getInviter().getAsMention();
                }
            }
        }

        String timeVar = "<t:" + (System.currentTimeMillis() / 1000) + ":f>";

        String desc = embedData.optString("desc", "");
        
        // THE FIX: \b ensures the code looks for the EXACT word. $INVITE will no longer break $INVITER.
        desc = desc.replaceAll("(?i)\\$USER\\b", java.util.regex.Matcher.quoteReplacement(event.getUser().getAsMention()));
        desc = desc.replaceAll("(?i)\\$GUILD\\b", java.util.regex.Matcher.quoteReplacement(guild.getName()));
        desc = desc.replaceAll("(?i)\\$MEMBER_COUNT\\b", String.valueOf(guild.getMemberCount()));
        desc = desc.replaceAll("(?i)\\$INVITER\\b", java.util.regex.Matcher.quoteReplacement(inviterVar));
        desc = desc.replaceAll("(?i)\\$INVITE\\b", java.util.regex.Matcher.quoteReplacement(inviteVar));
        desc = desc.replaceAll("(?i)\\$TIME\\b", java.util.regex.Matcher.quoteReplacement(timeVar));

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
            .replaceAll("(?i)\\$MEMBER_COUNT\\b", String.valueOf(guild.getMemberCount()));
        if (!footer.isEmpty()) eb.setFooter(footer);

        channel.sendMessageEmbeds(eb.build()).queue();
    }
}