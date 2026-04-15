package com.example;

import io.github.cdimascio.dotenv.Dotenv;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;

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

        // -------------------------------------------------------------------------------------------------------
        try {
            JDA jda = JDABuilder.createLight(token)
                    .addEventListeners(manager)
                    .build();
            
            jda.awaitReady();
            System.out.println("Bot is online!");
            
        // Catch all other errors
        } catch (Exception e) {
            System.err.println("Failed to start bot:");
            e.printStackTrace();
        }
    }
}