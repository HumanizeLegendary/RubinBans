package com.pluginbans.core;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public final class DatabaseSchema {
    private static final String CREATE_PUNISHMENTS = """
            CREATE TABLE IF NOT EXISTS pluginbans_punishments (
                internal_id VARCHAR(64) PRIMARY KEY,
                uuid VARCHAR(36) NOT NULL,
                ip VARCHAR(45),
                ip_hash VARCHAR(128),
                type VARCHAR(32) NOT NULL,
                reason TEXT NOT NULL,
                actor VARCHAR(64) NOT NULL,
                start_time BIGINT NOT NULL,
                end_time BIGINT,
                active BOOLEAN NOT NULL,
                silent BOOLEAN NOT NULL
            )
            """;

    private static final String CREATE_HISTORY = """
            CREATE TABLE IF NOT EXISTS pluginbans_punishment_history (
                id VARCHAR(96) PRIMARY KEY,
                uuid VARCHAR(36) NOT NULL,
                ip VARCHAR(45),
                ip_hash VARCHAR(128),
                type VARCHAR(32) NOT NULL,
                reason TEXT NOT NULL,
                actor VARCHAR(64) NOT NULL,
                start_time BIGINT NOT NULL,
                end_time BIGINT,
                internal_id VARCHAR(64) NOT NULL,
                action VARCHAR(32) NOT NULL,
                action_time BIGINT NOT NULL
            )
            """;

    private DatabaseSchema() {
    }

    public static void ensure(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(CREATE_PUNISHMENTS);
            statement.execute(CREATE_HISTORY);
        }
    }
}
