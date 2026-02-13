package com.pluginbans.core;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface PunishmentRepository {
    CompletableFuture<Void> addPunishment(PunishmentRecord record);

    CompletableFuture<Void> deactivate(String internalId, String actor, String reason, String action);

    CompletableFuture<List<PunishmentRecord>> findActiveByUuid(UUID uuid);

    CompletableFuture<List<PunishmentRecord>> findActiveByIp(String ip);

    CompletableFuture<List<PunishmentRecord>> findActiveByIpHash(String ipHash);

    CompletableFuture<Optional<PunishmentRecord>> findByInternalId(String internalId);

    CompletableFuture<List<PunishmentHistoryRecord>> findHistory(UUID uuid);
}
