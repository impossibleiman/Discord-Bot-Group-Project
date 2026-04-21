package com.example.managers;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RestrictionManager {

    private static final String DATA_FILE = "restrictions.json";
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private static final Type TYPE = new TypeToken<Map<String, RestrictionRecord>>() {}.getType();

    private static Map<String, RestrictionRecord> data = loadData();

    public static class RestrictedChannelState {
        public String channelId;
        public int viewState;
        public int sendState;
    }

    public static class RestrictionRecord {
        public String guildId;
        public String userId;
        public String allowedChannelId;
        public List<RestrictedChannelState> channels = new ArrayList<>();
    }

    private static String key(String guildId, String userId) {
        return guildId + ":" + userId;
    }

    private static Map<String, RestrictionRecord> loadData() {
        try {
            File file = new File(DATA_FILE);
            if (!file.exists()) {
                return new HashMap<>();
            }

            FileReader reader = new FileReader(file);
            Map<String, RestrictionRecord> loaded = gson.fromJson(reader, TYPE);
            reader.close();

            return loaded != null ? loaded : new HashMap<>();
        } catch (Exception e) {
            e.printStackTrace();
            return new HashMap<>();
        }
    }

    private static void saveData() {
        try (FileWriter writer = new FileWriter(DATA_FILE)) {
            gson.toJson(data, writer);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static synchronized void saveRestriction(String guildId, String userId, String allowedChannelId, List<RestrictedChannelState> channels) {
        RestrictionRecord record = new RestrictionRecord();
        record.guildId = guildId;
        record.userId = userId;
        record.allowedChannelId = allowedChannelId;
        record.channels = channels != null ? new ArrayList<>(channels) : new ArrayList<>();

        data.put(key(guildId, userId), record);
        saveData();
    }

    public static synchronized RestrictionRecord getRestriction(String guildId, String userId) {
        return data.get(key(guildId, userId));
    }

    public static synchronized void clearRestriction(String guildId, String userId) {
        if (data.remove(key(guildId, userId)) != null) {
            saveData();
        }
    }
}
