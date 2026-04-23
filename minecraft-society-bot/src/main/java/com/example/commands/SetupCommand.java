package com.example.commands;

import com.example.MinecraftSocietyBot;
import com.example.model.ServerConfig;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.selections.EntitySelectMenu;
import net.dv8tion.jda.api.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.EntitySelectInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

import java.util.Collections;
import java.util.List;

public class SetupCommand implements ICommand {

    private static final String ID_PREFIX = "setup";

    @Override
    public String getName() {
        return "setup";
    }

    @Override
    public String getDescription() {
        return "Start the interactive setup wizard for dashboard or Discord channel routing.";
    }

    @Override
    public List<OptionData> getOptions() {
        return Collections.emptyList();
    }

    @Override
    public DefaultMemberPermissions getPermission() {
        return DefaultMemberPermissions.enabledFor(net.dv8tion.jda.api.Permission.MANAGE_SERVER);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        if (event.getGuild() == null) {
            event.reply("This command can only be used in a server.").setEphemeral(true).queue();
            return;
        }

        event.reply("How would you like to set up the bot?")
                .setEphemeral(true)
                .addComponents(ActionRow.of(
                        Button.primary(componentId("dashboard", event.getUser().getId()), "Use Dashboard"),
                        Button.primary(componentId("discord", event.getUser().getId()), "Configure in Discord"),
                        Button.danger(componentId("cancel", event.getUser().getId()), "Cancel")
                ))
                .queue();
    }

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        String componentId = event.getComponentId();
        if (!componentId.startsWith(ID_PREFIX + ":")) {
            return;
        }

        String[] parts = componentId.split(":", 3);
        if (parts.length < 3) {
            return;
        }

        String action = parts[1];
        String userId = parts[2];
        if (!event.getUser().getId().equals(userId)) {
            event.reply("Only the user who started this setup can use these controls.").setEphemeral(true).queue();
            return;
        }

