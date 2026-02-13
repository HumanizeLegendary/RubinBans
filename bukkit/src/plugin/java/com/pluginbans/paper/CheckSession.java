package com.pluginbans.paper;

import org.bukkit.boss.BossBar;

import java.time.Instant;
import java.util.UUID;

public final class CheckSession {
    private final UUID uuid;
    private final Instant endTime;
    private final BossBar bossBar;
    private final long totalSeconds;
    private boolean paused;

    public CheckSession(UUID uuid, Instant endTime, BossBar bossBar) {
        this.uuid = uuid;
        this.endTime = endTime;
        this.bossBar = bossBar;
        this.totalSeconds = endTime == null ? 0L : Math.max(1L, endTime.getEpochSecond() - Instant.now().getEpochSecond());
    }

    public UUID uuid() {
        return uuid;
    }

    public Instant endTime() {
        return endTime;
    }

    public BossBar bossBar() {
        return bossBar;
    }

    public long totalSeconds() {
        return totalSeconds;
    }

    public boolean isPaused() {
        return paused;
    }

    public void setPaused(boolean paused) {
        this.paused = paused;
    }
}
