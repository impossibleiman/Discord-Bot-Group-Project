package com.whale.bot.ai;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// this class handles sending messages to the openrouter api and getting responses back
public class OpenRouterService {

    private static final String API_URL = "https://openrouter.ai/api/v1/chat/completions";
    private static final String MODEL = "deepseek/deepseek-chat-v3-0324"; // using deepseek since its cheap
    private static final int MAX_HISTORY = 10; // remember last 10 messages per channel

    private String apiKey;
    private HttpClient httpClient;
    private Gson gson;
    private KnowledgeBase knowledgeBase;
    private Map<String, List<JsonObject>> channelHistory;

    public OpenRouterService(String apiKey) {
        this.apiKey = apiKey;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.gson = new Gson();
        this.knowledgeBase = new KnowledgeBase();
        this.channelHistory = new HashMap<>();
    }

    private String buildSystemPrompt() {
        StringBuilder prompt = new StringBuilder();

        prompt.append(
            "You are Bob, a guy in a university Discord group chat. " +
            "Type like a normal uni student texting. Lowercase, casual, no overthinking it. " +
            "You are naturally witty and a bit sarcastic but you dont force it. " +
            "Match the energy of whoever is talking to you. Short message gets a short reply. " +
            "Big question gets a proper answer. Just be natural about it. " +
            "You help with study stuff when asked - you actually give good answers. " +
            "You banter with people but its always friendly. If they get vulgar you can match it. " +
            "No emojis. No asterisk actions like *does something*. No roleplay. Just normal text. " +
            "Write on one line like a text message, dont split into multiple lines unless needed. " +
            "If asked if you are AI, just say no casually and move on. Never say you are DeepSeek or GPT or any AI. " +
            "If asked personal stuff like height or age, deflect with a joke instead of making things up. " +
            "Messages come as 'username: message' so you know who is talking. " +
            "You are just Bob. Nothing more. Keep it real and easy"
        );

        if (knowledgeBase.hasContent()) {
            prompt.append("\n\nStudy material for reference:\n\n");
            prompt.append(knowledgeBase.getContent());
        }

        return prompt.toString();
    }

    public String chat(String userMessage, String channelId) {
        try {
            JsonObject body = new JsonObject();
            body.addProperty("model", MODEL);
            body.addProperty("max_tokens", 1024);
            body.addProperty("temperature", 0.8);

            JsonArray messages = new JsonArray();

            JsonObject system = new JsonObject();
            system.addProperty("role", "system");
            system.addProperty("content", buildSystemPrompt());
            messages.add(system);

            // add conversation history for this channel
            List<JsonObject> history = channelHistory.getOrDefault(channelId, new ArrayList<>());
            for (JsonObject msg : history) {
                messages.add(msg);
            }

            JsonObject user = new JsonObject();
            user.addProperty("role", "user");
            user.addProperty("content", userMessage);
            messages.add(user);

            body.add("messages", messages);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(API_URL))
                    .timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(body)))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                return "API error (HTTP " + response.statusCode() + "). Try again later.";
            }

            JsonObject json = gson.fromJson(response.body(), JsonObject.class);
            String reply = json.getAsJsonArray("choices")
                    .get(0).getAsJsonObject()
                    .getAsJsonObject("message")
                    .get("content").getAsString();

            // save to history
            JsonObject userMsg = new JsonObject();
            userMsg.addProperty("role", "user");
            userMsg.addProperty("content", userMessage);

            JsonObject assistantMsg = new JsonObject();
            assistantMsg.addProperty("role", "assistant");
            assistantMsg.addProperty("content", reply);

            history.add(userMsg);
            history.add(assistantMsg);

            while (history.size() > MAX_HISTORY * 2) {
                history.remove(0);
                history.remove(0);
            }

            channelHistory.put(channelId, history);

            return reply;

        } catch (Exception e) {
            e.printStackTrace();
            return "Something went wrong: " + e.getMessage();
        }
    }
}
