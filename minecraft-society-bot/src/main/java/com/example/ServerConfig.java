package com.example;

import java.util.HashMap;
import java.util.Map;

public class ServerConfig {
    public String nickname = "Society Bot";
    public String welcomeMessage = "Welcome $USER to $GUILD! Joined via: $INVITE_ALIAS";
    
    // Stores: "inviteCode" -> "Alias Name" (e.g., "ABC123" -> "Instagram")
    public Map<String, String> inviteAliases = new HashMap<>();
}