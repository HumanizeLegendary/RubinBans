package com.pluginbans.paper;

import com.pluginbans.core.DiscordBridge;
import com.pluginbans.core.DurationFormatter;
import com.pluginbans.core.PunishmentIdGenerator;
import com.pluginbans.core.PunishmentRecord;
import com.pluginbans.core.PunishmentRepository;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class PaperPunishmentService {
    private final Plugin plugin;
    private final PunishmentRepository repository;
    private final PaperConfig config;
    private final MessagesConfig messages;
    private final DiscordBridge discordBridge;
    private final VelocityMessenger velocityMessenger;
    private final MessageService messageService;

    public PaperPunishmentService(
            Plugin plugin,
            PunishmentRepository repository,
            PaperConfig config,
            MessagesConfig messages,
            DiscordBridge discordBridge,
            VelocityMessenger velocityMessenger
    ) {
        this.plugin = plugin;
        this.repository = repository;
        this.config = config;
        this.messages = messages;
        this.discordBridge = discordBridge;
        this.velocityMessenger = velocityMessenger;
        this.messageService = new MessageService(messages);
    }

    public CompletableFuture<PunishmentRecord> issuePunishment(UUID uuid, String type, String reason, long durationSeconds, String issuedBy, String ip) {
        boolean nnr = config.nnrTypes().contains(type.toUpperCase(Locale.ROOT));
        String typeCode = nnr ? "NNR" : (durationSeconds > 0 ? "TM" : "NV");
        String id = PunishmentIdGenerator.generate(typeCode);
        PunishmentRecord record = new PunishmentRecord(
                id,
                uuid,
                type.toUpperCase(Locale.ROOT),
                reason,
                durationSeconds,
                issuedBy,
                Instant.now(),
                ip,
                true,
                nnr
        );
        return repository.addPunishment(record)
                .thenApply(ignored -> record)
                .thenApply(saved -> {
                    discordBridge.buildPayload(saved);
                    velocityMessenger.sendPunishment(saved);
                    return saved;
                });
    }

    public CompletableFuture<List<PunishmentRecord>> getActiveByUuid(UUID uuid) {
        return repository.findActiveByUuid(uuid).thenCompose(this::filterExpired);
    }

    public CompletableFuture<List<PunishmentRecord>> getActiveByIp(String ip) {
        return repository.findActiveByIp(ip).thenCompose(this::filterExpired);
    }

    public CompletableFuture<Optional<PunishmentRecord>> findById(String id) {
        return repository.findById(id);
    }

    public CompletableFuture<Long> countActiveWarns(UUID uuid) {
        return getActiveByUuid(uuid).thenApply(records -> records.stream()
                .filter(record -> record.type().equalsIgnoreCase("WARN"))
                .count());
    }

    public PaperConfig config() {
        return config;
    }

    public MessageService messageService() {
        return messageService;
    }

    public MessagesConfig messages() {
        return messages;
    }

    public void runSync(Runnable runnable) {
        Bukkit.getScheduler().runTask(plugin, runnable);
    }

    public void kickIfOnline(UUID uuid, String reason, long durationSeconds, String id, boolean nnr) {
        Player player = Bukkit.getPlayer(uuid);
        if (player == null) {
            return;
        }
        String finalReason = nnr ? config.nnrHiddenReason() : reason;
        String message = messageService.applyPlaceholders(messages.banScreen(), Map.of(
                "%reason%", finalReason,
                "%time%", DurationFormatter.formatSeconds(durationSeconds),
                "%id%", id
        ));
        runSync(() -> player.kick(messageService.format(message)));
    }

    public void sendMuteMessage(UUID uuid, String reason, long durationSeconds, String id, boolean nnr) {
        Player player = Bukkit.getPlayer(uuid);
        if (player == null) {
            return;
        }
        String finalReason = nnr ? config.nnrHiddenReason() : reason;
        String message = messageService.applyPlaceholders(messages.muteMessage(), Map.of(
                "%reason%", finalReason,
                "%time%", DurationFormatter.formatSeconds(durationSeconds),
                "%id%", id
        ));
        runSync(() -> player.sendMessage(messageService.format(message)));
    }

    public void sendWarnMessage(UUID uuid, String reason, long durationSeconds, String id, boolean nnr) {
        Player player = Bukkit.getPlayer(uuid);
        if (player == null) {
            return;
        }
        String finalReason = nnr ? config.nnrHiddenReason() : reason;
        String message = messageService.applyPlaceholders(messages.warnMessage(), Map.of(
                "%reason%", finalReason,
                "%time%", DurationFormatter.formatSeconds(durationSeconds),
                "%id%", id
        ));
        runSync(() -> player.sendMessage(messageService.format(message)));
    }

    public CompletableFuture<List<PunishmentRecord>> getActiveBanLike(UUID uuid, String ip) {
        CompletableFuture<List<PunishmentRecord>> byUuid = getActiveByUuid(uuid);
        CompletableFuture<List<PunishmentRecord>> byIp = ip == null ? CompletableFuture.completedFuture(List.of()) : getActiveByIp(ip);
        return byUuid.thenCombine(byIp, (uuidList, ipList) -> {
            List<PunishmentRecord> combined = new ArrayList<>(uuidList);
            combined.addAll(ipList);
            return combined.stream()
                    .filter(record -> switch (record.type()) {
                        case "BAN", "IPBAN", "IDBAN" -> true;
                        default -> false;
                    })
                    .toList();
        });
    }

    private CompletableFuture<List<PunishmentRecord>> filterExpired(List<PunishmentRecord> records) {
        Instant now = Instant.now();
        List<CompletableFuture<Void>> updates = new ArrayList<>();
        List<PunishmentRecord> active = new ArrayList<>();
        for (PunishmentRecord record : records) {
            if (record.isPermanent()) {
                active.add(record);
                continue;
            }
            if (record.expiresAt().isAfter(now)) {
                active.add(record);
            } else {
                updates.add(repository.deactivate(record.id()));
            }
        }
        if (updates.isEmpty()) {
            return CompletableFuture.completedFuture(active);
        }
        return CompletableFuture.allOf(updates.toArray(new CompletableFuture[0]))
                .thenApply(ignored -> active);
    }
}
