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
    
    // Track active event per guild: guildId -> categoryId
    private static final Map<String, String> activeEvents = new ConcurrentHashMap<>();

    // Helper method for the CommandManager to clear the event when the button is clicked!
    public static void clearActiveEvent(String guildId) {
        activeEvents.remove(guildId);
    }

    @Override
    public String getName() {
        return "event";
    }

    @Override
    public String getDescription() {
        return "Create/Delete Voice Channels for an event.";
    }

    @Override
    public List<OptionData> getOptions() {
        List<OptionData> options = new ArrayList<>();
        
        options.add(new OptionData(OptionType.STRING, "action", "Action to perform (start, append, end)", true)
                .addChoice("start", "start")
                .addChoice("append", "append")
                .addChoice("end", "end"));
                
        options.add(new OptionData(OptionType.CHANNEL, "category", "The category in which to create the voice channels", false)
                .setChannelTypes(ChannelType.CATEGORY));
                
        options.add(new OptionData(OptionType.INTEGER, "count", "Number of voice channels to create", false)
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
                
        options.add(new OptionData(OptionType.INTEGER, "limit", "User limit for appended voice channels (optional, default 2)", false));
        
        return options;
    }

    @Override
    public DefaultMemberPermissions getPermission() {
        return DefaultMemberPermissions.enabledFor(net.dv8tion.jda.api.Permission.MANAGE_CHANNEL);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        if (event.getMember() == null || !event.getMember().hasPermission(net.dv8tion.jda.api.Permission.MANAGE_CHANNEL)) {
            event.reply("You need the 'Manage Channels' permission to use this command.").setEphemeral(true).queue();
            return;
        }

        OptionMapping actionOption = event.getOption("action");
        if (actionOption == null) {
            event.reply("You must specify an action (start, end, or append).").setEphemeral(true).queue();
            return;
        }

        String action = actionOption.getAsString();
        var guild = event.getGuild();
        if (guild == null) {
            event.reply("This command can only be used in a server.").setEphemeral(true).queue();
            return;
        }
        String guildId = guild.getId();

        if (action.equalsIgnoreCase("start")) {
            OptionMapping categoryOption = event.getOption("category");
            OptionMapping countOption = event.getOption("count");
            
            if (categoryOption == null) {
                event.reply("You must specify a category for the event.").setEphemeral(true).queue();
                return;
            }
            
            var category = categoryOption.getAsChannel().asCategory();
            String categoryId = category.getId();
            
            int count = 1;
            if (countOption != null) {
                count = countOption.getAsInt();
                // We keep this check just in case, but Discord's UI now makes it impossible to fail!
                if (count < 1 || count > 10) {
                    event.reply("Please specify a count between 1 and 10.").setEphemeral(true).queue();
                    return;
                }
            }
            
            if (!guild.getSelfMember().hasPermission(category, net.dv8tion.jda.api.Permission.MANAGE_CHANNEL)) {
                event.reply("I need the 'Manage Channels' permission to create voice channels in this category.").setEphemeral(true).queue();
                return;
            }
            if (activeEvents.containsKey(guildId)) {
                event.reply("An event is already active in this guild. End it before starting a new one.").setEphemeral(true).queue();
                return;
            }
            
            category.createVoiceChannel("Event Main")
                    .reason("Main event voice channel creation by " + event.getUser().getName())
                    .queue();
                    
            for (int i = 1; i <= count; i++) {
                String channelName = "Event VC " + i;
                category.createVoiceChannel(channelName)
                        .reason("Event voice channel creation by " + event.getUser().getName())
                        .queue();
            }
            activeEvents.put(guildId, categoryId);

            Button endButton = Button.danger("event:end:" + categoryId, "End Event");

            event.reply("Event started! Main channel and " + count + " voice channel(s) have been created in category '" + category.getName() + "'.")
                 .addComponents(ActionRow.of(endButton))
                 .queue();
            
        } else if (action.equalsIgnoreCase("append")) {
            if (!activeEvents.containsKey(guildId)) {
                event.reply("No active event found in this guild. Start an event first.").setEphemeral(true).queue();
                return;
            }
            String categoryId = activeEvents.get(guildId);
            var category = guild.getCategoryById(categoryId);
            if (category == null) {
                event.reply("Event category not found. Please end the event and start a new one.").setEphemeral(true).queue();
                return;
            }
            OptionMapping countOption = event.getOption("count");
            OptionMapping limitOption = event.getOption("limit");
            int count = 1;
            final int userLimit = limitOption != null ? limitOption.getAsInt() : 2;
            if (countOption != null) {
                count = countOption.getAsInt();
                if (count < 1 || count > 10) {
                    event.reply("Please specify a count between 1 and 10.").setEphemeral(true).queue();
                    return;
                }
            }
            if (limitOption != null) {
                if (userLimit < 1 || userLimit > 99) {
                    event.reply("Please specify a user limit between 1 and 99.").setEphemeral(true).queue();
                    return;
                }
            }
            for (int i = 1; i <= count; i++) {
                String channelName = "Event VC " + (category.getVoiceChannels().size() + 1);
                category.createVoiceChannel(channelName)
                        .reason("Appended event voice channel by " + event.getUser().getName())
                        .queue(channel -> channel.getManager().setUserLimit(userLimit).queue());
            }
            event.reply(count + " voice channel(s) with user limit " + userLimit + " have been appended to the event in category '" + category.getName() + "'.").queue();
            
        } else if (action.equalsIgnoreCase("end")) {
            OptionMapping categoryOption = event.getOption("category");
            if (categoryOption == null) {
                event.reply("You must specify a category to end the event.").setEphemeral(true).queue();
                return;
            }
            
            var category = categoryOption.getAsChannel().asCategory();
            
            if (!guild.getSelfMember().hasPermission(category, net.dv8tion.jda.api.Permission.MANAGE_CHANNEL)) {
                event.reply("I need the 'Manage Channels' permission to delete voice channels in this category.").setEphemeral(true).queue();
                return;
            }
            var voiceChannels = category.getVoiceChannels();
            int deleted = 0;
            for (var vc : voiceChannels) {
                if (vc.getName().startsWith("Event VC ") || vc.getName().equals("Event Main")) {
                    vc.delete().reason("Event ended by " + event.getUser().getName()).queue();
                    deleted++;
                }
            }
            activeEvents.remove(guildId);
            event.reply("Event ended! " + deleted + " voice channel(s) have been deleted from category '" + category.getName() + "'.").queue();
        } else {
            event.reply("Invalid action specified. Use 'start', 'append', or 'end'.").setEphemeral(true).queue();
        }
    }

    @Override
    public void onButtonInteraction(net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent event) {
        String componentId = event.getComponentId();

        if (componentId.startsWith("event:end:")) {
            if (!event.getMember().hasPermission(net.dv8tion.jda.api.Permission.MANAGE_CHANNEL)) {
                event.reply("You need the 'Manage Channels' permission to click this button!").setEphemeral(true).queue();
                return;
            }

            String categoryId = componentId.replace("event:end:", "");
            var category = event.getGuild().getCategoryById(categoryId);

            if (category == null) {
                event.reply("Category not found. It may have already been deleted.").setEphemeral(true).queue();
                return;
            }

            int deleted = 0;
            for (var vc : category.getVoiceChannels()) {
                if (vc.getName().startsWith("Event VC ") || vc.getName().equals("Event Main")) {
                    vc.delete().reason("Event ended by " + event.getUser().getName()).queue();
                    deleted++;
                }
            }

            clearActiveEvent(event.getGuild().getId());

            event.editMessage("Event ended! " + deleted + " voice channel(s) were deleted.")
                 .setComponents()
                 .queue();
        }
    }
}