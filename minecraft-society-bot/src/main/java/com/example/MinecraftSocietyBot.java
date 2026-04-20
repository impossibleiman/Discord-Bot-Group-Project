package com.example;

import io.github.cdimascio.dotenv.Dotenv;
import io.javalin.Javalin;
import io.javalin.http.Context;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.requests.GatewayIntent;
import okhttp3.*;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.List;

import org.json.JSONObject;
import org.json.JSONArray;

import com.google.gson.Gson;
import com.example.commands.CommandManager;
import com.example.commands.PingCommand;
import com.example.commands.Purgecommand;
import com.example.commands.ReactionRoleCommand;
import com.example.commands.StartEventCommand;
import com.example.commands.TicketPanelCommand;
import com.example.listeners.LeaveListener;
import com.example.listeners.MinecraftSocietyListener;
import com.example.listeners.RoleUpdateListener;
import com.example.model.ServerConfig;

public class MinecraftSocietyBot {

    private static final String CONFIG_FILE = "guild_configs.json";
    private static final String CORS_ALLOW_HOST = "https://mmuminecraftsociety.co.uk";
    private static final String OAUTH_REDIRECT_URI = "https://api.mmuminecraftsociety.co.uk/callback";
    private static final String DASHBOARD_URL = "https://mmuminecraftsociety.co.uk/dashboard";

    private static final String MC_SYNC_AUTH_HEADER = "X-MC-Auth";
    private static final String MC_SYNC_SECRET = "MMU_Soc_7721_x92_SecretSync_!99";

    private static final String TARGET_GUILD_ID = "1468598134241230851";
    private static final String TARGET_BOT_ID = "1493768627256688772";

    // Store latest Minecraft data in memory
    private static final List<Map<String, String>> liveChat = new java.util.concurrent.CopyOnWriteArrayList<>();
    private static final List<String> webToGameQueue = new java.util.concurrent.CopyOnWriteArrayList<>();
    private static final Map<String, Object> mcStatus = new ConcurrentHashMap<>();
    
    // Session-based auth model for dashboard endpoints
    static class SessionData {
        String userId;
        String username;
        Set<String> adminGuilds;
        
        SessionData(String userId, String username, Set<String> adminGuilds) {
            this.userId = userId;
            this.username = username;
            this.adminGuilds = adminGuilds;
        }
    }
    
    private static final Map<String, SessionData> activeSessions = new ConcurrentHashMap<>();
    
    private static Map<String, ServerConfig> guildConfigs = new HashMap<>();

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
        // 1) Boot config + env
        Dotenv dotenv = Dotenv.configure().directory("./minecraft-society-bot").load();
        String token = dotenv.get("DISCORD_TOKEN");

        if (token == null) {
            System.err.println("Error: DISCORD_TOKEN is missing from .env file!");
            return; 
        }
        
        loadConfigs();

        // 2) Boot Discord bot + listeners
        CommandManager manager = buildCommandManager();

        MinecraftSocietyListener listener = new MinecraftSocietyListener();
        JDA jdaHolder = startJda(token, manager, listener);
        if (jdaHolder == null) {
            return;
        }

        warmInviteCaches(jdaHolder);

