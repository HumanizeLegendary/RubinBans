package com.pluginbans.core;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface PunishmentRepository {
    CompletableFuture<Void> addPunishment(PunishmentRecord record);

    CompletableFuture<List<PunishmentRecord>> findActiveByUuid(UUID uuid);

    CompletableFuture<List<PunishmentRecord>> findActiveByIp(String ip);

    CompletableFuture<Optional<PunishmentRecord>> findById(String id);

    CompletableFuture<Long> countActiveWarns(UUID uuid);

    CompletableFuture<Void> deactivate(String id);
}
