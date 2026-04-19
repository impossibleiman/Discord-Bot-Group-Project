package com.example;

import io.github.cdimascio.dotenv.Dotenv;
import io.javalin.Javalin;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.requests.GatewayIntent;

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

        try {
            JDA jda = JDABuilder.createDefault(token)
                    .enableIntents(GatewayIntent.MESSAGE_CONTENT, GatewayIntent.GUILD_MEMBERS, GatewayIntent.GUILD_MESSAGES) // Needed for some commands
                    .addEventListeners(manager, listener)
                    .build();
            
            jda.awaitReady();
            System.out.println("Bot is online!");
            
        // Catch all other errors
        } catch (Exception e) {
            System.err.println("Failed to start bot:");
            e.printStackTrace();
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
                    "&scope=identify";
            ctx.redirect(url);
        });

        app.get("/callback", ctx -> {
            String code = ctx.queryParam("code");
            if(code == null) {
                ctx.status(400).result("Missing code parameter");
                return;
            }
            ctx.result("Received code: " + code);
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
}