package com.example;

import java.util.Map;

public class ServerConfig {
    public String nickname;
    public Map<String, String> inviteAliases;
    
    // NEW: The Embed Configuration Object
    public WelcomeEmbedConfig welcomeEmbed;

    // Sub-class mapping directly to our JavaScript object
    public static class WelcomeEmbedConfig {
        public String authorName;
        public String authorIcon;
        public String title;
        public String url;
        public String description;
        public String color;
        public String thumbnail;
        public String image;
        public String footerText;
        public String footerIcon;
    }
}