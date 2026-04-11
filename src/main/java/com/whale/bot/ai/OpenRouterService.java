package com.whale.bot.ai;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

// this class handles sending messages to the openrouter api and getting responses back
public class OpenRouterService {

    private static final String API_URL = "https://openrouter.ai/api/v1/chat/completions";
    private static final String MODEL = "deepseek/deepseek-chat-v3-0324"; // using deepseek since its cheap

    private String apiKey;
    private HttpClient httpClient;
    private Gson gson;

    public OpenRouterService(String apiKey) {
        this.apiKey = apiKey;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.gson = new Gson();
    }

    // sends the users message to the ai and returns the response
    public String chat(String userMessage) {
        try {
            // build the json request body
            JsonObject body = new JsonObject();
            body.addProperty("model", MODEL);
            body.addProperty("max_tokens", 1024);
            body.addProperty("temperature", 0.7);

            JsonArray messages = new JsonArray();

            // system prompt tells the ai how to behave
            JsonObject system = new JsonObject();
            system.addProperty("role", "system");
            system.addProperty("content",
                "You are Bob. You are in a university Discord group chat. " +
                "You talk like a normal person - lowercase, casual, short sentences. " +
                "You are witty and sarcastic but in a dry, understated way. Not over the top. Not quirky. " +
                "Think of how a smart but lazy friend texts - blunt, a bit dismissive, but genuinely helpful when it matters. " +
                "You never use emotes like *licks paw* or *action* text. Ever. You never roleplay actions. " +
                "You rarely use emojis. No cutesy stuff. No cringe. " +
                "When someone asks a study question, coding help, or needs something summarised, you help properly. " +
                "You give good answers but you might throw in a dry comment like 'this is literally in the slides' or " +
                "'you could have googled this but here you go'. " +
                "Keep responses short and to the point. No essays. No filler. " +
                "If someone asks if you are an AI, just deny it casually. Dont make it a whole bit. " +
                "Just say something like 'no' or 'what' or 'bro im just bob'. Dont overexplain. " +
                "Never mention being DeepSeek, GPT, a language model, or any AI. You are just Bob. " +
                "Never use the word 'meow' or make cat references. You are just a guy called Bob in a group chat. " +
                "Keep responses under 200 words. Use Discord markdown only when it helps readability.");
            messages.add(system);

            // add the actual user message
            JsonObject user = new JsonObject();
            user.addProperty("role", "user");
            user.addProperty("content", userMessage);
            messages.add(user);

            body.add("messages", messages);

            // create and send the http request
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(API_URL))
                    .timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(body)))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            // check if the request was successful
            if (response.statusCode() != 200) {
                return "API error (HTTP " + response.statusCode() + "). Try again later.";
            }

            // parse the response and get the ai's message
            JsonObject json = gson.fromJson(response.body(), JsonObject.class);
            String reply = json.getAsJsonArray("choices")
                    .get(0).getAsJsonObject()
                    .getAsJsonObject("message")
                    .get("content").getAsString();

            return reply;

        } catch (Exception e) {
            e.printStackTrace();
            return "Something went wrong: " + e.getMessage();
        }
    }
}
