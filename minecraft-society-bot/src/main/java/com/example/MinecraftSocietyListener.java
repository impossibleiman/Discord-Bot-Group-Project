package com.example;

import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;

import java.awt.Color;
import java.util.List;
import java.util.function.Function;

public class MinecraftSocietyListener extends ListenerAdapter {

    // (Keep your existing updateInviteCache methods here if you have them)

    @Override
    public void onGuildMemberJoin(GuildMemberJoinEvent event) {
        // 1. Get the server configuration
        ServerConfig config = MinecraftSocietyBot.getGuildConfig(event.getGuild().getId());

        // 2. Find the target welcome channel (Checks for "test-welcome", falls back to System Channel)
        List<TextChannel> channels = event.getGuild().getTextChannelsByName("test-welcome", true);
        TextChannel targetChannel = !channels.isEmpty() ? channels.get(0) : event.getGuild().getSystemChannel();

        if (targetChannel == null) return; // Stop if there's no channel to send messages to

        // 3. Build and send the Embed (Only if a description was provided)
        if (config != null && config.welcomeEmbed != null && 
            config.welcomeEmbed.description != null && !config.welcomeEmbed.description.isEmpty()) {

            ServerConfig.WelcomeEmbedConfig data = config.welcomeEmbed;
            EmbedBuilder embed = new EmbedBuilder();

            // Formatter to quickly replace variables in any string
            Function<String, String> formatVars = str -> {
                if (str == null || str.isEmpty()) return null;
                return str.replace("$USER", event.getUser().getAsMention())
                          .replace("$USERNAME", event.getUser().getName())
                          .replace("$GUILD", event.getGuild().getName())
                          .replace("$MEMBER_COUNT", String.valueOf(event.getGuild().getMemberCount()));
            };

            // Apply Description & Title
            embed.setDescription(formatVars.apply(data.description));
            if (data.title != null && !data.title.isEmpty()) {
                embed.setTitle(formatVars.apply(data.title), data.url != null && !data.url.isEmpty() ? data.url : null);
            }

            // Apply Author Block
            if (data.authorName != null && !data.authorName.isEmpty()) {
                embed.setAuthor(formatVars.apply(data.authorName), null, 
                                data.authorIcon != null && !data.authorIcon.isEmpty() ? data.authorIcon : null);
            }

            // Apply Images
            if (data.thumbnail != null && !data.thumbnail.isEmpty()) embed.setThumbnail(data.thumbnail);
            if (data.image != null && !data.image.isEmpty()) embed.setImage(data.image);

            // Apply Footer
            if (data.footerText != null && !data.footerText.isEmpty()) {
                embed.setFooter(formatVars.apply(data.footerText), 
                                data.footerIcon != null && !data.footerIcon.isEmpty() ? data.footerIcon : null);
            }

            // Apply Sidebar Color (Safely catches invalid hex codes)
            if (data.color != null && data.color.startsWith("#")) {
                try {
                    embed.setColor(Color.decode(data.color));
                } catch (NumberFormatException ignored) {
                    System.err.println("Invalid hex code used for welcome embed: " + data.color);
                }
            }

            // Send the completed embed
            targetChannel.sendMessageEmbeds(embed.build()).queue();
        }
    }
}