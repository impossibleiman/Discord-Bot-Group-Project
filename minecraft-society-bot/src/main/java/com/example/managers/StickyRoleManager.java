package com.example.managers;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.example.model.MemberData;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;

public class StickyRoleManager {

    private static final String DATA_FILE = "memberdata.json";
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private static final Type TYPE = new TypeToken<Map<String, MemberData>>() {}.getType();

    private static Map<String, MemberData> data = loadData();

    private static Map<String, MemberData> loadData() {
        try {
            File file = new File(DATA_FILE);
            if (!file.exists()) {
                return new HashMap<>();
            }

            FileReader reader = new FileReader(file);
            Map<String, MemberData> loaded = gson.fromJson(reader, TYPE);
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

    public static MemberData getMemberData(String userId) {
        return data.get(userId);
    }

    public static void saveJoinTime(String userId, long joinTimestamp) {
        MemberData memberData = data.getOrDefault(userId, new MemberData());
        memberData.setJoinTimestamp(joinTimestamp);
        data.put(userId, memberData);
        saveData();
    }

    public static void saveLeaveData(String userId, long leaveTimestamp, java.util.List<String> roleIds) {
        MemberData memberData = data.getOrDefault(userId, new MemberData());
        memberData.setLeaveTimestamp(leaveTimestamp);
        memberData.setRoleIds(roleIds);
        data.put(userId, memberData);
        saveData();
    }
}