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
import java.util.Set;
import java.util.HashSet;
import java.util.List;

import org.json.JSONObject;
import org.json.JSONArray;

import com.google.gson.Gson;

public class MinecraftSocietyBot {

    // Store the latest Minecraft data in memory
    private static final List<Map<String, String>> liveChat = new java.util.concurrent.CopyOnWriteArrayList<>();
    private static final List<String> webToGameQueue = new java.util.concurrent.CopyOnWriteArrayList<>();
    
    // --- NEW SECURITY ARCHITECTURE ---
    static class SessionData {
        String userId;
        String username;
        Set<String> adminGuilds; // Only servers this specific user is allowed to edit
        
        SessionData(String userId, String username, Set<String> adminGuilds) {
            this.userId = userId;
            this.username = username;
            this.adminGuilds = adminGuilds;
        }
    }
    
    // Maps the session token to the user's secure SessionData
    private static final Map<String, SessionData> activeSessions = new HashMap<>();
    // ---------------------------------
    
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
        Dotenv dotenv = Dotenv.configure().directory("./minecraft-society-bot").load();
        String token = dotenv.get("DISCORD_TOKEN");

        if (token == null) {
            System.err.println("Error: DISCORD_TOKEN is missing from .env file!");
            return; 
        }
        
        CommandManager manager = new CommandManager();
        manager.addCommand(new PingCommand()); 
        manager.addCommand(new StartEventCommand()); 
        manager.addCommand(new ReactionRoleCommand()); 
        manager.addCommand(new Purgecommand());
        manager.addCommand(new TicketPanelCommand()); 

        MinecraftSocietyListener listener = new MinecraftSocietyListener();
        final JDA jdaHolder;

        try {
            jdaHolder = JDABuilder.createDefault(token)
                    .enableIntents(GatewayIntent.MESSAGE_CONTENT, GatewayIntent.GUILD_MEMBERS, GatewayIntent.GUILD_MESSAGES)
                    .addEventListeners(
                                       manager,
                                       listener,
                                       new LeaveListener(),
                                       new RoleUpdateListener()
                                      )
                    .build();
            
            jdaHolder.awaitReady();
            System.out.println("Bot is online!");
            for (Guild guild : jdaHolder.getGuilds()) {
                // Check for permission before trying to fetch invites
                if (guild.getSelfMember().hasPermission(net.dv8tion.jda.api.Permission.MANAGE_SERVER)) {
                    updateInviteCache(guild);
                } else {
                    System.err.println("⚠️ Skipping invite cache for " + guild.getName() + " (Missing MANAGE_SERVER permission)");
                }
            }
        } catch (Exception e) {
            System.err.println("Failed to start bot:");
            e.printStackTrace();
            return;
        }

        var app = Javalin.create(config -> {
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
            String url = "https://discord.com/api/oauth2/authorize?client_id=" + clientId + "&redirect_uri=" + redirectUri + "&response_type=code&scope=identify+guilds";
            ctx.redirect(url);
        });

        app.get("/callback", ctx -> {
            String code = ctx.queryParam("code");
            if (code == null) { ctx.status(400).result("Auth code missing."); return; }

            OkHttpClient httpClient = new OkHttpClient();
            RequestBody formBody = new FormBody.Builder()
                    .add("client_id", dotenv.get("DISCORD_CLIENT_ID"))
                    .add("client_secret", dotenv.get("DISCORD_CLIENT_SECRET"))
                    .add("grant_type", "authorization_code")
                    .add("code", code)
                    .add("redirect_uri", "https://api.mmuminecraftsociety.co.uk/callback")
                    .build();

            Request tokenRequest = new Request.Builder().url("https://discord.com/api/oauth2/token").post(formBody).build();

            try (Response tokenResponse = httpClient.newCall(tokenRequest).execute()) {
                JSONObject tokenJson = new JSONObject(tokenResponse.body().string());
                String accessToken = tokenJson.getString("access_token");

                String userId = getDiscordUserId(accessToken, httpClient);
                String username = getDiscordUsername(accessToken, httpClient);
                if (userId == null) { ctx.status(500).result("Failed to fetch Discord user."); return; }

                // SECURE: Fetch only the servers this user is an admin of
                Set<String> allowedGuilds = getAdminGuilds(accessToken, httpClient, jdaHolder);

                if (!allowedGuilds.isEmpty()) {
                    String sessionId = java.util.UUID.randomUUID().toString();
                    activeSessions.put(sessionId, new SessionData(userId, username, allowedGuilds));
                    ctx.redirect("https://mmuminecraftsociety.co.uk/dashboard.html?session=" + sessionId);
                } else {
                    ctx.status(403).result("Access Denied: You must be an Admin in a server where the bot is present.");
                }
            } catch (Exception e) {
                ctx.status(500).result("Error during auth: " + e.getMessage());
            }
        });