        // 3) Boot HTTP API and register routes
        Javalin app = createApp();
        registerRoutes(app, dotenv, jdaHolder);
    }

    private static CommandManager buildCommandManager() {
        CommandManager manager = new CommandManager();
        manager.addCommand(new PingCommand());
        manager.addCommand(new StartEventCommand());
        manager.addCommand(new ReactionRoleCommand());
        manager.addCommand(new Purgecommand());
        manager.addCommand(new TicketPanelCommand());
        return manager;
    }

    private static JDA startJda(String token, CommandManager manager, MinecraftSocietyListener listener) {
        try {
            JDA jda = JDABuilder.createDefault(token)
                    .enableIntents(GatewayIntent.MESSAGE_CONTENT, GatewayIntent.GUILD_MEMBERS, GatewayIntent.GUILD_MESSAGES, GatewayIntent.GUILD_PRESENCES)
                    .setMemberCachePolicy(net.dv8tion.jda.api.utils.MemberCachePolicy.ALL)
                    .enableCache(net.dv8tion.jda.api.utils.cache.CacheFlag.ONLINE_STATUS)
                    .addEventListeners(
                            manager,
                            listener,
                            new LeaveListener(),
                            new RoleUpdateListener()
                    )
                    .build();

            jda.awaitReady();
            System.out.println("Bot is online!");
            return jda;
        } catch (Exception e) {
            System.err.println("Failed to start bot:");
            e.printStackTrace();
            return null;
        }
    }

    private static void warmInviteCaches(JDA jdaHolder) {
        for (Guild guild : jdaHolder.getGuilds()) {
            if (guild.getSelfMember().hasPermission(net.dv8tion.jda.api.Permission.MANAGE_SERVER)) {
                updateInviteCache(guild);
            } else {
                System.err.println("⚠️ Skipping invite cache for " + guild.getName() + " (Missing MANAGE_SERVER permission)");
            }
        }
    }

    private static Javalin createApp() {
        return Javalin.create(config -> {
            config.bundledPlugins.enableCors(cors -> {
                cors.addRule(it -> it.allowHost(CORS_ALLOW_HOST));
            });
        }).start(8080);
    }

    private static void registerRoutes(Javalin app, Dotenv dotenv, JDA jdaHolder) {
        registerHealthRoutes(app);
        registerAuthRoutes(app, dotenv, jdaHolder);
        registerConfigRoutes(app, jdaHolder);
        registerMinecraftRoutes(app);
        registerDiscordStatsRoute(app, jdaHolder);
    }

    private static void registerHealthRoutes(Javalin app) {
        app.get("/status", ctx -> ctx.result("Bot is running!"));
    }

    private static void registerAuthRoutes(Javalin app, Dotenv dotenv, JDA jdaHolder) {
        app.get("/login", ctx -> {
            String clientId = dotenv.get("DISCORD_CLIENT_ID");
            String url = "https://discord.com/api/oauth2/authorize?client_id="
                    + clientId
                    + "&redirect_uri="
                    + OAUTH_REDIRECT_URI
                    + "&response_type=code&scope=identify+guilds";
            ctx.redirect(url);
        });

        app.get("/callback", ctx -> {
            String code = ctx.queryParam("code");
            if (code == null) {
                ctx.status(400).result("Auth code missing.");
                return;
            }

            OkHttpClient httpClient = new OkHttpClient();
            RequestBody formBody = new FormBody.Builder()
                    .add("client_id", dotenv.get("DISCORD_CLIENT_ID"))
                    .add("client_secret", dotenv.get("DISCORD_CLIENT_SECRET"))
                    .add("grant_type", "authorization_code")
                    .add("code", code)
                    .add("redirect_uri", OAUTH_REDIRECT_URI)
                    .build();

            Request tokenRequest = new Request.Builder().url("https://discord.com/api/oauth2/token").post(formBody).build();

            try (Response tokenResponse = httpClient.newCall(tokenRequest).execute()) {
                if (tokenResponse.body() == null) {
                    ctx.status(500).result("Error during auth: Empty token response.");
                    return;
                }

                JSONObject tokenJson = new JSONObject(tokenResponse.body().string());
                String accessToken = tokenJson.getString("access_token");

                String userId = getDiscordUserId(accessToken, httpClient);
                String username = getDiscordUsername(accessToken, httpClient);
                if (userId == null) {
                    ctx.status(500).result("Failed to fetch Discord user.");
                    return;
                }

                Set<String> allowedGuilds = getAdminGuilds(accessToken, httpClient, jdaHolder);
                if (allowedGuilds.isEmpty()) {
                    ctx.status(403).result("Access Denied: You must be an Admin in a server where the bot is present.");
                    return;
                }

                String sessionId = java.util.UUID.randomUUID().toString();
                activeSessions.put(sessionId, new SessionData(userId, username, allowedGuilds));
                ctx.redirect(DASHBOARD_URL + "?session=" + sessionId);
            } catch (Exception e) {
                ctx.status(500).result("Error during auth: " + e.getMessage());
            }
        });

        app.get("/my-guilds", ctx -> {
            SessionData session = requireSession(ctx);
            if (session == null) {
                return;
            }

            var guilds = session.adminGuilds.stream()
                    .map(jdaHolder::getGuildById)
                    .filter(java.util.Objects::nonNull)
                    .map(g -> Map.of("id", g.getId(), "name", g.getName()))
                    .toList();

            ctx.json(guilds);
        });

        app.get("/check-session", ctx -> {
            String sessionId = ctx.queryParam("session");
            SessionData session = activeSessions.get(sessionId);

            if (session == null) {
                ctx.status(401).result("Session invalid.");
                return;
            }
            ctx.result("Logged in as User ID: " + session.userId);
        });
    }

    private static void registerConfigRoutes(Javalin app, JDA jdaHolder) {
        app.get("/create-magic-invite/{guildId}", ctx -> {
            SessionData session = requireSession(ctx);
            if (session == null) {
                return;
            }

            String guildId = ctx.pathParam("guildId");
            if (!requireGuildAccess(ctx, session, guildId)) {
                return;
            }

            String requestedAlias = ctx.queryParam("alias");
            if (requestedAlias == null || requestedAlias.trim().isEmpty()) {
                ctx.status(400).result("Error: Alias name is required.");
                return;
            }

            try {
                Map<String, String> invite = createAndStoreMagicInvite(jdaHolder, guildId, requestedAlias.trim());
                // Keep legacy behavior for current frontend compatibility.
                ctx.result(invite.get("code"));
            } catch (IllegalArgumentException e) {
                ctx.status(400).result(e.getMessage());
            } catch (IllegalStateException e) {
                ctx.status(404).result(e.getMessage());
            } catch (Exception e) {
                ctx.status(500).result("Failed to communicate with Discord.");
            }
        });

        app.post("/magic-invites/{guildId}", ctx -> {
            SessionData session = requireSession(ctx);
            if (session == null) {
                return;
            }

            String guildId = ctx.pathParam("guildId");
            if (!requireGuildAccess(ctx, session, guildId)) {
                return;
            }

            String requestedAlias = ctx.formParam("alias");
            if (requestedAlias == null || requestedAlias.trim().isEmpty()) {
                requestedAlias = ctx.queryParam("alias");
            }
            if ((requestedAlias == null || requestedAlias.trim().isEmpty()) && ctx.body() != null && !ctx.body().isBlank()) {
                try {
                    requestedAlias = new JSONObject(ctx.body()).optString("alias", null);
                } catch (Exception ignored) {
                    // Invalid JSON body, handled by alias validation below.
                }
            }

            if (requestedAlias == null || requestedAlias.trim().isEmpty()) {
                ctx.status(400).result("Error: Alias name is required.");
                return;
            }

            try {
                Map<String, String> invite = createAndStoreMagicInvite(jdaHolder, guildId, requestedAlias.trim());
                ctx.json(invite);
            } catch (IllegalArgumentException e) {
                ctx.status(400).result(e.getMessage());
            } catch (IllegalStateException e) {
                ctx.status(404).result(e.getMessage());
            } catch (Exception e) {
                ctx.status(500).result("Failed to communicate with Discord.");
            }
        });

        app.delete("/delete-invite/{guildId}/{code}", ctx -> {
            SessionData session = requireSession(ctx);
            if (session == null) {
                return;
            }

            String guildId = ctx.pathParam("guildId");
            if (!requireGuildAccess(ctx, session, guildId)) {
                return;
            }

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

        app.get("/config/{guildId}", ctx -> {
            SessionData session = requireSession(ctx);
            if (session == null) {
                return;
            }

            String guildId = ctx.pathParam("guildId");
            if (!requireGuildAccess(ctx, session, guildId)) {
                return;
            }

            ServerConfig config = guildConfigs.getOrDefault(guildId, new ServerConfig());
            ctx.json(config);
        });

        app.post("/config/{guildId}", ctx -> {
            SessionData session = requireSession(ctx);
            if (session == null) {
                return;
            }

            String guildId = ctx.pathParam("guildId");
            if (!requireGuildAccess(ctx, session, guildId)) {
                return;
            }

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
    }

    private static Map<String, String> createAndStoreMagicInvite(JDA jdaHolder, String guildId, String alias) {
        if (alias == null || alias.trim().isEmpty()) {
            throw new IllegalArgumentException("Error: Alias name is required.");
        }

        var guild = jdaHolder.getGuildById(guildId);
        if (guild == null) {
            throw new IllegalStateException("Guild not found.");
        }

        var channels = guild.getTextChannelsByName("test-welcome", true);
        var targetChannel = !channels.isEmpty() ? channels.get(0) : guild.getSystemChannel();
        if (targetChannel == null) {
            throw new IllegalArgumentException("No suitable channel found.");
        }

        var invite = targetChannel.createInvite().setMaxAge(0).setMaxUses(0).setUnique(true).complete();

        ServerConfig config = guildConfigs.getOrDefault(guildId, new ServerConfig());
        if (config.inviteAliases == null) {
            config.inviteAliases = new HashMap<>();
        }
        config.inviteAliases.put(invite.getCode(), alias.trim());

        guildConfigs.put(guildId, config);
        saveConfigs();

        return Map.of(
                "code", invite.getCode(),
                "inviteUrl", "https://discord.gg/" + invite.getCode(),
                "alias", alias.trim()
        );
    }

    private static void registerMinecraftRoutes(Javalin app) {
        // 1) DATA FROM PLUGIN -> BOT
        app.post("/mc-sync/update", ctx -> {
            if (!MC_SYNC_SECRET.equals(ctx.header(MC_SYNC_AUTH_HEADER))) {
                ctx.status(401);
                return;
            }

            JSONObject data = new JSONObject(ctx.body());
            String now = java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"));

            if (data.has("onlinePlayers")) {
                mcStatus.put("online", data.getInt("onlinePlayers"));
                mcStatus.put("max", data.getInt("maxPlayers"));
                mcStatus.put("day", data.getLong("gameDay"));
                mcStatus.put("time", data.getLong("gameTime"));
            }

            if (data.has("newChat")) {
                JSONArray newMsgs = data.getJSONArray("newChat");
                for (int i = 0; i < newMsgs.length(); i++) {
                    JSONObject m = newMsgs.getJSONObject(i);
                    liveChat.add(0, java.util.Map.of(
                            "user", m.getString("player"),
                            "text", m.getString("content"),
                            "time", now
                    ));
                }
                while (liveChat.size() > 50) {
                    liveChat.remove(liveChat.size() - 1);
                }
            }

            JSONArray responseQueue = new JSONArray(webToGameQueue);
            webToGameQueue.clear();
            ctx.json(responseQueue.toString());
        });

        // 2) DATA FROM BOT -> WEBSITE
        app.get("/mc-data", ctx -> ctx.json(java.util.Map.of("status", mcStatus, "chat", liveChat)));

        // 3) CHAT FROM WEBSITE -> GAME
        app.post("/mc-send-chat", ctx -> {
            String sessionId = ctx.header("Authorization");
            if (!activeSessions.containsKey(sessionId)) {
                ctx.status(401);
                return;
            }

            JSONObject body = new JSONObject(ctx.body());
            String message = body.getString("message");
            String user = activeSessions.get(sessionId).username;
            String now = java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"));

            webToGameQueue.add("§a[Web] §f" + user + ": " + message);

            liveChat.add(0, java.util.Map.of(
                    "user", user + " (Web)",
                    "text", message,
                    "time", now
            ));

            ctx.status(200);
        });
    }

    private static void registerDiscordStatsRoute(Javalin app, JDA jdaHolder) {
        app.get("/discord-stats", ctx -> {
            Guild guild = jdaHolder.getGuildById(TARGET_GUILD_ID);

            if (guild == null) {
                ctx.status(404).json(java.util.Map.of("error", "Server not found."));
                return;
            }

            int memberCount = guild.getMemberCount();
            String botStatus = "offline";

            net.dv8tion.jda.api.entities.Member targetBot = guild.getMemberById(TARGET_BOT_ID);
            if (targetBot != null && targetBot.getOnlineStatus() != net.dv8tion.jda.api.OnlineStatus.OFFLINE) {
                botStatus = "online";
            }

            ctx.json(java.util.Map.of(
                    "memberCount", memberCount,
                    "botStatus", botStatus
            ));
        });
    }

    private static SessionData requireSession(Context ctx) {
        String sessionId = ctx.header("Authorization");
        SessionData session = activeSessions.get(sessionId);
        if (session == null) {
            ctx.status(401).result("Unauthorized");
        }
        return session;
    }

    private static boolean requireGuildAccess(Context ctx, SessionData session, String guildId) {
        if (!session.adminGuilds.contains(guildId)) {
            ctx.status(403).result("Forbidden");
            return false;
        }
        return true;
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
