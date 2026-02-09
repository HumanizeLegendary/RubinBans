package com.pluginbans.core;

import java.time.Instant;
import java.util.UUID;

public record PunishmentRecord(
        String id,
        UUID uuid,
        String type,
        String reason,
        long durationSeconds,
        String issuedBy,
        Instant issuedAt,
        String ip,
        boolean active,
        boolean nnr
) {
    public boolean isPermanent() {
        return durationSeconds <= 0;
    }

    public Instant expiresAt() {
        if (isPermanent()) {
            return null;
        }
        return issuedAt.plusSeconds(durationSeconds);
    }
}
