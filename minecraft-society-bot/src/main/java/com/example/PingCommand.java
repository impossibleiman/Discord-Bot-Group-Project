package com.example;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

import java.util.ArrayList;
import java.util.List;

public class PingCommand implements ICommand {

    @Override
    public String getName() {
        return "ping";
    }

    @Override
    public String getDescription() {
        return "Replies with Pong! Tests if the bot is working.";
    }

    @Override
    public List<OptionData> getOptions() {
        return new ArrayList<>(); // No extra options needed for this command
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        // This is what happens when someone types /ping
        event.reply("Pong!").queue();
    }
}
