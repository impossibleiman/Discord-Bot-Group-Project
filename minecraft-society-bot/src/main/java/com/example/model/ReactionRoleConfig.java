package com.example.model;

import java.util.ArrayList;
import java.util.List;

public class ReactionRoleConfig {
    public String templateId;
    public String channelId;
    public String messageId;
    public String content;
    public List<ReactionRoleButtonConfig> buttons = new ArrayList<>();
}
