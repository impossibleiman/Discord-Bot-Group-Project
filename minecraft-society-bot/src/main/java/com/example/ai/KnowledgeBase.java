package com.example.ai;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

// loads text files from the knowledge folder and combines them into a single string
// this gets injected into the system prompt so the ai has context about the module
public class KnowledgeBase {

    private static final String KNOWLEDGE_DIR = "knowledge";
    private static final int MAX_CHARS = 8000; // keep it under the token limit

    private String content;

    public KnowledgeBase() {
        this.content = loadFiles();
    }

    // reads all .txt files from the knowledge folder
    private String loadFiles() {
        Path dir = Paths.get(KNOWLEDGE_DIR);

        if (!Files.exists(dir)) {
            System.out.println("No knowledge folder found - running without knowledge base");
            return "";
        }

        StringBuilder sb = new StringBuilder();

        try (Stream<Path> files = Files.list(dir)) {
            files.filter(f -> f.toString().endsWith(".txt"))
                 .sorted()
                 .forEach(f -> {
                     try {
                         String fileName = f.getFileName().toString().replace(".txt", "");
                         sb.append("--- ").append(fileName).append(" ---\n");
                         sb.append(Files.readString(f));
                         sb.append("\n\n");
                     } catch (IOException e) {
                         System.out.println("Failed to read: " + f.getFileName());
                     }
                 });
        } catch (IOException e) {
            System.out.println("Failed to read knowledge folder: " + e.getMessage());
            return "";
        }

        String result = sb.toString();

        // trim if too long so we dont blow the context window
        if (result.length() > MAX_CHARS) {
            result = result.substring(0, MAX_CHARS) + "\n\n[content trimmed due to length]";
            System.out.println("Knowledge base trimmed to " + MAX_CHARS + " chars");
        }

        System.out.println("Loaded knowledge base: " + result.length() + " chars");
        return result;
    }

    // returns the knowledge content, empty string if nothing loaded
    public String getContent() {
        return content;
    }

    // check if we actually have any knowledge loaded
    public boolean hasContent() {
        return !content.isEmpty();
    }
}
