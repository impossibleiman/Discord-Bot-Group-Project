package com.example;

import io.github.cdimascio.dotenv.Dotenv;
import io.javalin.Javalin;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.requests.GatewayIntent;
import okhttp3.*;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.json.JSONObject;

import com.google.gson.Gson;

public class MinecraftSocietyBot {
    
    // A simple way to store active sessions (In-memory for now)
    private static final java.util.Map<String, String> activeSessions = new java.util.HashMap<>();
    
    private static Map<String, ServerConfig> guildConfigs = new HashMap<>();
    private static final String CONFIG_FILE = "guild_configs.json";

    private static void saveConfigs() {
        try (FileWriter writer = new FileWriter(CONFIG_FILE)) {
            new Gson().toJson(guildConfigs, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void loadConfigs() {
        File file = new File(CONFIG_FILE);
        if (file.exists()) {
            try (FileReader reader = new FileReader(file)) {
                java.lang.reflect.Type type = new com.google.gson.reflect.TypeToken<Map<String, ServerConfig>>(){}.getType();
                guildConfigs = new Gson().fromJson(reader, type);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }


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
            for (Guild guild : jdaHolder[0].getGuilds()) {
                listener.updateInviteCache(guild);
                }
        // Catch all other errors
        } catch (Exception e) {
            System.err.println("Failed to start bot:");
            e.printStackTrace();
            return;
        }

        var app = Javalin.create(config -> {
            // We have to go through 'bundledPlugins' to find the CORS settings
            config.bundledPlugins.enableCors(cors -> {
                cors.addRule(it -> {
                    it.allowHost("https://mmuminecraftsociety.co.uk");
                });
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

                // Get Discord user ID from access token
                String userId = getDiscordUserId(accessToken, httpClient);
                if (userId == null) {
                    ctx.status(500).result("Failed to fetch Discord user.");
                    return;
                }

                // B. Check if user is an Administrator in a server with the bot
                if (isUserAdmin(accessToken, httpClient, jdaHolder[0])) {
                    String sessionId = java.util.UUID.randomUUID().toString();

                    // Store sessionId -> userId
                    activeSessions.put(sessionId, userId);

                    ctx.redirect("https://mmuminecraftsociety.co.uk/dashboard.html?session=" + sessionId);
                } else {
                    ctx.status(403).result("Access Denied: You must be an Admin in a server where the bot is present.");
                }
            } catch (Exception e) {
                ctx.status(500).result("Error during auth: " + e.getMessage());
            }
        });

        app.get("/create-magic-invite/{guildId}", ctx -> {
            String sessionId = ctx.header("Authorization");
            if (!activeSessions.containsKey(sessionId)) { ctx.status(401); return; }

            String guildId = ctx.pathParam("guildId");
            
            // 1. Get the requested alias from the website URL (?alias=Instagram)
            String requestedAlias = ctx.queryParam("alias");
            if (requestedAlias == null || requestedAlias.trim().isEmpty()) {
                ctx.status(400).result("Error: Alias name is required.");
                return;
            }

            var guild = jdaHolder[0].getGuildById(guildId);
            if (guild == null) { ctx.status(404); return; }

            // 2. Find target channel (same logic as before)
            var channels = guild.getTextChannelsByName("test-welcome", true);
            var targetChannel = !channels.isEmpty() ? channels.get(0) : guild.getSystemChannel();

            if (targetChannel == null) {
                ctx.status(400).result("No suitable channel found.");
                return;
            }

            // 3. Create the unique, permanent invite
            targetChannel.createInvite()
                    .setMaxAge(0)
                    .setMaxUses(0)
                    .setUnique(true) // Crucial for unique aliasing
                    .queue(invite -> {
                        
                        // 4. Fetch current config, add new mapping, and save IMMEDIATELY
                        ServerConfig config = guildConfigs.getOrDefault(guildId, new ServerConfig());
                        if (config.inviteAliases == null) config.inviteAliases = new java.util.HashMap<>();
                        
                        // Map cryptic Discord Code -> Your Human Alias
                        config.inviteAliases.put(invite.getCode(), requestedAlias.trim());
                        
                        // Commit to RAM map and JSON file
                        guildConfigs.put(guildId, config);
                        saveConfigs(); 
                        // ---------------------------------

                        // Send code back just for logging on the dashboard
                        ctx.result(invite.getCode()); 

                    }, throwable -> {
                        ctx.status(500).result("Discord Error: " + throwable.getMessage());
                    });
        });

        app.get("/test-env", ctx -> {
            String clientId = dotenv.get("DISCORD_CLIENT_ID");
            
            if (clientId == null || clientId.isEmpty()) {
                ctx.status(500).result("❌ Java could NOT find the CLIENT_ID in .env");
            } else {
                ctx.result("✅ API is Live! Found Client ID: " + clientId);
            }
        });

        app.get("/check-session", ctx -> {
            String sessionId = ctx.queryParam("session");
            
            if (sessionId == null || !activeSessions.containsKey(sessionId)) {
                ctx.status(401).result("Session invalid.");
                return;
            }
            
            String userId = activeSessions.get(sessionId);
            ctx.result("Logged in as User ID: " + userId);
        });

        loadConfigs(); // Load settings when bot starts

        app.get("/config/{guildId}", ctx -> {
            String sessionId = ctx.header("Authorization"); // We'll send the session ID here
            if (!activeSessions.containsKey(sessionId)) {
                ctx.status(401).result("Unauthorized");
                return;
            }

            String guildId = ctx.pathParam("guildId");
            ServerConfig config = guildConfigs.getOrDefault(guildId, new ServerConfig());
            ctx.json(config);
        });

        app.post("/config/{guildId}", ctx -> {
            String sessionId = ctx.header("Authorization");
            if (!activeSessions.containsKey(sessionId)) {
                ctx.status(401).result("Unauthorized");
                return;
            }

            String guildId = ctx.pathParam("guildId");
            ServerConfig newConfig = ctx.bodyAsClass(ServerConfig.class);
            
            guildConfigs.put(guildId, newConfig);
            saveConfigs(); // Permanent save!

            // Apply the nickname change in Discord immediately
            var guild = jdaHolder[0].getGuildById(guildId);
            if (guild != null) {
                guild.getSelfMember().modifyNickname(newConfig.nickname).queue();
            }

            ctx.result("Config updated and applied!");
        });


        app.get("/my-guilds", ctx -> {
            String sessionId = ctx.header("Authorization");
            if (!activeSessions.containsKey(sessionId)) { ctx.status(401); return; }

            // Return a list of guilds the bot is in
            var guilds = jdaHolder[0].getGuilds().stream()
                .map(g -> Map.of("id", g.getId(), "name", g.getName()))
                .toList();
            ctx.json(guilds);
        });

    }

    private static String getDiscordUserId(String accessToken, OkHttpClient client) {
        Request request = new Request.Builder()
                .url("https://discord.com/api/users/@me")
                .header("Authorization", "Bearer " + accessToken)
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) return null;
            JSONObject userJson = new JSONObject(response.body().string());
            return userJson.getString("id");
        } catch (Exception e) {
            System.err.println("Error fetching Discord user ID:");
            e.printStackTrace();
            return null;
        }
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


    public static ServerConfig getGuildConfig(String guildId) {
    return guildConfigs.getOrDefault(guildId, new ServerConfig());
}
}