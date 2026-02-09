package com.pluginbans.velocity;

import com.pluginbans.core.DurationFormatter;
import com.pluginbans.core.PunishmentRecord;
import com.pluginbans.core.PunishmentRepository;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class VelocityPunishmentService {
    private final PunishmentRepository repository;
    private final String nnrHiddenReason;

    public VelocityPunishmentService(PunishmentRepository repository, String nnrHiddenReason) {
        this.repository = repository;
        this.nnrHiddenReason = nnrHiddenReason;
    }

    public Optional<Component> buildDenial(UUID uuid, String ip, VelocityMessages messages, MiniMessage miniMessage) {
        List<PunishmentRecord> punishments = getActive(uuid, ip);
        if (punishments.isEmpty()) {
            return Optional.empty();
        }
        PunishmentRecord punishment = punishments.get(0);
        String reason = punishment.nnr() ? nnrHiddenReason : punishment.reason();
        String message = applyPlaceholders(messages.banScreen(), Map.of(
                "%reason%", reason,
                "%time%", DurationFormatter.formatSeconds(punishment.durationSeconds()),
                "%id%", punishment.id()
        ));
        return Optional.of(miniMessage.deserialize(messages.prefix() + message));
    }

    private List<PunishmentRecord> getActive(UUID uuid, String ip) {
        List<PunishmentRecord> byUuid = repository.findActiveByUuid(uuid).join();
        List<PunishmentRecord> byIp = repository.findActiveByIp(ip).join();
        List<PunishmentRecord> combined = new ArrayList<>(byUuid);
        combined.addAll(byIp);
        return filterExpired(combined).stream()
                .filter(record -> switch (record.type()) {
                    case "BAN", "IPBAN", "IDBAN" -> true;
                    default -> false;
                })
                .toList();
    }

    private List<PunishmentRecord> filterExpired(List<PunishmentRecord> records) {
        Instant now = Instant.now();
        List<PunishmentRecord> active = new ArrayList<>();
        for (PunishmentRecord record : records) {
            if (record.isPermanent()) {
                active.add(record);
                continue;
            }
            if (record.expiresAt().isAfter(now)) {
                active.add(record);
            } else {
                repository.deactivate(record.id());
            }
        }
        return active;
    }

    private String applyPlaceholders(String template, Map<String, String> placeholders) {
        String result = template;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            result = result.replace(entry.getKey(), entry.getValue());
        }
        return result;
    }
}
