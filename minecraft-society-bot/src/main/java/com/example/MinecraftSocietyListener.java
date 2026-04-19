package com.example;

import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.entities.Invite;
import net.dv8tion.jda.api.EmbedBuilder;


import java.awt.Color;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class MinecraftSocietyListener extends ListenerAdapter {

    // This stores the state of invites: Map<GuildID_InviteCode, Uses>
    private final Map<String, Integer> inviteCache = new ConcurrentHashMap<>();

    // Call this when the bot starts up, or when an invite is created/deleted
    public void updateInviteCache(net.dv8tion.jda.api.entities.Guild guild) {
        if (!guild.getSelfMember().hasPermission(net.dv8tion.jda.api.Permission.MANAGE_SERVER)) return;
        
        guild.retrieveInvites().queue(invites -> {
            for (Invite invite : invites) {
                inviteCache.put(guild.getId() + "_" + invite.getCode(), invite.getUses());
            }
            System.out.println("✅ Invite cache updated for: " + guild.getName());
        });
    }

@Override
    public void onGuildMemberJoin(GuildMemberJoinEvent event) {
        var user = event.getUser();
        var guild = event.getGuild();

        // 1. FETCH CONFIGURATION
        ServerConfig config = MinecraftSocietyBot.getGuildConfig(guild.getId());
        
        // If there's no config or no embed set up, do nothing.
        if (config == null || config.welcomeEmbed == null) return;
        ServerConfig.WelcomeEmbedConfig emb = config.welcomeEmbed;

        // 2. FIGURE OUT WHICH INVITE WAS USED
        guild.retrieveInvites().queue(currentInvites -> {
            Invite usedInvite = null;
            
            for (Invite invite : currentInvites) {
                String cacheKey = guild.getId() + "_" + invite.getCode();
                int cachedUses = inviteCache.getOrDefault(cacheKey, 0);
                
                if (invite.getUses() > cachedUses) {
                    usedInvite = invite;
                    break;
                }
            }

            updateInviteCache(guild);

            // 3. DETERMINE THE ALIAS AND INVITER
            String aliasName = "Unknown Link";
            String inviterName = "Unknown";
            
            if (usedInvite != null) {
                if (config.inviteAliases != null && config.inviteAliases.containsKey(usedInvite.getCode())) {
                    aliasName = config.inviteAliases.get(usedInvite.getCode()) + " (" + usedInvite.getCode() + ")";
                } else {
                    aliasName = "Standard Link (" + usedInvite.getCode() + ")";
                }
                if (usedInvite.getInviter() != null) inviterName = usedInvite.getInviter().getName();
            }

            // 4. BUILD THE EMBED DIRECTLY FROM THE OBJECT
            try {
                EmbedBuilder eb = new EmbedBuilder();

                // Description with variable replacement
                String desc = emb.description != null ? emb.description : "";
                desc = desc.replace("$USER", user.getAsMention())
                           .replace("$GUILD", guild.getName())
                           .replace("$INVITE_ALIAS", aliasName)
                           .replace("$INVITER", inviterName);
                if (!desc.isEmpty()) eb.setDescription(desc);

                // Footer with variable replacement
                String footer = emb.footerText != null ? emb.footerText : "";
                footer = footer.replace("$MEMBER_COUNT", String.valueOf(guild.getMemberCount()));
                if (!footer.isEmpty()) eb.setFooter(footer);

                // Basic Embed Properties
                if (emb.title != null && !emb.title.isEmpty()) eb.setTitle(emb.title);
                if (emb.color != null && !emb.color.isEmpty()) eb.setColor(Color.decode(emb.color));
                if (emb.thumbnail != null && !emb.thumbnail.isEmpty()) eb.setThumbnail(emb.thumbnail);
                if (emb.image != null && !emb.image.isEmpty()) eb.setImage(emb.image);
                
                // Author properties
                if (emb.authorName != null && !emb.authorName.isEmpty()) {
                    if (emb.authorIcon != null && !emb.authorIcon.isEmpty()) {
                        eb.setAuthor(emb.authorName, null, emb.authorIcon);
                    } else {
                        eb.setAuthor(emb.authorName);
                    }
                }

                // 5. SEND IT TO THE SYSTEM CHANNEL
                var sysChannel = guild.getSystemChannel();
                if (sysChannel != null && sysChannel.canTalk()) {
                    sysChannel.sendMessageEmbeds(eb.build()).queue();
                }

            } catch (Exception e) {
                System.err.println("Failed to build welcome embed: " + e.getMessage());
            }
        });
    }
}