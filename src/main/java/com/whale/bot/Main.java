package com.whale.bot;

import com.whale.bot.ai.AIChatListener;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.requests.GatewayIntent;

public class Main {

    public static void main(String[] args) throws Exception {

        // get the tokens from environment variables
        String discordToken = System.getenv("DISCORD_TOKEN");
        String openRouterKey = System.getenv("OPENROUTER_API_KEY");

        // check if tokens are set
        if (discordToken == null || openRouterKey == null) {
            System.out.println("Please set DISCORD_TOKEN and OPENROUTER_API_KEY environment variables");
            return;
        }

        // build the bot with the intents we need and add the ai listener
        JDABuilder.createDefault(discordToken)
                .enableIntents(
                        GatewayIntent.MESSAGE_CONTENT,
                        GatewayIntent.GUILD_MEMBERS,
                        GatewayIntent.GUILD_MESSAGES
                )
                .addEventListeners(new AIChatListener(openRouterKey))
                .build()
                .awaitReady();

        System.out.println("Bot is running!");
    }
}