        switch (action) {
            case "dashboard" -> showDashboardLink(event, userId);
            case "discord" -> showTargetPicker(event, userId);
            case "restart" -> showStartPrompt(event, userId);
            case "cancel" -> finishWizard(event, "Setup cancelled.");
            case "done" -> finishWizard(event, "Setup complete.");
            case "back" -> showTargetPicker(event, userId);
            default -> {
            }
        }
    }

    @Override
    public void onStringSelectInteraction(StringSelectInteractionEvent event) {
        String componentId = event.getComponentId();
        if (!componentId.startsWith(ID_PREFIX + ":target:")) {
            return;
        }

        String[] parts = componentId.split(":", 3);
        if (parts.length < 3) {
            return;
        }

        String userId = parts[2];
        if (!event.getUser().getId().equals(userId)) {
            event.reply("Only the user who started this setup can use these controls.").setEphemeral(true).queue();
            return;
        }

        String target = event.getValues().isEmpty() ? null : event.getValues().get(0);
        if (target == null || target.isBlank()) {
            event.reply("Please choose a target.").setEphemeral(true).queue();
            return;
        }

        event.deferEdit().queue(ignored -> showChannelPicker(event, userId, target));
    }

    @Override
    public void onChannelSelectInteraction(EntitySelectInteractionEvent event) {
        String componentId = event.getComponentId();
        if (!componentId.startsWith(ID_PREFIX + ":channel:")) {
            return;
        }

        String[] parts = componentId.split(":", 4);
        if (parts.length < 4) {
            return;
        }

        String userId = parts[2];
        String target = parts[3];
        if (!event.getUser().getId().equals(userId)) {
            event.reply("Only the user who started this setup can use these controls.").setEphemeral(true).queue();
            return;
        }

        if (event.getMentions().getChannels().isEmpty()) {
            event.reply("Please choose a text channel.").setEphemeral(true).queue();
            return;
        }

        String channelId = event.getMentions().getChannels().get(0).getId();
        String label = setTarget(event.getGuild().getId(), target, channelId);
        if (label == null) {
            event.reply("Unknown setup target.").setEphemeral(true).queue();
            return;
        }

        ServerConfig config = MinecraftSocietyBot.getGuildConfig(event.getGuild().getId());
        event.deferEdit().queue(ignored -> event.getHook().editOriginal("Updated " + label + " to <#" + channelId + ">. Choose another target or finish.")
            .setComponents(
                ActionRow.of(targetMenu(userId, config)),
                ActionRow.of(
                    Button.secondary(componentId("done", userId), "Done"),
                    Button.secondary(componentId("discord", userId), "Back"),
                    Button.danger(componentId("cancel", userId), "Cancel")
                )
            )
            .queue());
    }

    private void showStartPrompt(ButtonInteractionEvent event, String userId) {
        event.editMessage("How would you like to set up the bot?")
                .setComponents(ActionRow.of(
                        Button.primary(componentId("dashboard", userId), "Use Dashboard"),
                        Button.primary(componentId("discord", userId), "Configure in Discord"),
                        Button.danger(componentId("cancel", userId), "Cancel")
                ))
                .queue();
    }

    private void showDashboardLink(ButtonInteractionEvent event, String userId) {
        event.editMessage("Use the dashboard login page to configure your channels and AI profiles.")
                .setComponents(ActionRow.of(
                        Button.link(MinecraftSocietyBot.DASHBOARD_LOGIN_URL, "Open Dashboard Login"),
                        Button.secondary(componentId("restart", userId), "Back"),
                        Button.danger(componentId("cancel", userId), "Cancel")
                ))
                .queue();
    }

    private void showTargetPicker(ButtonInteractionEvent event, String userId) {
        ServerConfig config = MinecraftSocietyBot.getGuildConfig(event.getGuild().getId());
        event.editMessage("Step 1: Choose what you want to configure.")
                .setComponents(
                        ActionRow.of(targetMenu(userId, config)),
                        ActionRow.of(
                                Button.secondary(componentId("restart", userId), "Back"),
                                Button.danger(componentId("cancel", userId), "Cancel")
                        )
                )
                .queue();
    }

    private void showChannelPicker(StringSelectInteractionEvent event, String userId, String target) {
        String label = targetLabel(target);
        if (label == null) {
            event.reply("Unknown setup target.").setEphemeral(true).queue();
            return;
        }

        ServerConfig config = MinecraftSocietyBot.getGuildConfig(event.getGuild().getId());
        event.getHook().editOriginal("Step 2: Choose the channel for " + label + ".")
                .setComponents(
                        ActionRow.of(channelMenu(userId, target, config)),
                        ActionRow.of(
                                Button.secondary(componentId("back", userId), "Back"),
                                Button.danger(componentId("cancel", userId), "Cancel")
                        )
                )
                .queue();
    }

    private StringSelectMenu targetMenu(String userId, ServerConfig config) {
        StringSelectMenu.Builder builder = StringSelectMenu.create(componentId("target", userId))
                .setPlaceholder("Choose a setting to configure")
                .setRequiredRange(1, 1)
                .addOption("Welcome Channel", "welcome", "Where welcome messages are posted")
                .addOption("Leave Channel", "leave", "Where leave messages are posted")
                .addOption("Audit Edit Channel", "audit_edit", "Where message edit logs are posted")
                .addOption("Audit Delete Channel", "audit_delete", "Where message delete logs are posted")
                .addOption("AI Chat Channel", "ai_chat", "Where the AI listens and replies");

        return builder.build();
    }

    private EntitySelectMenu channelMenu(String userId, String target, ServerConfig config) {
        EntitySelectMenu.Builder builder = EntitySelectMenu.create(componentId("channel", userId, target), EntitySelectMenu.SelectTarget.CHANNEL)
                .setChannelTypes(ChannelType.TEXT)
                .setPlaceholder("Select a text channel")
                ;

        String currentChannelId = currentChannelId(config, target);
        if (currentChannelId != null && !currentChannelId.isBlank()) {
            try {
                builder.setDefaultValues(EntitySelectMenu.DefaultValue.channel(Long.parseLong(currentChannelId)));
            } catch (NumberFormatException ignoredException) {
                // Ignore malformed stored channel ids and just render the menu without a default.
            }
        }

        return builder.build();
    }

    private String componentId(String action, String userId) {
        return ID_PREFIX + ":" + action + ":" + userId;
    }

    private String componentId(String action, String userId, String target) {
        return ID_PREFIX + ":" + action + ":" + userId + ":" + target;
    }

    private String setTarget(String guildId, String target, String channelId) {
        ServerConfig config = MinecraftSocietyBot.getGuildConfig(guildId);
        String label = switch (target) {
            case "welcome" -> {
                config.welcomeChannelId = channelId;
                yield "welcome channel";
            }
            case "leave" -> {
                config.leaveChannelId = channelId;
                yield "leave channel";
            }
            case "audit_edit" -> {
                config.auditEditChannelId = channelId;
                yield "audit edit channel";
            }
            case "audit_delete" -> {
                config.auditDeleteChannelId = channelId;
                yield "audit delete channel";
            }
            case "ai_chat" -> {
                config.aiChannelId = channelId;
                yield "AI chat channel";
            }
            default -> null;
        };

        if (label != null) {
            MinecraftSocietyBot.saveGuildConfig(guildId, config);
        }

        return label;
    }

    private String targetLabel(String target) {
        return switch (target) {
            case "welcome" -> "welcome messages";
            case "leave" -> "leave messages";
            case "audit_edit" -> "message edit logs";
            case "audit_delete" -> "message delete logs";
            case "ai_chat" -> "AI chat";
            default -> null;
        };
    }

    private String currentChannelId(ServerConfig config, String target) {
        return switch (target) {
            case "welcome" -> config.welcomeChannelId;
            case "leave" -> config.leaveChannelId;
            case "audit_edit" -> config.auditEditChannelId;
            case "audit_delete" -> config.auditDeleteChannelId;
            case "ai_chat" -> config.aiChannelId;
            default -> null;
        };
    }

    private void finishWizard(ButtonInteractionEvent event, String message) {
        event.editMessage(message).setComponents(java.util.Collections.emptyList()).queue();
    }
}
