package com.example.commands;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

import java.awt.*;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.json.JSONArray;
import org.json.JSONObject;

// This command fetches a random cat image from the r/cats subreddit
// It uses Reddit's public JSON API which does not require an API key
public class CatsCommand implements ICommand {

    // Reddit's JSON API URL for r/cats - fetches the top 50 hot posts
    private static final String REDDIT_URL = "https://www.reddit.com/r/cats/hot.json?limit=50";

    private final Random random = new Random();

    @Override
    public String getName() {
        return "cat";
    }

    @Override
    public String getDescription() {
        return "Get a random cat picture from r/cats.";
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

        // Defer the reply because the HTTP request to Reddit may take a moment
        event.deferReply().queue();

        try {
            // Open a connection to the Reddit API
            URL url = URI.create(REDDIT_URL).toURL();
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");

            // Reddit requires a User-Agent header or it will block the request
            connection.setRequestProperty("User-Agent", "DiscordBot/1.0");

            // Read the response from Reddit into a string
            BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
            reader.close();

            // Parse the JSON response
            JSONObject json = new JSONObject(response.toString());
            JSONArray posts = json.getJSONObject("data").getJSONArray("children");

            // Loop through the posts and collect only ones that have an image
            List<JSONObject> imagePosts = new ArrayList<>();
            for (int i = 0; i < posts.length(); i++) {
                JSONObject postData = posts.getJSONObject(i).getJSONObject("data");

                // Skip posts that are not images or are marked as NSFW
                String postUrl = postData.optString("url", "");
                boolean isNsfw = postData.optBoolean("over_18", false);

                if (!isNsfw && (postUrl.endsWith(".jpg") || postUrl.endsWith(".png") || postUrl.endsWith(".gif"))) {
                    imagePosts.add(postData);
                }
            }

            // If no image posts were found, let the user know
            if (imagePosts.isEmpty()) {
                event.getHook().editOriginal("Could not find any cat images right now. Try again in a moment.").queue();
                return;
            }

            // Pick a random image post from the ones we found
            JSONObject chosenPost = imagePosts.get(random.nextInt(imagePosts.size()));

            String title = chosenPost.optString("title", "A cat!");
            String imageUrl = chosenPost.optString("url", "");
            String postLink = "https://reddit.com" + chosenPost.optString("permalink", "");
            int upvotes = chosenPost.optInt("ups", 0);

            // Build an embed with the cat image
            EmbedBuilder embed = new EmbedBuilder()
                    .setTitle(title, postLink)
                    .setImage(imageUrl)
                    .setColor(new Color(255, 140, 0))
                    .setFooter("From r/cats  |  " + upvotes + " upvotes")
                    .setTimestamp(Instant.now());

            event.getHook().editOriginalEmbeds(embed.build()).queue();

        } catch (Exception e) {
            // If anything goes wrong with the request, show a friendly error
            event.getHook().editOriginal("Failed to fetch a cat image: " + e.getMessage()).queue();
        }
    }
}