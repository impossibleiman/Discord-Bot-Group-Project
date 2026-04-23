package com.example.ai;

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
    public static final String DEFAULT_PROFILE_NAME = "Bob";
    public static final String DEFAULT_PROFILE_PROMPT =
        "You are Bob, a guy in a university Discord server. " +
            "Reply directly with just your message, plain text only. " +
            "Talk casually like a normal person, low effort, half paying attention. " +
            "Help when asked, keep answers short unless the question needs more. " +
            "Dry humour comes out naturally sometimes. " +
            "When someone tells you to be quiet or stop, just acknowledge it briefly and stop. " +
            "Input comes as 'username: message' so you know who is talking.";
    // Prefer DeepSeek V3.2, then fall back if the model is unavailable or rate-limited.
    private static final List<String> MODELS = List.of(
            "deepseek/deepseek-chat-v3.2",
            "deepseek/deepseek-chat-v3-0324"
    );
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

    private String buildSystemPrompt(String basePrompt) {
        StringBuilder prompt = new StringBuilder();

        prompt.append(basePrompt == null || basePrompt.isBlank() ? DEFAULT_PROFILE_PROMPT : basePrompt);

        if (knowledgeBase.hasContent()) {
            prompt.append("\n\nStudy material for reference:\n\n");
            prompt.append(knowledgeBase.getContent());
        }

        return prompt.toString();
    }

    public String chat(String userMessage, String channelId, String profileName, String systemPrompt) {
        try {
            // add conversation history for this channel
            String resolvedProfileName = profileName == null || profileName.isBlank() ? DEFAULT_PROFILE_NAME : profileName;
            String historyKey = channelId + "::" + resolvedProfileName;
            List<JsonObject> history = channelHistory.getOrDefault(historyKey, new ArrayList<>());
            boolean hitRateLimit = false;
            int lastStatusCode = -1;

            for (String model : MODELS) {
                JsonObject body = new JsonObject();
                body.addProperty("model", model);
                body.addProperty("max_tokens", 1024);
                body.addProperty("temperature", 0.8);

                JsonArray messages = new JsonArray();

                JsonObject system = new JsonObject();
                system.addProperty("role", "system");
                system.addProperty("content", buildSystemPrompt(systemPrompt));
                messages.add(system);

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
                lastStatusCode = response.statusCode();

                if (lastStatusCode == 429) {
                    hitRateLimit = true;
                    continue;
                }

                if (lastStatusCode != 200) {
                    continue;
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

                channelHistory.put(historyKey, history);
                return reply;
            }

            if (hitRateLimit) {
                return "I hit a free-tier rate limit (HTTP 429). Please try again in about 30-60 seconds.";
            }

            if (lastStatusCode > 0) {
                return "API error (HTTP " + lastStatusCode + "). Try again later.";
            }

            return "API error. Try again later.";

        } catch (Exception e) {
            e.printStackTrace();
            return "Something went wrong: " + e.getMessage();
        }
    }

    public String chat(String userMessage, String channelId) {
        return chat(userMessage, channelId, DEFAULT_PROFILE_NAME, DEFAULT_PROFILE_PROMPT);
    }
}
