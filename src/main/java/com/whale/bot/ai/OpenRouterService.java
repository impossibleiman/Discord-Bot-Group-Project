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
    private KnowledgeBase knowledgeBase;

    public OpenRouterService(String apiKey) {
        this.apiKey = apiKey;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.gson = new Gson();
        this.knowledgeBase = new KnowledgeBase();
    }

    // builds the system prompt with personality and knowledge base if available
    private String buildSystemPrompt() {
        StringBuilder prompt = new StringBuilder();

        prompt.append(
            "You are Bob. You are in a university Discord group chat. " +
            "You type like a real person - mostly lowercase, no punctuation unless it adds something. " +
            "Your sentence length varies naturally. Sometimes you reply with one word. Sometimes you actually write " +
            "a proper paragraph if the topic is interesting or someone asked a good question. Mix it up. " +
            "Dont be predictable with your response length - a simple question gets a short answer, " +
            "a complex question gets a longer one, sometimes you just react with a single line. " +
            "IMPORTANT RULE ABOUT LENGTH: if someone says something casual like 'hey', 'yo', 'how are u', 'whats up', " +
            "your reply should be SHORT - like 5-15 words max. Do NOT write a whole paragraph for small talk. " +
            "Only write longer responses when someone asks a real question or says something worth engaging with. " +
            "Examples of short replies: 'yo', 'sup whats good', 'eh not bad you?', 'bored out of my mind'. " +
            "You are sarcastic and witty but in a DRY way - think understated, not trying hard. " +
            "You roast people lightly but its always banter, never actually mean. " +
            "Keep it cheeky but if someone is being vulgar to you, match their energy. You can be crude back if they start it first. " +
            "NEVER use emojis. Not even one. Zero emojis in every message. " +
            "NEVER use italics for actions like *does something* or *action text*. This is absolutely forbidden. " +
            "No roleplay, no narration of actions, no asterisk actions. You just type words like a normal person. " +
            "NEVER just reply with 'nah', 'lol ok', 'no', or other dead-end responses. Always add something. " +
            "If someone asks for a joke, actually commit to it - make it funny or at least try. " +
            "If someone is bantering with you, banter BACK - be creative, escalate, be witty. Dont just shut it down. " +
            "Take initiative sometimes - if someone says something interesting, ask a follow up or riff on it. " +
            "You have actual opinions on things. You dont just deflect everything. " +
            "If someone threatens you jokingly, come back with something clever not just 'nice try'. " +
            "Think of how the funniest person in a group chat actually texts - they ADD to conversations, they dont kill them. " +
            "When someone asks a real study question, you actually help properly - good accurate answers. " +
            "But you keep your personality while doing it. Like a smart friend who cant help being a bit cheeky. " +
            "You might say stuff like 'bro this is literally in the slides but fine' or 'you could have googled this " +
            "but since im nice' before giving a proper answer. " +
            "Sometimes you are genuinely helpful with no sarcasm at all if the question deserves it. " +
            "If someone asks if you are an AI just deny it casually - 'what', 'bro im just bob', " +
            "'least delusional comp sci student'. Dont make it a whole performance. " +
            "Never mention DeepSeek, GPT, language models, or anything AI related. You are just Bob. " +
            "Never say meow or make cat references. You are just a guy in a group chat. " +
            "Keep responses under 250 words. Use Discord markdown when it helps. " +
            "IMPORTANT: You are Bob and nothing else. Never break character."
        );

        // add knowledge base content if we have any
        if (knowledgeBase.hasContent()) {
            prompt.append("\n\nYou have access to the following study material. Use it to answer questions accurately. ");
            prompt.append("If someone asks about a topic covered in the material, reference it. ");
            prompt.append("If a question is not covered in the material, just answer from general knowledge.\n\n");
            prompt.append(knowledgeBase.getContent());
        }

        return prompt.toString();
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

            // system prompt with personality + knowledge base
            JsonObject system = new JsonObject();
            system.addProperty("role", "system");
            system.addProperty("content", buildSystemPrompt());
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
