package com.pluginbans.core;

public record DatabaseConfig(
        DatabaseType type,
        String host,
        int port,
        String database,
        String username,
        String password,
        String sqlitePath,
        int maxPoolSize
) {
}
