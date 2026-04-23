package com.example.ai;

import com.example.MinecraftSocietyBot;
import com.example.model.ServerConfig;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

// listens for messages and responds with ai in the ai-chat channel or when mentioned
public class AIChatListener extends ListenerAdapter {

    private static final String AI_CHANNEL_NAME = "ai-chat"; // channel where bot auto replies
    private static final int MAX_LENGTH = 2000; // discord character limit

    private OpenRouterService aiService;

    public AIChatListener(String openRouterApiKey) {
        this.aiService = new OpenRouterService(openRouterApiKey);
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        // dont respond to other bots or ourselves
        if (event.getAuthor().isBot()) return;
        if (!event.isFromGuild()) return;

        String message = event.getMessage().getContentRaw();
        ServerConfig config = MinecraftSocietyBot.getGuildConfig(event.getGuild().getId());

        // check if the bot was mentioned or if its in the ai channel
        boolean isMentioned = event.getMessage().getMentions().isMentioned(
                event.getJDA().getSelfUser());
        boolean isAIChannel = event.getChannel().getName().equalsIgnoreCase(AI_CHANNEL_NAME);
        if (config.aiChannelId != null && !config.aiChannelId.isBlank()) {
            isAIChannel = event.getChannel().getId().equals(config.aiChannelId);
        }

        if (!isMentioned && !isAIChannel) return;

        // remove the @mention from the message so we just get the actual question
        String prompt = message
                .replaceAll("<@!?" + event.getJDA().getSelfUser().getId() + ">", "")
                .trim();

        // include the username so bob knows who hes talking to
        String username = event.getAuthor().getName();
        String fullPrompt = username + ": " + prompt;

        // if they just mentioned the bot with no message
        if (prompt.isEmpty()) {
            event.getChannel().sendMessage("what").queue();
            return;
        }

        // show typing while we wait for the api
        event.getChannel().sendTyping().queue();

        // get channel id for conversation memory
        String channelId = event.getChannel().getId();

        // use a virtual thread so we dont block the bot while waiting for the api response
        Thread.startVirtualThread(() -> {
            String reply = aiService.chat(fullPrompt, channelId);

            // cut off the message if its too long for discord
            if (reply.length() > MAX_LENGTH) {
                reply = reply.substring(0, MAX_LENGTH - 15) + "\n\n*truncated*";
            }

            event.getMessage().reply(reply).queue();
        });
    }
}
