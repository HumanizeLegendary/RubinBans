package com.pluginbans.core;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public final class PunishmentService implements AutoCloseable {
    private final PunishmentRepository repository;
    private final ConcurrentHashMap<UUID, ActivePunishment> cache;
    private final ConcurrentHashMap<UUID, String> trackedIps;
    private final List<PunishmentListener> listeners;
    private final ScheduledExecutorService scheduler;
    private final Duration pollInterval;

    public PunishmentService(PunishmentRepository repository, Duration pollInterval) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.pollInterval = pollInterval == null ? Duration.ofSeconds(5) : pollInterval;
        this.cache = new ConcurrentHashMap<>();
        this.trackedIps = new ConcurrentHashMap<>();
        this.listeners = new CopyOnWriteArrayList<>();
        this.scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "PluginBans-Пуллинг");
            thread.setDaemon(true);
            return thread;
        });
        this.scheduler.scheduleAtFixedRate(this::poll, 5, this.pollInterval.toSeconds(), TimeUnit.SECONDS);
    }

    public void registerListener(PunishmentListener listener) {
        listeners.add(listener);
    }

    public void track(UUID uuid, String ip) {
        if (uuid != null) {
            if (ip != null && !ip.isBlank()) {
                trackedIps.put(uuid, ip);
            }
        }
    }

    public void untrack(UUID uuid) {
        if (uuid != null) {
            trackedIps.remove(uuid);
            cache.remove(uuid);
        }
    }

    public CompletableFuture<PunishmentRecord> createPunishment(PunishmentRecord record) {
        return repository.addPunishment(record).thenApply(ignored -> {
            refreshCache(record.uuid());
            notifyCreate(record);
            return record;
        });
    }

    public CompletableFuture<Void> removePunishment(String internalId, String actor, String reason, String action) {
        return repository.findByInternalId(internalId).thenCompose(optional -> {
            if (optional.isEmpty()) {
                return CompletableFuture.completedFuture(null);
            }
            PunishmentRecord record = optional.get();
            return repository.deactivate(internalId, actor, reason, action)
                    .thenRun(() -> {
                        refreshCache(record.uuid());
                        notifyRemove(record, reason);
                    });
        });
    }

    public CompletableFuture<ActivePunishment> getActiveByUuid(UUID uuid) {
        return repository.findActiveByUuid(uuid)
                .thenCompose(this::expireIfNeeded)
                .thenApply(records -> {
                    ActivePunishment active = new ActivePunishment(records);
                    cache.put(uuid, active);
                    return active;
                });
    }

    public CompletableFuture<List<PunishmentRecord>> getActiveByIp(String ip) {
        return repository.findActiveByIp(ip).thenCompose(this::expireIfNeeded);
    }

    public CompletableFuture<List<PunishmentRecord>> getActiveByIpHash(String ipHash) {
        return repository.findActiveByIpHash(ipHash).thenCompose(this::expireIfNeeded);
    }

    public Optional<ActivePunishment> cached(UUID uuid) {
        return Optional.ofNullable(cache.get(uuid));
    }

    public Map<UUID, ActivePunishment> cacheSnapshot() {
        return Map.copyOf(cache);
    }

    public CompletableFuture<List<PunishmentHistoryRecord>> history(UUID uuid) {
        return repository.findHistory(uuid);
    }

    public CompletableFuture<Optional<PunishmentRecord>> findByInternalId(String internalId) {
        return repository.findByInternalId(internalId);
    }

    public CompletableFuture<List<PunishmentRecord>> getActiveForConnection(UUID uuid, String ip) {
        CompletableFuture<List<PunishmentRecord>> byUuidFuture;
        if (uuid == null) {
            byUuidFuture = CompletableFuture.completedFuture(List.of());
        } else {
            byUuidFuture = getActiveByUuid(uuid).thenApply(ActivePunishment::all);
        }

        CompletableFuture<List<PunishmentRecord>> byIpFuture;
        if (ip == null || ip.isBlank()) {
            byIpFuture = CompletableFuture.completedFuture(List.of());
        } else {
            byIpFuture = getActiveByIp(ip)
                    .thenCombine(getActiveByIpHash(IpHashing.hash(ip)), (byIp, byHash) -> {
                        List<PunishmentRecord> merged = new ArrayList<>(byIp);
                        merged.addAll(byHash);
                        return merged;
                    });
        }

        return byUuidFuture.thenCombine(byIpFuture, (byUuid, byIp) -> {
            Map<String, PunishmentRecord> unique = new LinkedHashMap<>();
            for (PunishmentRecord record : byUuid) {
                unique.put(record.internalId(), record);
            }
            for (PunishmentRecord record : byIp) {
                unique.putIfAbsent(record.internalId(), record);
            }
            return List.copyOf(unique.values());
        });
    }

    private void poll() {
        for (Map.Entry<UUID, String> entry : trackedIps.entrySet()) {
            UUID uuid = entry.getKey();
            updateCache(uuid);
        }
    }

    private void refreshCache(UUID uuid) {
        if (uuid == null) {
            return;
        }
        repository.findActiveByUuid(uuid)
                .thenCompose(this::expireIfNeeded)
                .thenAccept(records -> cache.put(uuid, new ActivePunishment(records)));
    }

    private void updateCache(UUID uuid) {
        if (uuid == null) {
            return;
        }
        repository.findActiveByUuid(uuid)
                .thenCompose(this::expireIfNeeded)
                .thenAccept(records -> {
                    ActivePunishment previous = cache.get(uuid);
                    ActivePunishment current = new ActivePunishment(records);
                    cache.put(uuid, current);
                    if (previous != null) {
                        detectChanges(previous, current);
                    }
                });
    }

    private void detectChanges(ActivePunishment previous, ActivePunishment current) {
        java.util.Set<String> previousIds = previous.all().stream().map(PunishmentRecord::internalId).collect(java.util.stream.Collectors.toSet());
        java.util.Set<String> currentIds = current.all().stream().map(PunishmentRecord::internalId).collect(java.util.stream.Collectors.toSet());
        for (PunishmentRecord record : current.all()) {
            if (!previousIds.contains(record.internalId())) {
                notifyCreate(record);
            }
        }
        for (PunishmentRecord record : previous.all()) {
            if (!currentIds.contains(record.internalId())) {
                notifyRemove(record, "Снято системой");
            }
        }
    }

    private CompletableFuture<List<PunishmentRecord>> expireIfNeeded(List<PunishmentRecord> records) {
        Instant now = Instant.now();
        List<CompletableFuture<Void>> updates = new ArrayList<>();
        List<PunishmentRecord> active = new ArrayList<>();
        for (PunishmentRecord record : records) {
            if (record.isExpired(now)) {
                updates.add(repository.deactivate(record.internalId(), "Система", "Истек срок", "EXPIRE"));
            } else {
                active.add(record);
            }
        }
        if (updates.isEmpty()) {
            return CompletableFuture.completedFuture(active);
        }
        return CompletableFuture.allOf(updates.toArray(new CompletableFuture[0]))
                .thenApply(ignored -> active);
    }

    private void notifyCreate(PunishmentRecord record) {
        PunishmentCreateEvent event = new PunishmentCreateEvent(record);
        for (PunishmentListener listener : listeners) {
            listener.onCreate(event);
        }
    }

    private void notifyRemove(PunishmentRecord record, String reason) {
        PunishmentRemoveEvent event = new PunishmentRemoveEvent(record, reason);
        for (PunishmentListener listener : listeners) {
            listener.onRemove(event);
        }
    }

    @Override
    public void close() {
        scheduler.shutdownNow();
    }
}
