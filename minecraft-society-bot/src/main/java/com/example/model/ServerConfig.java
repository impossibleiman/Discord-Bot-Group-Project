package com.example.model;

import java.util.Map;

public class ServerConfig {
    public String nickname;
    public String welcomeMessage; // Make sure this is public!
    public String leaveMessage;
    public String welcomeChannelId;
    public String leaveChannelId;
    public String auditEditChannelId;
    public String auditDeleteChannelId;
    public String aiChannelId;
    public String ticketLogChannelId;
    public Map<String, String> inviteAliases;
    public Map<String, ReactionRoleConfig> reactionRoleConfigs;
}