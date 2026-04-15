package com.example;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.EntitySelectInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.components.selections.EntitySelectMenu;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;

import java.util.*;
import java.util.concurrent.*;

public class ReactionRoleCommand implements ICommand {

    private static final Map<String, ReactionRoleSession> sessions = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    @Override
    public String getName() { return "reactionrole"; }
    @Override
    public String getDescription() { return "Start the interactive reaction role setup wizard."; }
    @Override
    public List<OptionData> getOptions() { return Collections.emptyList(); }
    
    @Override
    public DefaultMemberPermissions getPermission() {
        return DefaultMemberPermissions.enabledFor(Permission.MANAGE_ROLES);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String userId = event.getUser().getId();
        ReactionRoleSession session = new ReactionRoleSession();
        sessions.put(userId, session);

        EntitySelectMenu menu = EntitySelectMenu.create("reactionrole:select_channel:" + userId, EntitySelectMenu.SelectTarget.CHANNEL)
                .setChannelTypes(ChannelType.TEXT)
                .setPlaceholder("Select the destination channel")
                .build();

        event.reply("Step 1: Select the destination channel.")
                .addComponents(ActionRow.of(menu))
                .setEphemeral(true).queue();

        startTimer(userId);
    }

    private void startTimer(String userId) {
        scheduler.schedule(() -> sessions.remove(userId), 60, TimeUnit.SECONDS);
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        String userId = event.getAuthor().getId();
        if (!sessions.containsKey(userId)) return;
        ReactionRoleSession session = sessions.get(userId);

        if (session.currentStep.equals("MESSAGE")) {
            session.messageBody = event.getMessage().getContentRaw();
            session.currentStep = "BUTTON_ROLE";
            
            EntitySelectMenu roleMenu = EntitySelectMenu.create("reactionrole:select_role:" + userId, EntitySelectMenu.SelectTarget.ROLE)
                    .setPlaceholder("Select a role for the button")
                    .build();

            event.getChannel().sendMessage("Message saved. Step 3: Select the role for this button.")
                    .addComponents(ActionRow.of(roleMenu)).queue();
        } 
        else if (session.currentStep.equals("BUTTON_LABEL")) {
            session.pendingButton.label = event.getMessage().getContentRaw();
            session.currentStep = "BUTTON_EMOJI";
            event.getChannel().sendMessage("Label saved. Step 5: Type or paste the emoji for this button.").queue();
        } 
        else if (session.currentStep.equals("BUTTON_EMOJI")) {
            session.pendingButton.emoji = event.getMessage().getContentRaw();
            session.buttons.add(session.pendingButton);
            session.pendingButton = null;
            showPreview(event.getChannel(), userId, session);
        }
    }

    public void handleEntitySelect(EntitySelectInteractionEvent event) {
        String userId = event.getUser().getId();
        ReactionRoleSession session = sessions.get(userId);
        if (session == null) return;

        if (event.getComponentId().startsWith("reactionrole:select_channel:")) {
            session.targetChannel = (TextChannel) event.getMentions().getChannels().get(0);
            session.currentStep = "MESSAGE";
            event.reply("Channel set. Step 2: Type the message content in chat.").setEphemeral(true).queue();
        } 
        else if (event.getComponentId().startsWith("reactionrole:select_role:")) {
            session.pendingButton = new ReactionRoleSession.ButtonData();
            session.pendingButton.roleId = event.getMentions().getRoles().get(0).getId();
            session.currentStep = "BUTTON_LABEL";
            event.reply("Role set. Step 4: Type the label for this button in chat.").setEphemeral(true).queue();
        }
    }

    private void showPreview(net.dv8tion.jda.api.entities.channel.middleman.MessageChannel channel, String userId, ReactionRoleSession session) {
        session.currentStep = "PREVIEW";
        List<Button> previewButtons = new ArrayList<>();
        for (ReactionRoleSession.ButtonData b : session.buttons) {
            previewButtons.add(Button.secondary("preview_" + b.roleId, b.label).withEmoji(Emoji.fromFormatted(b.emoji)));
        }

        channel.sendMessage("**Preview:**\n" + session.messageBody)
                .addComponents(ActionRow.of(previewButtons))
                .addComponents(ActionRow.of(
                    Button.success("reactionrole:confirm_send:" + userId, "Send Message"),
                    Button.primary("reactionrole:add_another:" + userId, "Add Another Button")
                )).queue();
    }

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        String componentId = event.getComponentId();
        String userId = event.getUser().getId();
        ReactionRoleSession session = sessions.get(userId);

        if (componentId.startsWith("reactionrole:add_another:")) {
            if (session == null) return;
            session.currentStep = "BUTTON_ROLE";
            EntitySelectMenu roleMenu = EntitySelectMenu.create("reactionrole:select_role:" + userId, EntitySelectMenu.SelectTarget.ROLE)
                    .setPlaceholder("Select the next role")
                    .build();
            event.reply("Add another button: Select the next role.")
                 .addComponents(ActionRow.of(roleMenu)).setEphemeral(true).queue();
        } 
        else if (componentId.startsWith("reactionrole:confirm_send:")) {
            if (session == null) return;
            List<Button> finalButtons = new ArrayList<>();
            for (ReactionRoleSession.ButtonData b : session.buttons) {
                finalButtons.add(Button.secondary("reactionrole:give:" + b.roleId, b.label).withEmoji(Emoji.fromFormatted(b.emoji)));
            }
            session.targetChannel.sendMessage(session.messageBody).addComponents(ActionRow.of(finalButtons)).queue();
            sessions.remove(userId);
            event.editMessage("Message sent! Wizard closed.").setComponents().queue();
        }
        else if (componentId.startsWith("reactionrole:give:")) {
            String[] parts = componentId.split(":");
            // FIXED: Added to extract the specific Role ID from the array
            String roleId = parts[2];
            Role role = event.getGuild().getRoleById(roleId);
            
            if (role != null) {
                if (event.getMember().getRoles().contains(role)) {
                    event.getGuild().removeRoleFromMember(event.getMember(), role).queue();
                    event.reply("Removed role: " + role.getName()).setEphemeral(true).queue();
                } else {
                    event.getGuild().addRoleToMember(event.getMember(), role).queue();
                    event.reply("Added role: " + role.getName()).setEphemeral(true).queue();
                }
            }
        }
    }
}