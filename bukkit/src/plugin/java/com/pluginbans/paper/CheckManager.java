package com.pluginbans.paper;

import com.pluginbans.core.DurationFormatter;
import com.pluginbans.core.PunishmentType;
import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class CheckManager {
    private final PaperPunishmentService service;
    private final Plugin plugin;
    private final Map<UUID, CheckSession> sessions = new ConcurrentHashMap<>();

    public CheckManager(Plugin plugin, PaperPunishmentService service) {
        this.plugin = plugin;
        this.service = service;
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    public void startCheck(UUID uuid, Instant endTime) {
        Player player = Bukkit.getPlayer(uuid);
        if (player == null) {
            return;
        }
        BossBar bossBar = Bukkit.createBossBar("Проверка", BarColor.RED, BarStyle.SOLID);
        bossBar.addPlayer(player);
        CheckSession session = new CheckSession(uuid, endTime, bossBar);
        sessions.put(uuid, session);
        updateBossBar(session);
    }

    public void stopCheck(UUID uuid) {
        CheckSession session = sessions.remove(uuid);
        if (session != null) {
            session.bossBar().removeAll();
        }
    }

    public boolean isInCheck(UUID uuid) {
        return sessions.containsKey(uuid);
    }

    public void pause(UUID uuid) {
        CheckSession session = sessions.get(uuid);
        if (session != null) {
            session.setPaused(true);
            updateBossBar(session);
        }
    }

    public void resume(UUID uuid) {
        CheckSession session = sessions.get(uuid);
        if (session != null) {
            session.setPaused(false);
        }
    }

    private void tick() {
        Instant now = Instant.now();
        for (CheckSession session : sessions.values()) {
            if (session.isPaused()) {
                continue;
            }
            if (session.endTime() != null && !now.isBefore(session.endTime())) {
                handleTimeout(session);
                continue;
            }
            updateBossBar(session);
        }
    }

    private void updateBossBar(CheckSession session) {
        String title;
        double progress = 1.0;
        if (session.endTime() == null) {
            title = "Проверка: без ограничений";
        } else {
            long remaining = Math.max(0L, session.endTime().getEpochSecond() - Instant.now().getEpochSecond());
            long total = Math.max(1L, session.totalSeconds());
            progress = Math.max(0.0, Math.min(1.0, (double) remaining / (double) total));
            String time = DurationFormatter.formatSeconds(remaining);
            title = "Проверка: %s".formatted(time);
        }
        if (session.isPaused()) {
            title = title + " (пауза)";
        }
        session.bossBar().setTitle(title);
        session.bossBar().setProgress(progress);
    }

    private void handleTimeout(CheckSession session) {
        stopCheck(session.uuid());
        service.issuePunishment(
                session.uuid(),
                PunishmentType.BAN.name(),
                service.config().checkTimeoutBanReason(),
                service.config().checkTimeoutBanSeconds(),
                "Система",
                PlayerResolver.resolveIp(session.uuid()).orElse(null),
                false,
                false
        );
    }
}