        app.get("/my-guilds", ctx -> {
            String sessionId = ctx.header("Authorization");
            SessionData session = activeSessions.get(sessionId);
            if (session == null) { ctx.status(401).result("Unauthorized"); return; }

            // SECURE: Return ONLY the servers this specific user is allowed to manage
            var guilds = session.adminGuilds.stream()
                .map(id -> jdaHolder.getGuildById(id))
                .filter(java.util.Objects::nonNull)
                .map(g -> Map.of("id", g.getId(), "name", g.getName()))
                .toList();
            
            ctx.json(guilds);
        });

        app.get("/create-magic-invite/{guildId}", ctx -> {
            String sessionId = ctx.header("Authorization");
            SessionData session = activeSessions.get(sessionId);
            if (session == null) { ctx.status(401); return; }

            String guildId = ctx.pathParam("guildId");
            
            // SECURE: Deny if they don't have access to this specific server
            if (!session.adminGuilds.contains(guildId)) { ctx.status(403).result("Forbidden"); return; }

            String requestedAlias = ctx.queryParam("alias");
            if (requestedAlias == null || requestedAlias.trim().isEmpty()) {
                ctx.status(400).result("Error: Alias name is required."); return;
            }

            var guild = jdaHolder.getGuildById(guildId);
            if (guild == null) { ctx.status(404); return; }

            var channels = guild.getTextChannelsByName("test-welcome", true);
            var targetChannel = !channels.isEmpty() ? channels.get(0) : guild.getSystemChannel();

            if (targetChannel == null) { ctx.status(400).result("No suitable channel found."); return; }

            try {
                // FIXED: Using .complete() ensures the invite exists before the website refreshes
                var invite = targetChannel.createInvite().setMaxAge(0).setMaxUses(0).setUnique(true).complete();

                ServerConfig config = guildConfigs.getOrDefault(guildId, new ServerConfig());
                if (config.inviteAliases == null) config.inviteAliases = new HashMap<>();
                config.inviteAliases.put(invite.getCode(), requestedAlias.trim());
                
                guildConfigs.put(guildId, config);
                saveConfigs(); 

                ctx.result(invite.getCode());
            } catch (Exception e) {
                ctx.status(500).result("Failed to communicate with Discord.");
            }
        });

        app.delete("/delete-invite/{guildId}/{code}", ctx -> {
            String sessionId = ctx.header("Authorization");
            SessionData session = activeSessions.get(sessionId);
            if (session == null) { ctx.status(401); return; }

            String guildId = ctx.pathParam("guildId");
            if (!session.adminGuilds.contains(guildId)) { ctx.status(403).result("Forbidden"); return; }

            String code = ctx.pathParam("code");

            net.dv8tion.jda.api.entities.Invite.resolve(jdaHolder, code).queue(invite -> {
                invite.delete().queue(
                    s -> System.out.println("Deleted invite " + code),
                    e -> System.err.println("Failed to delete from Discord: " + e.getMessage())
                );
            }, t -> System.err.println("Invite " + code + " not found."));

            ServerConfig config = guildConfigs.get(guildId);
            if (config != null && config.inviteAliases != null) {
                config.inviteAliases.remove(code);
                saveConfigs();
                ctx.result("Invite removed.");
            } else {
                ctx.status(404).result("Invite not found.");
            }
        });

        app.get("/check-session", ctx -> {
            String sessionId = ctx.queryParam("session");
            SessionData session = activeSessions.get(sessionId);
            
            if (session == null) {
                ctx.status(401).result("Session invalid."); return;
            }
            ctx.result("Logged in as User ID: " + session.userId);
        });

        loadConfigs();

        app.get("/config/{guildId}", ctx -> {
            String sessionId = ctx.header("Authorization");
            SessionData session = activeSessions.get(sessionId);
            if (session == null) { ctx.status(401).result("Unauthorized"); return; }

            String guildId = ctx.pathParam("guildId");
            if (!session.adminGuilds.contains(guildId)) { ctx.status(403).result("Forbidden"); return; }

            ServerConfig config = guildConfigs.getOrDefault(guildId, new ServerConfig());
            ctx.json(config);
        });

        app.post("/config/{guildId}", ctx -> {
            String sessionId = ctx.header("Authorization");
            SessionData session = activeSessions.get(sessionId);
            if (session == null) { ctx.status(401).result("Unauthorized"); return; }

            String guildId = ctx.pathParam("guildId");
            if (!session.adminGuilds.contains(guildId)) { ctx.status(403).result("Forbidden"); return; }

            ServerConfig newConfig = ctx.bodyAsClass(ServerConfig.class);
            
            ServerConfig existing = guildConfigs.get(guildId);
            if (existing != null && (newConfig.inviteAliases == null || newConfig.inviteAliases.isEmpty())) {
                newConfig.inviteAliases = existing.inviteAliases;
            }

            guildConfigs.put(guildId, newConfig);
            saveConfigs(); 

            var guild = jdaHolder.getGuildById(guildId);
            if (guild != null && newConfig.nickname != null && !newConfig.nickname.trim().isEmpty()) {
                guild.getSelfMember().modifyNickname(newConfig.nickname).queue();
            }

            ctx.result("Config updated and applied!");
        });


