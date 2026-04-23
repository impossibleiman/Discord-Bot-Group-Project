package com.example.commands;

import com.example.MinecraftSocietyBot;
import com.example.ai.OpenRouterService;
import com.example.model.ServerConfig;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.stream.Collectors;

public class AiProfileCommand implements ICommand {

    @Override
    public String getName() {
        return "ai-profile";
    }

    @Override
    public String getDescription() {
        return "Switch the active AI prompt profile.";
    }

    @Override
    public List<OptionData> getOptions() {
        List<OptionData> options = new ArrayList<>();

        options.add(new OptionData(OptionType.STRING, "profile", "Profile name", true)
            .setAutoComplete(true));

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

        String guildId = event.getGuild().getId();
        ServerConfig config = MinecraftSocietyBot.getGuildConfig(guildId);
        Map<String, String> profiles = config.aiProfiles;
        Map<String, String> descriptions = config.aiProfileDescriptions;

        OptionMapping profileOption = event.getOption("profile");
        String requestedProfile = profileOption != null ? profileOption.getAsString().trim() : "";

        if (requestedProfile.isEmpty()) {
            event.reply("Choose a profile from autocomplete.").setEphemeral(true).queue();
            return;
        }

        String resolvedProfile = resolveProfileName(requestedProfile, profiles);
        if (resolvedProfile == null) {
            event.reply(buildProfileChoicesMessage("That profile does not exist.", config, profiles, descriptions)).setEphemeral(true).queue();
            return;
        }

        config.activeAiProfileName = resolvedProfile;
        MinecraftSocietyBot.saveGuildConfig(guildId, config);
        event.reply("AI profile switched to **" + resolvedProfile + "**.").setEphemeral(true).queue();
    }

    @Override
    public void onAutoCompleteInteraction(CommandAutoCompleteInteractionEvent event) {
        if (!"profile".equals(event.getFocusedOption().getName())) {
            return;
        }

        if (event.getGuild() == null) {
            event.replyChoices(List.of()).queue();
            return;
        }

        String guildId = event.getGuild().getId();
        ServerConfig config = MinecraftSocietyBot.getGuildConfig(guildId);
        Map<String, String> profiles = config.aiProfiles;
        Map<String, String> descriptions = config.aiProfileDescriptions;
        String focused = event.getFocusedOption().getValue().toLowerCase(Locale.ROOT);

        List<Command.Choice> choices = profiles.entrySet().stream()
                .filter(entry -> entry.getKey().toLowerCase(Locale.ROOT).contains(focused))
                .sorted(Map.Entry.comparingByKey(String.CASE_INSENSITIVE_ORDER))
                .limit(25)
                .map(entry -> {
                    String description = descriptions != null ? descriptions.get(entry.getKey()) : null;
                    String name = buildAutocompleteLabel(entry.getKey(), description, entry.getValue());
                    return new Command.Choice(name, entry.getKey());
                })
                .collect(Collectors.toList());

        event.replyChoices(choices).queue();
    }

    private String resolveProfileName(String requestedProfile, Map<String, String> profiles) {
        if (requestedProfile == null || profiles == null || profiles.isEmpty()) {
            return null;
        }

        if (profiles.containsKey(requestedProfile)) {
            return requestedProfile;
        }

        for (String profileName : profiles.keySet()) {
            if (profileName.equalsIgnoreCase(requestedProfile)) {
                return profileName;
            }
        }

        return null;
    }

    private String buildProfileChoicesMessage(String prefix, ServerConfig config, Map<String, String> profiles, Map<String, String> descriptions) {
        StringBuilder message = new StringBuilder();
        if (prefix != null && !prefix.isBlank()) {
            message.append(prefix).append("\n\n");
        }

        message.append("Active profile: ")
                .append(formatProfileName(config.activeAiProfileName))
                .append("\nAvailable profiles:\n");

        for (Map.Entry<String, String> entry : profiles.entrySet()) {
            String profileName = entry.getKey();
            String description = descriptions != null ? descriptions.get(profileName) : null;
            message.append("- ")
                    .append(profileName)
                    .append(": ")
                .append(resolveDescription(description, entry.getValue()))
                    .append(profileName.equals(config.activeAiProfileName) ? " (active)" : "")
                    .append('\n');
        }

        message.append("\nUse /ai-profile and pick a profile from autocomplete.");
        return message.toString();
    }

    private String buildAutocompleteLabel(String profileName, String description, String prompt) {
        String summary = resolveDescription(description, prompt);
        String combined = profileName + " - " + summary;
        return combined.length() > 100 ? combined.substring(0, 97) + "..." : combined;
    }

    private String resolveDescription(String description, String prompt) {
        if (description != null && !description.isBlank()) {
            String trimmed = description.trim();
            return trimmed.length() > 60 ? trimmed.substring(0, 57) + "..." : trimmed;
        }
        return extractPromptSummary(prompt);
    }

    private String extractPromptSummary(String prompt) {
        if (prompt == null || prompt.isBlank()) {
            return "No description";
        }

        String normalized = prompt.replace('\n', ' ').trim();
        int periodIndex = normalized.indexOf('.');
        String summary = periodIndex > 0 ? normalized.substring(0, periodIndex + 1) : normalized;
        if (summary.length() > 60) {
            summary = summary.substring(0, 57) + "...";
        }
        return summary;
    }

    private String formatProfileName(String profileName) {
        return profileName == null || profileName.isBlank() ? OpenRouterService.DEFAULT_PROFILE_NAME : profileName;
    }
}
