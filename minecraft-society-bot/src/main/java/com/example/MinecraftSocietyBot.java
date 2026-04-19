package com.example;

import io.github.cdimascio.dotenv.Dotenv;
import io.javalin.Javalin;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.requests.GatewayIntent;
import okhttp3.*;
import org.json.JSONObject;

public class MinecraftSocietyBot {
    
    public static void main(String[] args) {
    
        // Get token from .env 
        Dotenv dotenv = Dotenv.configure()
            .directory("./minecraft-society-bot")
            .load();
        String token = dotenv.get("DISCORD_TOKEN");

        // Error catcher for missing token
        if (token == null) {
            System.err.println("Error: DISCORD_TOKEN is missing from .env file!");
            return; 
        }

        // Command Manager
        CommandManager manager = new CommandManager();
        // -------------------------------------------------------------------------------------------------------
        // Commands go here:
        manager.addCommand(new PingCommand()); // Added by Iman for first push - example command
        manager.addCommand(new StartEventCommand()); // Added by Iman
        manager.addCommand(new ReactionRoleCommand()); // Added by Iman
        manager.addCommand(new Purgecommand());//added by Jack

        // -------------------------------------------------------------------------------------------------------

        MinecraftSocietyListener listener = new MinecraftSocietyListener();

        final JDA[] jdaHolder = new JDA[1];

        try {
            jdaHolder[0] = JDABuilder.createDefault(token)
                    .enableIntents(GatewayIntent.MESSAGE_CONTENT, GatewayIntent.GUILD_MEMBERS, GatewayIntent.GUILD_MESSAGES) // Needed for some commands
                    .addEventListeners(manager, listener)
                    .build();
            
            jdaHolder[0].awaitReady();
            System.out.println("Bot is online!");
            
        // Catch all other errors
        } catch (Exception e) {
            System.err.println("Failed to start bot:");
            e.printStackTrace();
            return;
        }

        var app = Javalin.create(config -> {
            // We have to go through 'bundledPlugins' to find the CORS settings
            config.bundledPlugins.enableCors(cors -> {
                cors.addRule(it -> it.allowHost("https://mmuminecraftsociety.co.uk"));
            });
        }).start(8080);

        app.get("/status", ctx -> ctx.result("Bot is running!"));

        app.get("/login", ctx -> {
            String clientId = dotenv.get("DISCORD_CLIENT_ID");
            String redirectUri = "https://api.mmuminecraftsociety.co.uk/callback";

            String url = "https://discord.com/api/oauth2/authorize" +
                    "?client_id=" + clientId +
                    "&redirect_uri=" + redirectUri +
                    "&response_type=code" +
                    "&scope=identify+guilds"; // Added guilds here
            ctx.redirect(url);
        });

        app.get("/callback", ctx -> {
            String code = ctx.queryParam("code");
            if (code == null) {
                ctx.status(400).result("Auth code missing.");
                return;
            }

            OkHttpClient httpClient = new OkHttpClient();

            // A. Swap code for an Access Token
            RequestBody formBody = new FormBody.Builder()
                    .add("client_id", dotenv.get("DISCORD_CLIENT_ID"))
                    .add("client_secret", dotenv.get("DISCORD_CLIENT_SECRET"))
                    .add("grant_type", "authorization_code")
                    .add("code", code)
                    .add("redirect_uri", "https://api.mmuminecraftsociety.co.uk/callback")
                    .build();

            Request tokenRequest = new Request.Builder()
                    .url("https://discord.com/api/oauth2/token")
                    .post(formBody)
                    .build();

            try (Response tokenResponse = httpClient.newCall(tokenRequest).execute()) {
                JSONObject tokenJson = new JSONObject(tokenResponse.body().string());
                String accessToken = tokenJson.getString("access_token");

                // B. Check if user is an Administrator in a server with the bot
                if (isUserAdmin(accessToken, httpClient, jdaHolder[0])) {
                    // Success! Redirect to dashboard
                    ctx.redirect("https://mmuminecraftsociety.co.uk/dashboard.html?auth=success");
                } else {
                    ctx.status(403).result("Access Denied: You must be an Admin in a server where the bot is present.");
                }
            } catch (Exception e) {
                ctx.status(500).result("Error during auth: " + e.getMessage());
            }
        });

        app.get("/test-env", ctx -> {
            String clientId = dotenv.get("DISCORD_CLIENT_ID");
            
            if (clientId == null || clientId.isEmpty()) {
                ctx.status(500).result("❌ Java could NOT find the CLIENT_ID in .env");
            } else {
                ctx.result("✅ API is Live! Found Client ID: " + clientId);
            }
        });
    }


    private static boolean isUserAdmin(String accessToken, OkHttpClient client, JDA jda) {
        Request request = new Request.Builder()
                .url("https://discord.com/api/users/@me/guilds")
                .header("Authorization", "Bearer " + accessToken)
                .build();

        try (Response response = client.newCall(request).execute()) {
            String body = response.body().string();
            org.json.JSONArray guilds = new org.json.JSONArray(body);

            System.out.println("--- Checking Permissions for User ---");
            for (int i = 0; i < guilds.length(); i++) {
                JSONObject guild = guilds.getJSONObject(i);
                String name = guild.getString("name");
                String guildId = guild.getString("id");
                
                // FIX APPLIED HERE: Use getLong instead of getString
                long permissions = guild.getLong("permissions");

                boolean userIsAdmin = (permissions & 0x8) == 0x8;
                boolean botIsPresent = jda.getGuildById(guildId) != null;

                System.out.println(String.format("Server: %s | Admin: %b | Bot Present: %b", name, userIsAdmin, botIsPresent));

                if (userIsAdmin && botIsPresent) return true;
            }
        } catch (Exception e) {
            System.err.println("Error checking admin permissions:");
            e.printStackTrace();
        }
        return false;
    }
}