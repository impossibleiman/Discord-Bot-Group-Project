package com.example.commands;

import com.example.MinecraftSocietyBot;
import com.example.model.ServerConfig;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

import java.util.ArrayList;
import java.util.List;

public class SetupCommand implements ICommand {

    @Override
    public String getName() {
        return "setup";
    }

    @Override
    public String getDescription() {
        return "Configure channels for welcome, leave, audit logs, and AI chat.";
    }

    @Override
    public List<OptionData> getOptions() {
        List<OptionData> options = new ArrayList<>();

        options.add(new OptionData(OptionType.STRING, "action", "Action to perform", true)
                .addChoice("set", "set")
                .addChoice("clear", "clear")
                .addChoice("view", "view"));

        options.add(new OptionData(OptionType.STRING, "target", "What to configure", false)
                .addChoice("welcome", "welcome")
                .addChoice("leave", "leave")
                .addChoice("audit_edit", "audit_edit")
                .addChoice("audit_delete", "audit_delete")
                .addChoice("ai_chat", "ai_chat"));

        options.add(new OptionData(OptionType.CHANNEL, "channel", "Target text channel", false)
                .setChannelTypes(ChannelType.TEXT));

        return options;
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

        OptionMapping actionOption = event.getOption("action");
        String action = actionOption != null ? actionOption.getAsString().toLowerCase() : "";

        String guildId = event.getGuild().getId();
        ServerConfig config = MinecraftSocietyBot.getGuildConfig(guildId);

        switch (action) {
            case "view" -> handleView(event, config, event.getOption("target"));
            case "set" -> handleSet(event, guildId, config, event.getOption("target"), event.getOption("channel"));
            case "clear" -> handleClear(event, guildId, config, event.getOption("target"));
            default -> event.reply("Invalid action. Use set, clear, or view.").setEphemeral(true).queue();
        }
    }

    private void handleView(SlashCommandInteractionEvent event, ServerConfig config, OptionMapping targetOption) {
        if (targetOption == null) {
            event.reply(buildAllConfigMessage(config)).setEphemeral(true).queue();
            return;
        }

        String target = targetOption.getAsString().toLowerCase();
        event.reply(buildSingleConfigMessage(target, config)).setEphemeral(true).queue();
    }

    private void handleSet(
            SlashCommandInteractionEvent event,
            String guildId,
            ServerConfig config,
            OptionMapping targetOption,
            OptionMapping channelOption
    ) {
        if (targetOption == null) {
            event.reply("You must provide a target to set.").setEphemeral(true).queue();
            return;
        }

        if (channelOption == null) {
            event.reply("You must provide a channel for set action.").setEphemeral(true).queue();
            return;
        }

        String target = targetOption.getAsString().toLowerCase();
        String channelId = channelOption.getAsChannel().getId();

        String label = setTarget(config, target, channelId);
        if (label == null) {
            event.reply("Invalid target. Use welcome, leave, audit_edit, audit_delete, or ai_chat.")
                    .setEphemeral(true)
                    .queue();
            return;
        }

        MinecraftSocietyBot.saveGuildConfig(guildId, config);
        event.reply("Updated " + label + " to <#" + channelId + ">.").setEphemeral(true).queue();
    }

    private void handleClear(
            SlashCommandInteractionEvent event,
            String guildId,
            ServerConfig config,
            OptionMapping targetOption
    ) {
        if (targetOption == null) {
            event.reply("You must provide a target to clear.").setEphemeral(true).queue();
            return;
        }

        String target = targetOption.getAsString().toLowerCase();
        String label = setTarget(config, target, null);
        if (label == null) {
            event.reply("Invalid target. Use welcome, leave, audit_edit, audit_delete, or ai_chat.")
                    .setEphemeral(true)
                    .queue();
            return;
        }

        MinecraftSocietyBot.saveGuildConfig(guildId, config);
        event.reply("Cleared " + label + " configuration.").setEphemeral(true).queue();
    }

    private String setTarget(ServerConfig config, String target, String channelId) {
        return switch (target) {
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
    }

    private String buildSingleConfigMessage(String target, ServerConfig config) {
        return switch (target) {
            case "welcome" -> "welcome: " + formatChannel(config.welcomeChannelId);
            case "leave" -> "leave: " + formatChannel(config.leaveChannelId);
            case "audit_edit" -> "audit_edit: " + formatChannel(config.auditEditChannelId);
            case "audit_delete" -> "audit_delete: " + formatChannel(config.auditDeleteChannelId);
            case "ai_chat" -> "ai_chat: " + formatChannel(config.aiChannelId);
            default -> "Invalid target. Use welcome, leave, audit_edit, audit_delete, or ai_chat.";
        };
    }

    private String buildAllConfigMessage(ServerConfig config) {
        return "Current setup:\n"
                + "welcome: " + formatChannel(config.welcomeChannelId) + "\n"
                + "leave: " + formatChannel(config.leaveChannelId) + "\n"
                + "audit_edit: " + formatChannel(config.auditEditChannelId) + "\n"
                + "audit_delete: " + formatChannel(config.auditDeleteChannelId) + "\n"
                + "ai_chat: " + formatChannel(config.aiChannelId);
    }

    private String formatChannel(String channelId) {
        return channelId == null || channelId.isBlank() ? "not set" : "<#" + channelId + ">";
    }
}
