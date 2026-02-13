package com.pluginbans.velocity;

import com.pluginbans.core.DatabaseConfig;
import com.pluginbans.core.DatabaseType;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class VelocityConfigLoader {
    private VelocityConfigLoader() {
    }

    public static VelocityConfig load(Path dataDirectory) {
        VelocityConfig defaults = VelocityConfig.defaultConfig(dataDirectory);
        Path configPath = dataDirectory.resolve("config.toml");
        if (!Files.exists(configPath)) {
            writeDefault(configPath, defaults);
            return defaults;
        }
        try {
            List<String> lines = Files.readAllLines(configPath);
            List<String> lobbyServers = parseList(lines, "lobby-servers");
            String dbType = parseString(lines, "type", "SQLITE");
            String host = parseString(lines, "host", defaults.databaseConfig().host());
            int port = parseInt(lines, "port", defaults.databaseConfig().port());
            String database = parseString(lines, "database", defaults.databaseConfig().database());
            String user = parseString(lines, "user", defaults.databaseConfig().username());
            String password = parseString(lines, "password", defaults.databaseConfig().password());
            int poolSize = parseInt(lines, "pool-size", defaults.databaseConfig().maxPoolSize());
            String sqliteFile = parseString(lines, "sqlite-file", defaults.databaseConfig().sqlitePath());
            int throttleMax = parseInt(lines, "max-connections", defaults.throttleMaxConnections());
            int throttleWindow = parseInt(lines, "window-seconds", defaults.throttleWindowSeconds());
            DatabaseConfig databaseConfig = new DatabaseConfig(
                    DatabaseType.valueOf(dbType.toUpperCase(Locale.ROOT)),
                    host,
                    port,
                    database,
                    user,
                    password,
                    sqliteFile,
                    poolSize
            );
            return new VelocityConfig(
                    databaseConfig,
                    lobbyServers.isEmpty() ? defaults.lobbyServers() : lobbyServers,
                    throttleMax,
                    throttleWindow,
                    defaults.auditPath()
            );
        } catch (IOException exception) {
            throw new IllegalStateException("Не удалось загрузить config.toml.", exception);
        }
    }

    private static void writeDefault(Path configPath, VelocityConfig defaults) {
        try {
            Files.createDirectories(configPath.getParent());
            String content = """
                    [pluginbans]
                    lobby-servers = ["lobby", "hub"]

                    [database]
                    type = "SQLITE"
                    host = "localhost"
                    port = 3306
                    database = "pluginbans"
                    user = "root"
                    password = ""
                    pool-size = 10
                    sqlite-file = "%s"

                    [throttle]
                    max-connections = 5
                    window-seconds = 10
                    """.formatted(defaults.databaseConfig().sqlitePath());
            Files.writeString(configPath, content);
        } catch (IOException exception) {
            throw new IllegalStateException("Не удалось создать config.toml.", exception);
        }
    }

    private static String parseString(List<String> lines, String key, String fallback) {
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.startsWith(key + " =")) {
                String value = trimmed.substring(trimmed.indexOf('=') + 1).trim();
                return stripQuotes(value);
            }
        }
        return fallback;
    }

    private static int parseInt(List<String> lines, String key, int fallback) {
        String value = parseString(lines, key, null);
        if (value == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private static List<String> parseList(List<String> lines, String key) {
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.startsWith(key + " =")) {
                int start = trimmed.indexOf('[');
                int end = trimmed.lastIndexOf(']');
                if (start < 0 || end < 0 || end <= start) {
                    return List.of();
                }
                String listContent = trimmed.substring(start + 1, end);
                String[] parts = listContent.split(",");
                List<String> result = new ArrayList<>();
                for (String part : parts) {
                    String value = stripQuotes(part.trim());
                    if (!value.isBlank()) {
                        result.add(value);
                    }
                }
                return result;
            }
        }
        return List.of();
    }

    private static String stripQuotes(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.startsWith("\"") && trimmed.endsWith("\"") && trimmed.length() >= 2) {
            return trimmed.substring(1, trimmed.length() - 1);
        }
        return trimmed;
    }
}
