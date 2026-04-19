package com.example;

import java.util.Map;

public class ServerConfig {
    public String nickname;
    public Map<String, String> inviteAliases;
    public WelcomeEmbedConfig welcomeEmbed;

    public static class WelcomeEmbedConfig {
        public String authorName;
        public String authorIcon;
        public String title;
        public String description;
        public String color;
        public String thumbnail;
        public String image;
        public String footerText;
    }
}