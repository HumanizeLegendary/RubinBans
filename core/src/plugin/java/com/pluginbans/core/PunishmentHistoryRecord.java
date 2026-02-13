package com.pluginbans.core;

import java.time.Instant;
import java.util.UUID;

public record PunishmentHistoryRecord(
        String id,
        UUID uuid,
        String ip,
        String ipHash,
        PunishmentType type,
        String reason,
        String actor,
        Instant startTime,
        Instant endTime,
        String internalId,
        String action,
        Instant actionTime
) {
}
