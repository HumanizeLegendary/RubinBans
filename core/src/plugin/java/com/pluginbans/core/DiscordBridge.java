package com.pluginbans.core;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

public final class DiscordBridge {
    private final Gson gson = new Gson();

    public String buildPayload(PunishmentRecord record) {
        JsonObject json = new JsonObject();
        json.addProperty("uuid", record.uuid().toString());
        json.addProperty("type", record.type());
        json.addProperty("reason", record.reason());
        json.addProperty("duration", record.durationSeconds());
        json.addProperty("id", record.id());
        return gson.toJson(json);
    }
}
