package com.pluginbans.core;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class DatabaseManager implements AutoCloseable {
    private final HikariDataSource dataSource;
    private final ExecutorService executor;

    public DatabaseManager(DatabaseConfig config) {
        Objects.requireNonNull(config, "config");
        this.dataSource = new HikariDataSource(buildHikariConfig(config));
        this.executor = Executors.newFixedThreadPool(Math.max(2, config.maxPoolSize()));
        initializeSchema();
    }

    private HikariConfig buildHikariConfig(DatabaseConfig config) {
        HikariConfig hikari = new HikariConfig();
        hikari.setMaximumPoolSize(config.maxPoolSize());
        hikari.setPoolName("PluginBans-Пул");
        hikari.setAutoCommit(true);
        if (config.type() == DatabaseType.SQLITE) {
            hikari.setJdbcUrl("jdbc:sqlite:" + config.sqlitePath());
            hikari.setDriverClassName("org.sqlite.JDBC");
        } else {
            hikari.setJdbcUrl("jdbc:mysql://" + config.host() + ":" + config.port() + "/" + config.database()
                    + "?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=UTC");
            hikari.setUsername(config.username());
            hikari.setPassword(config.password());
            hikari.setDriverClassName("com.mysql.cj.jdbc.Driver");
        }
        return hikari;
    }

    private void initializeSchema() {
        try (Connection connection = dataSource.getConnection()) {
            DatabaseSchema.ensure(connection);
        } catch (SQLException exception) {
            throw new IllegalStateException("Не удалось инициализировать базу данных.", exception);
        }
    }

    public HikariDataSource dataSource() {
        return dataSource;
    }

    public ExecutorService executor() {
        return executor;
    }

    @Override
    public void close() {
        executor.shutdownNow();
        dataSource.close();
    }
}
