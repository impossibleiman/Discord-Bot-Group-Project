package com.example.listeners;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Invite;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.api.events.message.MessageDeleteEvent;
import net.dv8tion.jda.api.events.message.MessageUpdateEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.json.JSONObject;

import com.example.MinecraftSocietyBot;
import com.example.managers.StickyRoleManager;
import com.example.model.MemberData;
import com.example.model.ServerConfig;

import java.awt.Color;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

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
        ServerConfig config = MinecraftSocietyBot.getGuildConfig(guild.getId());

        List<Role> restoredRoles = restoreStickyRoles(event);

        // Persist join time for leave duration calculations.
        StickyRoleManager.saveJoinTime(event.getUser().getId(), System.currentTimeMillis());

        TextChannel welcomeChannel = null;
        if (config.welcomeChannelId != null && !config.welcomeChannelId.isBlank()) {
            welcomeChannel = guild.getTextChannelById(config.welcomeChannelId);
        }

        if (welcomeChannel == null) {
            for (TextChannel c : guild.getTextChannels()) {
                if (c.getName().toLowerCase().contains("welcome")) {
                    welcomeChannel = c;
                    break;
                }
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
                sendWelcomeMessage(event, targetChannel, usedInvite, restoredRoles);
            });
        } else {
            sendWelcomeMessage(event, targetChannel, null, restoredRoles); 
        }
    }

    private List<Role> restoreStickyRoles(GuildMemberJoinEvent event) {
        Guild guild = event.getGuild();
        Member member = event.getMember();

        if (!guild.getSelfMember().hasPermission(net.dv8tion.jda.api.Permission.MANAGE_ROLES)) {
            System.out.println("Sticky roles skipped: Missing MANAGE_ROLES in " + guild.getName());
            return Collections.emptyList();
        }

        MemberData data = StickyRoleManager.getMemberData(member.getId());
        if (data == null || data.getRoleIds() == null || data.getRoleIds().isEmpty()) {
            return Collections.emptyList();
        }

        java.util.List<Role> rolesToReapply = new ArrayList<>();
        for (String roleId : data.getRoleIds()) {
            Role role = guild.getRoleById(roleId);
            if (role == null) continue;
            if (role.isManaged()) continue;
            if (role.isPublicRole()) continue;
            if (!guild.getSelfMember().canInteract(role)) continue;
            if (member.getRoles().contains(role)) continue;
            rolesToReapply.add(role);
        }

        if (rolesToReapply.isEmpty()) {
            return Collections.emptyList();
        }

        guild.modifyMemberRoles(member, rolesToReapply, Collections.emptyList()).queue(
                success -> System.out.println("Reapplied " + rolesToReapply.size() + " sticky role(s) to " + member.getUser().getName()),
                error -> System.err.println("Failed to reapply sticky roles for " + member.getUser().getName() + ": " + error.getMessage())
        );

        return rolesToReapply;
    }

    private void sendWelcomeMessage(GuildMemberJoinEvent event, TextChannel channel, Invite usedInvite, List<Role> restoredRoles) {
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
        String ageVar = "<t:" + event.getUser().getTimeCreated().toEpochSecond() + ":R>";
        String pfpVar = event.getUser().getEffectiveAvatarUrl();

        String desc = embedData.optString("desc", "");
        
        // THE FIX: \b ensures the code looks for the EXACT word. $INVITE will no longer break $INVITER.
        desc = desc.replaceAll("(?i)\\$USER\\b", java.util.regex.Matcher.quoteReplacement(event.getUser().getAsMention()));
        desc = desc.replaceAll("(?i)\\$GUILD\\b", java.util.regex.Matcher.quoteReplacement(guild.getName()));
        desc = desc.replaceAll("(?i)\\$MEMBER_COUNT\\b", String.valueOf(guild.getMemberCount()));
        desc = desc.replaceAll("(?i)\\$INVITER\\b", java.util.regex.Matcher.quoteReplacement(inviterVar));
        desc = desc.replaceAll("(?i)\\$INVITE\\b", java.util.regex.Matcher.quoteReplacement(inviteVar));
        desc = desc.replaceAll("(?i)\\$AGE\\b", java.util.regex.Matcher.quoteReplacement(ageVar));
        desc = desc.replaceAll("(?i)\\$PFP\\b", java.util.regex.Matcher.quoteReplacement(pfpVar));
        desc = desc.replaceAll("(?i)\\$TIME\\b", java.util.regex.Matcher.quoteReplacement(timeVar));

        if (restoredRoles != null && !restoredRoles.isEmpty()) {
            String restoredRoleText = restoredRoles.stream()
                .map(Role::getAsMention)
                .collect(Collectors.joining(", "));
            String stickyLine = "Sticky roles restored: " + restoredRoleText;
            desc = desc.isBlank() ? stickyLine : desc + "\n\n" + stickyLine;
        }

        EmbedBuilder eb = new EmbedBuilder();
        eb.setDescription(desc);

        String title = embedData.optString("title", "")
            .replaceAll("(?i)\\$USER\\b", java.util.regex.Matcher.quoteReplacement(event.getUser().getAsMention()))
            .replaceAll("(?i)\\$GUILD\\b", java.util.regex.Matcher.quoteReplacement(guild.getName()))
            .replaceAll("(?i)\\$MEMBER_COUNT\\b", String.valueOf(guild.getMemberCount()))
            .replaceAll("(?i)\\$INVITER\\b", java.util.regex.Matcher.quoteReplacement(inviterVar))
            .replaceAll("(?i)\\$INVITE\\b", java.util.regex.Matcher.quoteReplacement(inviteVar))
            .replaceAll("(?i)\\$AGE\\b", java.util.regex.Matcher.quoteReplacement(ageVar))
            .replaceAll("(?i)\\$PFP\\b", java.util.regex.Matcher.quoteReplacement(pfpVar))
            .replaceAll("(?i)\\$TIME\\b", java.util.regex.Matcher.quoteReplacement(timeVar));
        if (!title.isEmpty()) eb.setTitle(title);

        String colorHex = embedData.optString("color", "");
        if (!colorHex.isEmpty()) {
            try { eb.setColor(Color.decode(colorHex)); } catch (Exception ignored) {}
        }

        String thumb = embedData.optString("thumb", "")
            .replaceAll("(?i)\\$PFP\\b", java.util.regex.Matcher.quoteReplacement(pfpVar));
        if (!thumb.isEmpty()) eb.setThumbnail(thumb);

        String image = embedData.optString("image", "")
            .replaceAll("(?i)\\$PFP\\b", java.util.regex.Matcher.quoteReplacement(pfpVar));
        if (!image.isEmpty()) eb.setImage(image);

        String footer = embedData.optString("footer", "")
            .replaceAll("(?i)\\$USER\\b", java.util.regex.Matcher.quoteReplacement(event.getUser().getAsMention()))
            .replaceAll("(?i)\\$GUILD\\b", java.util.regex.Matcher.quoteReplacement(guild.getName()))
            .replaceAll("(?i)\\$MEMBER_COUNT\\b", String.valueOf(guild.getMemberCount()))
            .replaceAll("(?i)\\$INVITER\\b", java.util.regex.Matcher.quoteReplacement(inviterVar))
            .replaceAll("(?i)\\$INVITE\\b", java.util.regex.Matcher.quoteReplacement(inviteVar))
            .replaceAll("(?i)\\$AGE\\b", java.util.regex.Matcher.quoteReplacement(ageVar))
            .replaceAll("(?i)\\$PFP\\b", java.util.regex.Matcher.quoteReplacement(pfpVar))
            .replaceAll("(?i)\\$TIME\\b", java.util.regex.Matcher.quoteReplacement(timeVar));
        if (!footer.isEmpty()) eb.setFooter(footer);

        channel.sendMessageEmbeds(eb.build()).queue();
    }