        // --- MINECRAFT SYNC ENDPOINTS ---

        // 1. DATA FROM PLUGIN -> BOT
        app.post("/mc-sync/update", ctx -> {
            if (!"MMU_Soc_7721_x92_SecretSync_!99".equals(ctx.header("X-MC-Auth"))) { 
                ctx.status(401); 
                return; 
            }

            org.json.JSONObject data = new org.json.JSONObject(ctx.body());
            String now = java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"));

            // Sync Chat Messages from Game
            if (data.has("newChat")) {
                org.json.JSONArray newMsgs = data.getJSONArray("newChat");
                for (int i = 0; i < newMsgs.length(); i++) {
                    org.json.JSONObject m = newMsgs.getJSONObject(i);
                    liveChat.add(0, java.util.Map.of(
                        "user", m.getString("player"), 
                        "text", m.getString("content"),
                        "time", now
                    ));
                }
                while (liveChat.size() > 50) liveChat.remove(liveChat.size() - 1);
            }

            // Send back any messages queued from the Website to the Game
            org.json.JSONArray responseQueue = new org.json.JSONArray(webToGameQueue);
            webToGameQueue.clear();
            ctx.json(responseQueue.toString());
        });

        // 2. DATA FROM BOT -> WEBSITE
        app.get("/mc-data", ctx -> {
            // Only returning chat now (no status object)
            ctx.json(java.util.Map.of("chat", liveChat));
        });

        // 3. CHAT FROM WEBSITE -> GAME
        app.post("/mc-send-chat", ctx -> {
            String sessionId = ctx.header("Authorization");
            if (!activeSessions.containsKey(sessionId)) { ctx.status(401); return; }

            org.json.JSONObject body = new org.json.JSONObject(ctx.body());
            String message = body.getString("message");
            String user = activeSessions.get(sessionId).username;
            String now = java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"));
            
            // Add to the queue for the plugin to pick up on its next sync
            webToGameQueue.add("§a[Web] §f" + user + ": " + message);
            
            // Add to web chat immediately so the sender sees it on the dashboard
            liveChat.add(0, java.util.Map.of(
                "user", user + " (Web)", 
                "text", message,
                "time", now
            ));
            
            ctx.status(200);
        });


    }

    private static String getDiscordUserId(String accessToken, OkHttpClient client) {
        Request request = new Request.Builder().url("https://discord.com/api/users/@me").header("Authorization", "Bearer " + accessToken).build();
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) return null;
            return new JSONObject(response.body().string()).getString("id");
        } catch (Exception e) { return null; }
    }

    private static String getDiscordUsername(String accessToken, OkHttpClient client) {
        Request request = new Request.Builder().url("https://discord.com/api/users/@me").header("Authorization", "Bearer " + accessToken).build();
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) return null;
            return new JSONObject(response.body().string()).getString("username");
        } catch (Exception e) { return null; }
    }

    // SECURE: This now returns a list of Server IDs instead of a simple True/False
    private static Set<String> getAdminGuilds(String accessToken, OkHttpClient client, JDA jda) {
        Set<String> adminGuilds = new HashSet<>();
        Request request = new Request.Builder().url("https://discord.com/api/users/@me/guilds").header("Authorization", "Bearer " + accessToken).build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) return adminGuilds;
            JSONArray guilds = new JSONArray(response.body().string());

            for (int i = 0; i < guilds.length(); i++) {
                JSONObject guild = guilds.getJSONObject(i);
                String guildId = guild.getString("id");
                long permissions = guild.getLong("permissions");

                // Check for Administrator (0x8) or Manage Server (0x20)
                boolean userIsAdmin = (permissions & 0x8) == 0x8 || (permissions & 0x20) == 0x20;
                boolean botIsPresent = jda.getGuildById(guildId) != null;

                if (userIsAdmin && botIsPresent) {
                    adminGuilds.add(guildId);
                }
            }
        } catch (Exception e) { e.printStackTrace(); }
        return adminGuilds;
    }

    public static ServerConfig getGuildConfig(String guildId) {
        return guildConfigs.getOrDefault(guildId, new ServerConfig());
    }

    public static void updateInviteCache(net.dv8tion.jda.api.entities.Guild guild) {
        // Double-check permissions just to be safe
        if (guild.getSelfMember().hasPermission(net.dv8tion.jda.api.Permission.MANAGE_SERVER)) {
            guild.retrieveInvites().queue(
                invites -> {
                    java.util.Map<String, Integer> cache = new java.util.HashMap<>();
                    for (net.dv8tion.jda.api.entities.Invite invite : invites) {
                        cache.put(invite.getCode(), invite.getUses());
                    }
                    System.out.println("✅ Invite cache updated for: " + guild.getName());
                },
                error -> System.err.println("Failed to cache invites for " + guild.getName() + ": " + error.getMessage())
            );
        }
    }
}
