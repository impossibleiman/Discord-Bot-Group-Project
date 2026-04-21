package com.example.commands;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

import org.json.JSONArray;
import org.json.JSONObject;

import java.awt.*;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

// This command fetches a random cat image using The Cat API (thecatapi.com)
// It is free to use and does not require an API key for basic requests
public class CatsCommand implements ICommand {

    // This endpoint returns one random cat image each time it is called
    private static final String CAT_API_URL = "https://api.thecatapi.com/v1/images/search";

    @Override
    public String getName() {
        return "cat";
    }

    @Override
    public String getDescription() {
        return "Get a random cat picture.";
    }

    @Override
    public List<OptionData> getOptions() {
        // No options needed for this command
        return new ArrayList<>();
    }

    @Override
    public DefaultMemberPermissions getPermission() {
        // Everyone can use this command
        return DefaultMemberPermissions.ENABLED;
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {

        // Defer the reply because the HTTP request may take a moment
        event.deferReply().queue();

        try {
            // Open a connection to The Cat API
            URL url = new URL(CAT_API_URL);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("User-Agent", "DiscordBot/1.0");

            // Read the response into a string
            BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
            reader.close();

            // The API returns a JSON array with one object inside it
            JSONArray jsonArray = new JSONArray(response.toString());
            JSONObject catData = jsonArray.getJSONObject(0);

            // Pull the image URL out of the response
            String imageUrl = catData.getString("url");

            // Build the embed with the cat image
            EmbedBuilder embed = new EmbedBuilder()
                    .setTitle("Here is a cat!")
                    .setImage(imageUrl)
                    .setColor(new Color(255, 140, 0))
                    .setFooter("Powered by thecatapi.com")
                    .setTimestamp(Instant.now());

            event.getHook().editOriginalEmbeds(embed.build()).queue();

        } catch (Exception e) {
            // If the request fails for any reason, show a friendly error message
            event.getHook().editOriginal("Failed to fetch a cat image: " + e.getMessage()).queue();
        }
    }
}