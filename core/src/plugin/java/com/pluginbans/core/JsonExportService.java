package com.pluginbans.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;

public final class JsonExportService {
    private final Gson gson;

    public JsonExportService() {
        this.gson = new GsonBuilder().setPrettyPrinting().create();
    }

    public void exportPunishments(Path file, Collection<PunishmentRecord> records) {
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, gson.toJson(records));
        } catch (IOException exception) {
            throw new IllegalStateException("Не удалось выполнить экспорт JSON.", exception);
        }
    }
}
