package com.example;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.components.buttons.Button;

import java.util.Collections;
import java.util.List;

public class TicketPanelCommand implements ICommand {

    private static final String STAFF_ROLE_NAME = "Admin";
    private static final String TICKET_LOG_CHANNEL_ID = "1494041507441934547";

    @Override
    public String getName() {
        return "ticketpanel";
    }

    @Override
    public String getDescription() {
        return "Sends the ticket panel.";
    }

    @Override
    public List<OptionData> getOptions() {
        return Collections.emptyList();
    }

    @Override
    public DefaultMemberPermissions getPermission() {
        return DefaultMemberPermissions.enabledFor(Permission.MESSAGE_SEND);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        event.reply("""
                **Support Tickets**
                                
                Click one of the buttons below to open a ticket.
                                
                **General Help** - for normal support questions
                **Purchased Support** - for purchase-related help
                """)
                .addActionRow(
                        Button.primary("ticketpanel:general", "General Help"),
                        Button.success("ticketpanel:purchase", "Purchased Support")
                )
                .queue();
    }

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        String buttonId = event.getComponentId();

        if (!buttonId.equals("ticketpanel:close")
                && !buttonId.equals("ticketpanel:general")
                && !buttonId.equals("ticketpanel:purchase")) {
            return;
        }

        if (buttonId.equals("ticketpanel:general") || buttonId.equals("ticketpanel:purchase")) {
            event.deferReply(true).queue(hook -> {
                if (buttonId.equals("ticketpanel:general")) {
                    TicketManager.createTicket(event, hook, "general-help");
                } else {
                    TicketManager.createTicket(event, hook, "purchased-support");
                }
            });
            return;
        }

        if (buttonId.equals("ticketpanel:close")) {
            if (event.getMember() == null || event.getGuild() == null) {
                event.reply("This can only be used in a server.")
                        .setEphemeral(true)
                        .queue();
                return;
            }

            boolean isStaff = event.getMember().getRoles().stream()
                    .anyMatch(role -> role.getName().equalsIgnoreCase(STAFF_ROLE_NAME));

            if (!isStaff) {
                event.reply("You are not allowed to close this ticket.")
                        .setEphemeral(true)
                        .queue();
                return;
            }

            TextChannel logChannel = event.getGuild().getTextChannelById(TICKET_LOG_CHANNEL_ID);

            String closedBy = event.getUser().getAsTag();
            String ticketName = event.getChannel().getName();

            if (logChannel != null) {
                logChannel.sendMessage("""
                        **Ticket Closed**
                                                
                        Closed by: %s
                        Channel: %s
                        """.formatted(closedBy, ticketName)).queue();
            }

            event.deferReply(true).queue();

            event.getChannel().delete().queue(
                    success -> {
                        // channel deleted successfully
                    },
                    failure -> event.getHook()
                            .editOriginal("Failed to close ticket.")
                            .queue()
            );
        }
    }
}