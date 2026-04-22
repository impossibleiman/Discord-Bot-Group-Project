package com.example.commands;

import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.components.buttons.Button;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class StartEventCommand implements ICommand {
    
    // Track active event per category in guild: guildId -> categoryId
    private static final Map<String, String> activeEvents = new ConcurrentHashMap<>();

    @Override
    public String getName() { // Name of the command in Discord
        return "event";
    }

    @Override
    public String getDescription() { // Description of the command that shows up in Discord
        return "Create/Delete Voice Channels for an event.";
    }


    // ------------------------------------------------------------------------------------------------------------------
    // -----------------------------------------------Command Options----------------------------------------------------
    // ------------------------------------------------------------------------------------------------------------------
    @Override
    public List<OptionData> getOptions() {
        List<OptionData> options = new ArrayList<>();
        
        // Actions (in chronological order)

        // 1A Start - Create a series of voice channels in a category
        // 1B Append - Add more voice channels to an existing event
        // 1C End - Delete all event voice channels in a category
        
        // 2 Category - Which category to start or end the event in
        // 3 Count - How many voice channels to create
        // 4 Limit - How many users can join each voice channel (optional, default 2)
        
        options.add(new OptionData(OptionType.STRING, "action", "Action to perform (start, append, end)", true) //1
                .addChoice("start", "start") // A
                .addChoice("append", "append") // B
                .addChoice("end", "end")); // C
                
        options.add(new OptionData(OptionType.CHANNEL, "category", "The category in which to create the voice channels", false) // 2
                .setChannelTypes(ChannelType.CATEGORY));
                
        options.add(new OptionData(OptionType.INTEGER, "count", "Number of voice channels to create", false) // 3
                .addChoice("1", 1)
                .addChoice("2", 2)
                .addChoice("3", 3)
                .addChoice("4", 4)
                .addChoice("5", 5)
                .addChoice("6", 6)
                .addChoice("7", 7)
                .addChoice("8", 8)
                .addChoice("9", 9)
                .addChoice("10", 10));
                
        options.add(new OptionData(OptionType.INTEGER, "limit", "User limit for appended voice channels (optional, default 2)", false)); // 4
        
        return options;
    }

    // ------------------------------------------------------------------------------------------------------------------
    // -----------------------------------------------Error Handlers-----------------------------------------------------
    // ------------------------------------------------------------------------------------------------------------------
    // By putting all the error messages in one place, it'll help when transforming this into a configurable command via the dashboard.
    // Aside from looking nicer :)

    @Override
    public DefaultMemberPermissions getPermission() {
        return DefaultMemberPermissions.enabledFor(net.dv8tion.jda.api.Permission.MANAGE_CHANNEL);
    } // This makes the command hidden to those without the 'Manage Channels' permission

    private void replyError(SlashCommandInteractionEvent event, String message) {
        event.reply(message).setEphemeral(true).queue();
    } // Helper method to reply with an error message that only the command user can see

    private void replyButtonError(net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent event, String message) {
        event.reply(message).setEphemeral(true).queue();
    } // Helper method to reply with an error message for button interactions that only the user who clicked can see

    private void errorMissingPermission(SlashCommandInteractionEvent event) {
        replyError(event, "You need the 'Manage Channels' permission to use this command.");
    } // Missing 'Manage Channels' permission error message
    // This is a fallback in case for some reason the user can see the command.
    // I didn't need to add this but it shows I'm reinforcing every permission check possible...

    private void errorMissingAction(SlashCommandInteractionEvent event) {
        replyError(event, "You must specify an action (start, end, or append).");
    } // Missing action error message (Empty string or null)

    private void errorInvalidAction(SlashCommandInteractionEvent event) {
        replyError(event, "Invalid action specified. Use 'start', 'append', or 'end'.");
    } // Invalid action error message (String did not match any valid options)
    // There is an autofill for the action options but you can still type for some reason...

    private void errorGuildOnly(SlashCommandInteractionEvent event) {
        replyError(event, "This command can only be used in a server.");
    } // Prevents command from being used in DMs, despite me not asking for the bot to be an app
    // (Discord "recently" made all bots apps so they can be used in DMs)

    private void errorMissingCategory(SlashCommandInteractionEvent event) {
        replyError(event, "You must specify a category for this action.");
    } // Missing category (usually null) error message for actions that require a category

    private void errorInvalidCount(SlashCommandInteractionEvent event) {
        replyError(event, "Please specify a count between 1 and 10.");
    } // Invalid count error message for when the count is outside the allowed range

    private void errorBotMissingCreatePermission(SlashCommandInteractionEvent event) {
        replyError(event, "I need the 'Manage Channels' permission to create voice channels in this category.");
    } // Bot missing 'Manage Channels' permission error message for starting an event

    private void errorEventAlreadyActive(SlashCommandInteractionEvent event) {
        replyError(event, "An event is already active in this category. End it before starting a new one!");
    } // Event already active error message for when trying to start an event in a category that already has an active event

    private void errorNoActiveEvent(SlashCommandInteractionEvent event) {
        replyError(event, "No active event found in this category... Try starting an event first!");
    } // No active event error message for when trying to append an event in a inactive category

    private void errorEventCategoryNotFound(SlashCommandInteractionEvent event) {
        replyError(event, "Event category not found. Please end the event and start a new one.");
    } // Event category not found error message for when the category associated with the active event cannot be found (probably deleted manually - rare)

    private void errorInvalidUserLimit(SlashCommandInteractionEvent event) {
        replyError(event, "Please specify a user limit between 1 and 99.");
    } // Invalid user limit error message for when the user limit provided is outside Discord's allowed range

    private void errorBotMissingDeletePermission(SlashCommandInteractionEvent event) {
        replyError(event, "I need the 'Manage Channels' permission to delete voice channels in this category.");
    } // Bot missing 'Manage Channels' permission error message for ending an event

    private void errorButtonMissingPermission(net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent event) {
        replyButtonError(event, "You need the 'Manage Channels' permission to click this button!");
    } // Once an event starts, the button is visible to all

    private void errorButtonCategoryNotFound(net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent event) {
        replyButtonError(event, "Category not found. It may have already been deleted.");
    } // Event category not found error message for when the category associated with the active event cannot be found when clicking the end event button (probably deleted manually - rare)

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        if (event.getMember() == null || !event.getMember().hasPermission(net.dv8tion.jda.api.Permission.MANAGE_CHANNEL)) {
            errorMissingPermission(event);
            return;
        }

        OptionMapping actionOption = event.getOption("action");
        if (actionOption == null) {
            errorMissingAction(event);
            return;
        }

        String action = actionOption.getAsString();
        var guild = event.getGuild();
        if (guild == null) {
            errorGuildOnly(event);
            return;
        }
        String guildId = guild.getId();
        switch (action.toLowerCase()) {
            case "start" -> handleStart(event, guild, guildId);
            case "append" -> handleAppend(event, guild, guildId);
            case "end" -> handleEnd(event, guild, guildId);
            default -> errorInvalidAction(event);
        }
    }

    /// ------------------------------------------------------------------------------------------------------------------
    /// ----------------------------------------------------Start---------------------------------------------------------
    /// ------------------------------------------------------------------------------------------------------------------

    private void handleStart(SlashCommandInteractionEvent event, net.dv8tion.jda.api.entities.Guild guild, String guildId) {
        OptionMapping categoryOption = event.getOption("category");
        OptionMapping countOption = event.getOption("count");


        if (categoryOption == null) {
            errorMissingCategory(event);
            return;
        }

        var category = categoryOption.getAsChannel().asCategory();
        String categoryId = category.getId();

        int count = 1;
        if (countOption != null) {
            count = countOption.getAsInt();
            if (count < 1 || count > 10) {
                errorInvalidCount(event);
                return;
            }
        }

        if (!guild.getSelfMember().hasPermission(category, net.dv8tion.jda.api.Permission.MANAGE_CHANNEL)) {
            errorBotMissingCreatePermission(event);
            return;
        }

        if (activeEvents.containsKey(guildId)) {
            errorEventAlreadyActive(event);
            return;
        }

        for (int i = 1; i <= count; i++) {
            String channelName = "Event VC " + i;
            category.createVoiceChannel(channelName)
                    .reason("Event voice channel creation by " + event.getUser().getName())
                    .queue();
        } // This is what creates the voice channels to the user's specifications

        activeEvents.put(guildId, categoryId); // Mark this category as having an active event (categoryID)

        Button endButton = Button.danger("event:end:" + categoryId, "End Event"); // End button using categoryID

        event.reply("Event started! " + count + (count == 1 ? " voice channel" : " voice channels") + " have been created in category '" + category.getName() + "'.")
                .addComponents(ActionRow.of(endButton))
                .queue();
    } // This is the active summary message, displayed until the end button is clicked

    /// ------------------------------------------------------------------------------------------------------------------
    /// ----------------------------------------------------Append--------------------------------------------------------
    /// ------------------------------------------------------------------------------------------------------------------

    private void handleAppend(SlashCommandInteractionEvent event, net.dv8tion.jda.api.entities.Guild guild, String guildId) {
        if (!activeEvents.containsKey(guildId)) {
            errorNoActiveEvent(event);
            return;
        }

        String categoryId = activeEvents.get(guildId);
        var category = guild.getCategoryById(categoryId);
        if (category == null) {
            errorEventCategoryNotFound(event);
            return;
        }

        OptionMapping countOption = event.getOption("count");
        OptionMapping limitOption = event.getOption("limit");
        int count = 1; // Default to creating ONE VOICE CHANNEL if no count is provided
        final int userLimit = limitOption != null ? limitOption.getAsInt() : 2; // Default to a USER LIMIT of TWO if no limit is provided

        if (countOption != null) {
            count = countOption.getAsInt();
            if (count < 1 || count > 10) {
                errorInvalidCount(event);
                return;
            }
        }

        if (limitOption != null && (userLimit < 1 || userLimit > 99)) {
            errorInvalidUserLimit(event);
            return;
        }

        for (int i = 1; i <= count; i++) {
            String channelName = "Event VC " + (category.getVoiceChannels().size() + 1);
            category.createVoiceChannel(channelName)
                    .reason("Appended event voice channel by " + event.getUser().getName())
                    .queue(channel -> channel.getManager().setUserLimit(userLimit).queue());
        } // Create extra voice channels with the specified user limit to active event

        event.reply(count + (count == 1 ? " voice channel" : " voice channels") + " with user limit " + userLimit + " have been appended to the event in category '" + category.getName() + "'.").queue();
    } // Append summary message

    /// ------------------------------------------------------------------------------------------------------------------
    /// ----------------------------------------------------End-----------------------------------------------------------
    /// ------------------------------------------------------------------------------------------------------------------
     
    private void handleEnd(SlashCommandInteractionEvent event, net.dv8tion.jda.api.entities.Guild guild, String guildId) {
        OptionMapping categoryOption = event.getOption("category");
        if (categoryOption == null) {
            errorMissingCategory(event);
            return;
        }

        var category = categoryOption.getAsChannel().asCategory();

        if (!guild.getSelfMember().hasPermission(category, net.dv8tion.jda.api.Permission.MANAGE_CHANNEL)) {
            errorBotMissingDeletePermission(event);
            return;
        }

        var voiceChannels = category.getVoiceChannels();
        int deleted = 0;
        for (var vc : voiceChannels) {
            if (vc.getName().startsWith("Event VC ")) {
                vc.delete().reason("Event ended by " + event.getUser().getName()).queue();
                deleted++;
            }
        } // Delete all voice channels via a loop that checks for the "Event VC " prefix, just in case there are other voice channels in the category

        activeEvents.remove(guildId);
        event.reply("Event ended! " + deleted + (deleted == 1 ? " voice channel" : " voice channels") + " have been deleted from category '" + category.getName() + "'.").queue();
    } // End summary message

    // ------------------------------------------------------------------------------------------------------------------
    // ----------------------------------------------------Button Interaction Handler------------------------------------
    // ------------------------------------------------------------------------------------------------------------------
    
    public static void clearActiveEvent(String guildId) {
        activeEvents.remove(guildId);
    } // Helper method for the CommandManager to clear the active category for a guild when an event ends 

    @Override
    public void onButtonInteraction(net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent event) {
        String componentId = event.getComponentId(); // button ID format: "event:end:categoryId"

        if (componentId.startsWith("event:end:")) {
            if (!event.getMember().hasPermission(net.dv8tion.jda.api.Permission.MANAGE_CHANNEL)) {
                errorButtonMissingPermission(event);
                return;
            }

            String categoryId = componentId.replace("event:end:", ""); // Extract categoryId from the button's custom ID
            var category = event.getGuild().getCategoryById(categoryId);

            if (category == null) {
                errorButtonCategoryNotFound(event);
                return;
            }

            int deleted = 0;
            for (var vc : category.getVoiceChannels()) {
                if (vc.getName().startsWith("Event VC ")) {
                    vc.delete().reason("Event ended by " + event.getUser().getName()).queue();
                    deleted++;
                } // Same deletion logic as the slash command, but triggered by the button instead. This is a more user-friendly method.
            }

            clearActiveEvent(event.getGuild().getId());

            event.editMessage("Event ended! " + deleted + (deleted == 1 ? " voice channel" : " voice channels") + " were deleted.")
                 .setComponents()
                 .queue();
        } // End summary message (but from a button)
    }
}