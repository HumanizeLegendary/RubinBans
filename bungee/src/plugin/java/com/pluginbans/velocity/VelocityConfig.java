package com.pluginbans.velocity;

import com.pluginbans.core.DatabaseConfig;
import com.pluginbans.core.DatabaseType;

import java.nio.file.Path;
import java.util.List;

public record VelocityConfig(
        DatabaseConfig databaseConfig,
        List<String> lobbyServers,
        int throttleMaxConnections,
        int throttleWindowSeconds,
        Path auditPath
) {
    public static VelocityConfig defaultConfig(Path dataDirectory) {
        return new VelocityConfig(
                new DatabaseConfig(DatabaseType.SQLITE, "localhost", 3306, "pluginbans", "root", "", dataDirectory.resolve("pluginbans-velocity.db").toString(), 10),
                List.of("lobby", "hub"),
                5,
                10,
                dataDirectory.resolve("audit.log")
        );
    }
}