//  HELPER — GET AUDIT LOG CHANNEL
private TextChannel getAuditChannel(Guild guild, boolean isDeleteEvent) {
    ServerConfig config = MinecraftSocietyBot.getGuildConfig(guild.getId());
    String configuredId = isDeleteEvent ? config.auditDeleteChannelId : config.auditEditChannelId;

    if (configuredId != null && !configuredId.isBlank()) {
        return guild.getTextChannelById(configuredId);
    }

    var channels = guild.getTextChannelsByName("audit-logs", true);
    if (channels.isEmpty()) return null;
    return channels.get(0);
}
//  MESSAGE DELETED
@Override
public void onMessageDelete(MessageDeleteEvent event) {
    if (!event.isFromGuild()) return;
    Guild guild = event.getGuild();
    TextChannel auditChannel = getAuditChannel(guild, true);
    if (auditChannel == null) return;

    EmbedBuilder audit = new EmbedBuilder();
    audit.setColor(Color.RED);
    audit.setTitle("🗑️ Message Deleted");
    audit.addField("Channel", event.getChannel().getAsMention(), true);
    audit.addField("Message ID", event.getMessageId(), true);
    audit.setFooter("Minecraft Society • Audit Log");
    audit.setTimestamp(Instant.now());
    auditChannel.sendMessageEmbeds(audit.build()).queue();


}

//  MESSAGE EDITED
@Override
public void onMessageUpdate(MessageUpdateEvent event) {
    if (!event.isFromGuild()) return;
    if (event.getAuthor().isBot()) return;
    Guild guild = event.getGuild();
    TextChannel auditChannel = getAuditChannel(guild, false);
    if (auditChannel == null) return;

    EmbedBuilder audit = new EmbedBuilder();
    audit.setColor(Color.ORANGE);
    audit.setTitle("✏️ Message Edited");
    audit.addField("Author", event.getAuthor().getAsTag(), true);
    audit.addField("Channel", event.getChannel().getAsMention(), true);
    audit.addField("New Content", event.getMessage().getContentRaw(), false);
    audit.setFooter("Minecraft Society • Audit Log");
    audit.setTimestamp(Instant.now());
    auditChannel.sendMessageEmbeds(audit.build()).queue();
    }
}