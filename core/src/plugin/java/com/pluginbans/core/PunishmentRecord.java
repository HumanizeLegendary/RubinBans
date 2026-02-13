package com.pluginbans.core;

import java.time.Instant;
import java.util.UUID;

public record PunishmentRecord(
        UUID uuid,
        String ip,
        String ipHash,
        PunishmentType type,
        String reason,
        String actor,
        Instant startTime,
        Instant endTime,
        boolean active,
        String internalId,
        boolean silent
) {
    public boolean isPermanent() {
        return endTime == null;
    }

    public boolean isExpired(Instant now) {
        if (endTime == null) {
            return false;
        }
        return endTime.isBefore(now) || endTime.equals(now);
    }

    public long durationSeconds() {
        if (endTime == null) {
            return 0L;
        }
        return Math.max(0L, endTime.getEpochSecond() - startTime.getEpochSecond());
    }
}
