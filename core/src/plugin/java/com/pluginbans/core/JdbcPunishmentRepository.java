package com.pluginbans.core;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

public final class JdbcPunishmentRepository implements PunishmentRepository {
    private final DataSource dataSource;
    private final ExecutorService executor;

    public JdbcPunishmentRepository(DataSource dataSource, ExecutorService executor) {
        this.dataSource = dataSource;
        this.executor = executor;
    }

    @Override
    public CompletableFuture<Void> addPunishment(PunishmentRecord record) {
        return CompletableFuture.runAsync(() -> {
            String sql = """
                    INSERT INTO pluginbans_punishments
                    (id, uuid, type, reason, duration_seconds, issued_by, issued_at, ip, active, nnr)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """;
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, record.id());
                statement.setString(2, record.uuid().toString());
                statement.setString(3, record.type());
                statement.setString(4, record.reason());
                statement.setLong(5, record.durationSeconds());
                statement.setString(6, record.issuedBy());
                statement.setLong(7, record.issuedAt().toEpochMilli());
                statement.setString(8, record.ip());
                statement.setBoolean(9, record.active());
                statement.setBoolean(10, record.nnr());
                statement.executeUpdate();
            } catch (SQLException exception) {
                throw new IllegalStateException("Не удалось записать наказание.", exception);
            }
        }, executor);
    }

    @Override
    public CompletableFuture<List<PunishmentRecord>> findActiveByUuid(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> queryList(
                "SELECT * FROM pluginbans_punishments WHERE uuid = ? AND active = 1",
                statement -> statement.setString(1, uuid.toString())
        ), executor);
    }

    @Override
    public CompletableFuture<List<PunishmentRecord>> findActiveByIp(String ip) {
        return CompletableFuture.supplyAsync(() -> queryList(
                "SELECT * FROM pluginbans_punishments WHERE ip = ? AND active = 1",
                statement -> statement.setString(1, ip)
        ), executor);
    }

    @Override
    public CompletableFuture<Optional<PunishmentRecord>> findById(String id) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT * FROM pluginbans_punishments WHERE id = ?";
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, id);
                try (ResultSet resultSet = statement.executeQuery()) {
                    if (resultSet.next()) {
                        return Optional.of(map(resultSet));
                    }
                }
            } catch (SQLException exception) {
                throw new IllegalStateException("Не удалось загрузить наказание.", exception);
            }
            return Optional.empty();
        }, executor);
    }

    @Override
    public CompletableFuture<Long> countActiveWarns(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT COUNT(*) FROM pluginbans_punishments WHERE uuid = ? AND type = ? AND active = 1";
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, uuid.toString());
                statement.setString(2, "WARN");
                try (ResultSet resultSet = statement.executeQuery()) {
                    if (resultSet.next()) {
                        return resultSet.getLong(1);
                    }
                }
            } catch (SQLException exception) {
                throw new IllegalStateException("Не удалось получить количество предупреждений.", exception);
            }
            return 0L;
        }, executor);
    }

    @Override
    public CompletableFuture<Void> deactivate(String id) {
        return CompletableFuture.runAsync(() -> {
            String sql = "UPDATE pluginbans_punishments SET active = 0 WHERE id = ?";
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, id);
                statement.executeUpdate();
            } catch (SQLException exception) {
                throw new IllegalStateException("Не удалось деактивировать наказание.", exception);
            }
        }, executor);
    }

    private List<PunishmentRecord> queryList(String sql, StatementConsumer binder) {
        List<PunishmentRecord> records = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            binder.accept(statement);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    records.add(map(resultSet));
                }
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Не удалось загрузить список наказаний.", exception);
        }
        return records;
    }

    private PunishmentRecord map(ResultSet resultSet) throws SQLException {
        return new PunishmentRecord(
                resultSet.getString("id"),
                UUID.fromString(resultSet.getString("uuid")),
                resultSet.getString("type"),
                resultSet.getString("reason"),
                resultSet.getLong("duration_seconds"),
                resultSet.getString("issued_by"),
                Instant.ofEpochMilli(resultSet.getLong("issued_at")),
                resultSet.getString("ip"),
                resultSet.getBoolean("active"),
                resultSet.getBoolean("nnr")
        );
    }

    @FunctionalInterface
    private interface StatementConsumer {
        void accept(PreparedStatement statement) throws SQLException;
    }
}
