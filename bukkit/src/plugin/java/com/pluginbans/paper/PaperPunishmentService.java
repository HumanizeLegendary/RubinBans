package com.pluginbans.paper;

import com.pluginbans.core.AuditLogger;
import com.pluginbans.core.DurationFormatter;
import com.pluginbans.core.IpHashing;
import com.pluginbans.core.PunishmentCreateEvent;
import com.pluginbans.core.PunishmentIdGenerator;
import com.pluginbans.core.PunishmentListener;
import com.pluginbans.core.PunishmentRecord;
import com.pluginbans.core.PunishmentRules;
import com.pluginbans.core.PunishmentService;
import com.pluginbans.core.PunishmentType;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

public final class PaperPunishmentService implements PunishmentListener {
    private final Plugin plugin;
    private final PunishmentService punishmentService;
    private final PaperConfig config;
    private final MessagesConfig messages;
    private final MessageService messageService;
    private final AuditLogger auditLogger;
    private CheckManager checkManager;

    public PaperPunishmentService(
            Plugin plugin,
            PunishmentService punishmentService,
            PaperConfig config,
            MessagesConfig messages,
            AuditLogger auditLogger,
            CheckManager checkManager
    ) {
        this.plugin = plugin;
        this.punishmentService = punishmentService;
        this.config = config;
        this.messages = messages;
        this.messageService = new MessageService(messages);
        this.auditLogger = auditLogger;
        this.checkManager = checkManager;
    }

    public void setCheckManager(CheckManager checkManager) {
        this.checkManager = checkManager;
    }

    public CompletableFuture<PunishmentRecord> issuePunishment(UUID uuid, String typeName, String reason, long durationSeconds, String actor, String ip, boolean silent, boolean nnr) {
        PunishmentType type = PunishmentType.valueOf(typeName.toUpperCase(Locale.ROOT));
        if (type == PunishmentType.BAN && durationSeconds > 0) {
            type = PunishmentType.TEMPBAN;
        }
        Instant start = Instant.now();
        Instant end = durationSeconds > 0 ? start.plusSeconds(durationSeconds) : null;
        String idCode = nnr ? "NNR" : (end == null ? "NV" : "TM");
        PunishmentRecord record = new PunishmentRecord(
                uuid,
                ip,
                IpHashing.hash(ip),
                type,
                reason,
                actor,
                start,
                end,
                true,
                PunishmentIdGenerator.generate(idCode),
                silent
        );
        auditLogger.log("Наказание: %s -> %s (%s), причина: %s, длительность: %s, скрыто: %s".formatted(
                actor,
                uuid,
                type.name(),
                reason,
                DurationFormatter.formatSeconds(durationSeconds),
                silent ? "да" : "нет"
        ));
        return punishmentService.createPunishment(record);
    }

    public PunishmentService core() {
        return punishmentService;
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

    public void logError(String message, Throwable throwable) {
        plugin.getLogger().log(Level.SEVERE, message, throwable);
    }

    @Override
    public void onCreate(PunishmentCreateEvent event) {
        PunishmentRecord record = event.record();
        if (checkManager == null) {
            return;
        }
        if (record.type() == PunishmentType.CHECK) {
            checkManager.startCheck(record.uuid(), record.endTime());
            broadcast(record, messages.checkMessage());
            return;
        }
        if (record.type() == PunishmentType.MUTE) {
            sendPunished(record, messages.muteMessage());
            broadcast(record, messages.muteMessage());
            return;
        }
        if (record.type() == PunishmentType.WARN) {
            kickIfOnline(record);
            broadcast(record, messages.warnMessage());
            return;
        }
        if (PunishmentRules.isBanLike(record.type())) {
            kickIfOnline(record);
            broadcast(record, messages.banMessage());
        }
    }

    private void broadcast(PunishmentRecord record, String template) {
        if (record.silent()) {
            return;
        }
        runSync(() -> {
            OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(record.uuid());
            String playerName = java.util.Optional.ofNullable(offlinePlayer.getName()).orElse(record.uuid().toString());
            String time = DurationFormatter.formatSeconds(record.durationSeconds());
            String rendered = messageService.applyPlaceholders(template, Map.of(
                    "%player%", playerName,
                    "%reason%", record.reason(),
                    "%time%", time,
                    "%actor%", record.actor(),
                    "%id%", record.internalId()
            ));
            String message = ensureIdInMessage(template, rendered, record.internalId());
            Bukkit.getOnlinePlayers().stream()
                    .filter(this::canReceiveNotifications)
                    .forEach(player -> player.sendMessage(messageService.format(message)));
        });
    }

    private void sendPunished(PunishmentRecord record, String template) {
        runSync(() -> {
            Player player = Bukkit.getPlayer(record.uuid());
            if (player == null) {
                return;
            }
            String playerName = player.getName();
            String time = DurationFormatter.formatSeconds(record.durationSeconds());
            String rendered = messageService.applyPlaceholders(template, Map.of(
                    "%player%", playerName,
                    "%reason%", record.reason(),
                    "%time%", time,
                    "%actor%", record.actor(),
                    "%id%", record.internalId()
            ));
            String message = ensureIdInMessage(template, rendered, record.internalId());
            player.sendMessage(messageService.format(message));
        });
    }

    private void kickIfOnline(PunishmentRecord record) {
        runSync(() -> {
            Player player = Bukkit.getPlayer(record.uuid());
            if (player == null) {
                return;
            }
            String time = DurationFormatter.formatSeconds(record.durationSeconds());
            String rendered = messageService.applyPlaceholders(messages.kickMessage(), Map.of(
                    "%reason%", record.reason(),
                    "%time%", time,
                    "%actor%", record.actor(),
                    "%id%", record.internalId()
            ));
            String message = ensureIdInMessage(messages.kickMessage(), rendered, record.internalId());
            player.kick(messageService.formatRaw(message));
        });
    }

    private String ensureIdInMessage(String template, String rendered, String id) {
        if (template != null && template.contains("%id%")) {
            return rendered;
        }
        return rendered + "\n<gray>ID наказания:</gray> <white>" + id + "</white>";
    }

    private boolean canReceiveNotifications(Player player) {
        return player.hasPermission("bans.fullaccess")
                || player.hasPermission("bans.ban")
                || player.hasPermission("bans.tempban")
                || player.hasPermission("bans.ipban")
                || player.hasPermission("bans.warn")
                || player.hasPermission("bans.check")
                || player.hasPermission("bans.punish");
    }
}
