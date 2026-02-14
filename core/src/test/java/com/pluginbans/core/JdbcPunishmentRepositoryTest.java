package com.pluginbans.core;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class JdbcPunishmentRepositoryTest {
    private Path tempDir;
    private DatabaseManager databaseManager;
    private JdbcPunishmentRepository repository;

    @Before
    public void setUp() throws IOException {
        tempDir = java.nio.file.Files.createTempDirectory("pluginbans-core-test-");
        Path sqlitePath = tempDir.resolve("pluginbans-test.db");
        DatabaseConfig config = new DatabaseConfig(
                DatabaseType.SQLITE,
                "localhost",
                3306,
                "pluginbans",
                "root",
                "",
                sqlitePath.toString(),
                4
        );
        this.databaseManager = new DatabaseManager(config);
        this.repository = new JdbcPunishmentRepository(databaseManager.dataSource(), databaseManager.executor());
    }

    @After
    public void tearDown() throws IOException {
        if (databaseManager != null) {
            databaseManager.close();
        }
        if (tempDir != null && java.nio.file.Files.exists(tempDir)) {
            try (java.util.stream.Stream<Path> paths = java.nio.file.Files.walk(tempDir)) {
                paths.sorted(java.util.Comparator.reverseOrder())
                        .forEach(path -> {
                            try {
                                java.nio.file.Files.deleteIfExists(path);
                            } catch (IOException ignored) {
                            }
                        });
            }
        }
    }

    @Test
    public void persistsActiveBanAndMuteInDatabase() {
        UUID uuid = UUID.randomUUID();
        Instant start = Instant.parse("2026-02-14T10:15:30Z");

        PunishmentRecord ban = buildPunishment(uuid, PunishmentType.BAN, "BAN001", "Читы", "Console", start, 0L);
        PunishmentRecord mute = buildPunishment(uuid, PunishmentType.MUTE, "MUTE01", "Флуд", "Moderator", start.plusSeconds(10), 1800L);

        repository.addPunishment(ban).join();
        repository.addPunishment(mute).join();

        List<PunishmentRecord> active = repository.findActiveByUuid(uuid).join();
        assertEquals("Ожидались активные BAN и MUTE в базе.", 2, active.size());

        Set<PunishmentType> types = active.stream().map(PunishmentRecord::type).collect(Collectors.toSet());
        assertTrue("BAN должен читаться из БД.", types.contains(PunishmentType.BAN));
        assertTrue("MUTE должен читаться из БД.", types.contains(PunishmentType.MUTE));

        PunishmentRecord storedMute = repository.findByInternalId("MUTE01").join().orElseThrow();
        assertEquals("Флуд", storedMute.reason());

        List<PunishmentHistoryRecord> history = repository.findHistory(uuid).join();
        assertEquals("Для двух выдач должны быть две записи CREATE в истории.", 2, history.size());
        assertTrue(history.stream().allMatch(record -> "CREATE".equals(record.action())));
    }

    @Test
    public void deactivatingMuteKeepsBanActiveAndWritesHistory() throws InterruptedException {
        UUID uuid = UUID.randomUUID();
        Instant start = Instant.parse("2026-02-14T11:00:00Z");

        PunishmentRecord ban = buildPunishment(uuid, PunishmentType.BAN, "BAN002", "Гриферство", "Console", start, 0L);
        PunishmentRecord mute = buildPunishment(uuid, PunishmentType.MUTE, "MUTE02", "Оскорбления", "Moderator", start.plusSeconds(30), 3600L);

        repository.addPunishment(ban).join();
        repository.addPunishment(mute).join();

        // История формирует id по миллисекундам; короткая пауза делает тест детерминированным.
        Thread.sleep(2L);
        repository.deactivate("MUTE02", "Admin", "Снято модератором", "MANUAL_REMOVE").join();

        List<PunishmentRecord> active = repository.findActiveByUuid(uuid).join();
        assertEquals("После снятия мута активным должен остаться только BAN.", 1, active.size());
        assertEquals(PunishmentType.BAN, active.get(0).type());

        PunishmentRecord storedMute = repository.findByInternalId("MUTE02").join().orElseThrow();
        assertFalse("MUTE должен стать неактивным после снятия.", storedMute.active());

        List<PunishmentHistoryRecord> history = repository.findHistory(uuid).join();
        assertEquals("Ожидались CREATE(BAN), CREATE(MUTE), MANUAL_REMOVE(MUTE).", 3, history.size());
        assertTrue("MANUAL_REMOVE для мута должен сохраняться в историю.", history.stream().anyMatch(record ->
                "MANUAL_REMOVE".equals(record.action())
                        && "MUTE02".equals(record.internalId())
                        && "Снято модератором".equals(record.reason())
        ));
    }

    private PunishmentRecord buildPunishment(
            UUID uuid,
            PunishmentType type,
            String internalId,
            String reason,
            String actor,
            Instant start,
            long durationSeconds
    ) {
        String ip = "203.0.113.10";
        Instant end = durationSeconds <= 0L ? null : start.plusSeconds(durationSeconds);
        return new PunishmentRecord(
                uuid,
                ip,
                IpHashing.hash(ip),
                type,
                reason,
                actor,
                start,
                end,
                true,
                internalId,
                false
        );
    }
}
