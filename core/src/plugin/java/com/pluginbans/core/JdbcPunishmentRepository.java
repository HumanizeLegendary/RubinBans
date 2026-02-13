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
                    (internal_id, uuid, ip, ip_hash, type, reason, actor, start_time, end_time, active, silent)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """;
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, record.internalId());
                statement.setString(2, record.uuid().toString());
                statement.setString(3, record.ip());
                statement.setString(4, record.ipHash());
                statement.setString(5, record.type().name());
                statement.setString(6, record.reason());
                statement.setString(7, record.actor());
                statement.setLong(8, record.startTime().toEpochMilli());
                if (record.endTime() == null) {
                    statement.setNull(9, java.sql.Types.BIGINT);
                } else {
                    statement.setLong(9, record.endTime().toEpochMilli());
                }
                statement.setBoolean(10, record.active());
                statement.setBoolean(11, record.silent());
                statement.executeUpdate();
            } catch (SQLException exception) {
                throw new IllegalStateException("Не удалось записать наказание.", exception);
            }
            insertHistory(buildHistory(record, "CREATE"));
        }, executor);
    }

    @Override
    public CompletableFuture<Void> deactivate(String internalId, String actor, String reason, String action) {
        return CompletableFuture.runAsync(() -> {
            PunishmentRecord existing = null;
            String selectSql = "SELECT * FROM pluginbans_punishments WHERE internal_id = ?";
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement(selectSql)) {
                statement.setString(1, internalId);
                try (ResultSet resultSet = statement.executeQuery()) {
                    if (resultSet.next()) {
                        existing = map(resultSet);
                    }
                }
            } catch (SQLException exception) {
                throw new IllegalStateException("Не удалось загрузить наказание.", exception);
            }
            String sql = "UPDATE pluginbans_punishments SET active = 0 WHERE internal_id = ?";
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, internalId);
                statement.executeUpdate();
            } catch (SQLException exception) {
                throw new IllegalStateException("Не удалось деактивировать наказание.", exception);
            }
            if (existing != null) {
                PunishmentRecord removed = new PunishmentRecord(
                        existing.uuid(),
                        existing.ip(),
                        existing.ipHash(),
                        existing.type(),
                        reason,
                        actor,
                        existing.startTime(),
                        existing.endTime(),
                        false,
                        existing.internalId(),
                        existing.silent()
                );
                insertHistory(buildHistory(removed, action));
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
    public CompletableFuture<List<PunishmentRecord>> findActiveByIpHash(String ipHash) {
        return CompletableFuture.supplyAsync(() -> queryList(
                "SELECT * FROM pluginbans_punishments WHERE ip_hash = ? AND active = 1",
                statement -> statement.setString(1, ipHash)
        ), executor);
    }

    @Override
    public CompletableFuture<Optional<PunishmentRecord>> findByInternalId(String internalId) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT * FROM pluginbans_punishments WHERE internal_id = ?";
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, internalId);
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
    public CompletableFuture<List<PunishmentHistoryRecord>> findHistory(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT * FROM pluginbans_punishment_history WHERE uuid = ? ORDER BY action_time DESC";
            List<PunishmentHistoryRecord> records = new ArrayList<>();
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, uuid.toString());
                try (ResultSet resultSet = statement.executeQuery()) {
                    while (resultSet.next()) {
                        records.add(mapHistory(resultSet));
                    }
                }
            } catch (SQLException exception) {
                throw new IllegalStateException("Не удалось загрузить историю наказаний.", exception);
            }
            return records;
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
        Long end = resultSet.getObject("end_time", Long.class);
        return new PunishmentRecord(
                UUID.fromString(resultSet.getString("uuid")),
                resultSet.getString("ip"),
                resultSet.getString("ip_hash"),
                PunishmentType.valueOf(resultSet.getString("type")),
                resultSet.getString("reason"),
                resultSet.getString("actor"),
                Instant.ofEpochMilli(resultSet.getLong("start_time")),
                end == null ? null : Instant.ofEpochMilli(end),
                resultSet.getBoolean("active"),
                resultSet.getString("internal_id"),
                resultSet.getBoolean("silent")
        );
    }

    private PunishmentHistoryRecord mapHistory(ResultSet resultSet) throws SQLException {
        Long end = resultSet.getObject("end_time", Long.class);
        return new PunishmentHistoryRecord(
                resultSet.getString("id"),
                UUID.fromString(resultSet.getString("uuid")),
                resultSet.getString("ip"),
                resultSet.getString("ip_hash"),
                PunishmentType.valueOf(resultSet.getString("type")),
                resultSet.getString("reason"),
                resultSet.getString("actor"),
                Instant.ofEpochMilli(resultSet.getLong("start_time")),
                end == null ? null : Instant.ofEpochMilli(end),
                resultSet.getString("internal_id"),
                resultSet.getString("action"),
                Instant.ofEpochMilli(resultSet.getLong("action_time"))
        );
    }

    private PunishmentHistoryRecord buildHistory(PunishmentRecord record, String action) {
        String id = record.internalId() + "-" + Instant.now().toEpochMilli();
        return new PunishmentHistoryRecord(
                id,
                record.uuid(),
                record.ip(),
                record.ipHash(),
                record.type(),
                record.reason(),
                record.actor(),
                record.startTime(),
                record.endTime(),
                record.internalId(),
                action,
                Instant.now()
        );
    }

    private void insertHistory(PunishmentHistoryRecord record) {
        String sql = """
                INSERT INTO pluginbans_punishment_history
                (id, uuid, ip, ip_hash, type, reason, actor, start_time, end_time, internal_id, action, action_time)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, record.id());
            statement.setString(2, record.uuid().toString());
            statement.setString(3, record.ip());
            statement.setString(4, record.ipHash());
            statement.setString(5, record.type().name());
            statement.setString(6, record.reason());
            statement.setString(7, record.actor());
            statement.setLong(8, record.startTime().toEpochMilli());
            if (record.endTime() == null) {
                statement.setNull(9, java.sql.Types.BIGINT);
            } else {
                statement.setLong(9, record.endTime().toEpochMilli());
            }
            statement.setString(10, record.internalId());
            statement.setString(11, record.action());
            statement.setLong(12, record.actionTime().toEpochMilli());
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Не удалось сохранить историю наказаний.", exception);
        }
    }

    @FunctionalInterface
    private interface StatementConsumer {
        void accept(PreparedStatement statement) throws SQLException;
    }
}
