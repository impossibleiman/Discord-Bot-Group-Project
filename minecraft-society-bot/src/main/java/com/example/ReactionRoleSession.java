package com.example;

import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import java.util.ArrayList;
import java.util.List;

public class ReactionRoleSession {
    public MessageChannel targetChannel;
    public String messageBody;
    public List<ButtonData> buttons = new ArrayList<>();
    public String currentStep = "CHANNEL"; 
    public ButtonData pendingButton; 

    public static class ButtonData {
        public String roleId;
        public String label;
        public String emoji; // Stores the emoji as a string
    }
}