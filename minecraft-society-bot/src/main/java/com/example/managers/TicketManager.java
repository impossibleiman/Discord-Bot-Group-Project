package com.example.managers;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;

import java.awt.Color;
import java.util.EnumSet;

public class TicketManager {

    public static void createTicket(ButtonInteractionEvent event, InteractionHook hook, String type) {
        if (event.getGuild() == null || event.getMember() == null) {
            hook.editOriginal("This can only be used in a server.").queue();
            return;
        }

        Member botMember = event.getGuild().getSelfMember();

        String username = event.getUser().getName()
                .toLowerCase()
                .replaceAll("[^a-z0-9]", "");

        String channelName = type + "-" + username;

        boolean exists = event.getGuild().getTextChannels().stream()
                .anyMatch(channel -> channel.getName().equalsIgnoreCase(channelName));

        if (exists) {
            hook.editOriginal("You already have an open ticket: **" + channelName + "**").queue();
            return;
        }

        event.getGuild().createTextChannel(channelName)
                .addPermissionOverride(
                        event.getGuild().getPublicRole(),
                        null,
                        EnumSet.of(Permission.VIEW_CHANNEL)
                )
                .addPermissionOverride(
                        event.getMember(),
                        EnumSet.of(
                                Permission.VIEW_CHANNEL,
                                Permission.MESSAGE_SEND,
                                Permission.MESSAGE_HISTORY
                        ),
                        null
                )
                .addPermissionOverride(
                        botMember,
                        EnumSet.of(
                                Permission.VIEW_CHANNEL,
                                Permission.MESSAGE_SEND,
                                Permission.MESSAGE_HISTORY,
                                Permission.MANAGE_CHANNEL
                        ),
                        null
                )
                .queue(
                        channel -> sendTicketIntro(event, hook, channel, type),
                        failure -> {
                            failure.printStackTrace();
                            hook.editOriginal("Failed to create the ticket channel. Please check my permissions.")
                                    .queue();
                        }
                );
    }

    private static void sendTicketIntro(ButtonInteractionEvent event, InteractionHook hook, TextChannel channel, String type) {
        EmbedBuilder embed = new EmbedBuilder();
        embed.setTitle("🎫 Ticket Created");
        embed.setDescription("A member of staff will be with you soon.");
        embed.addField("User", event.getUser().getAsMention(), false);
        embed.addField("Type", formatTicketType(type), false);
        embed.setColor(new Color(0, 255, 170));

        channel.sendMessageEmbeds(embed.build())
                .queue(
                        success -> hook.editOriginal("Your ticket has been created: " + channel.getAsMention()).queue(),
                        failure -> {
                            failure.printStackTrace();
                            hook.editOriginal("The ticket channel was created, but I could not send the intro message.")
                                    .queue();
                        }
                );

        channel.sendMessage("Click below to close this ticket.")
                .addComponents(ActionRow.of(
                        Button.danger("ticketpanel:close", "Close Ticket")
                ))
                .queue();
    }

    private static String formatTicketType(String type) {
        return switch (type) {
            case "general-help" -> "General Help";
            case "purchased-support" -> "Purchased Support";
            default -> type;
        };
    }
}