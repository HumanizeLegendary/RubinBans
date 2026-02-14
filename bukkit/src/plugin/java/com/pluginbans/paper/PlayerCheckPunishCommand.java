package com.pluginbans.paper;

import com.pluginbans.core.ActivePunishment;
import com.pluginbans.core.PunishmentHistoryRecord;
import com.pluginbans.core.PunishmentRecord;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class PlayerCheckPunishCommand implements CommandExecutor {
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    private final PaperPunishmentService service;
    private final MessagesConfig messages;

    public PlayerCheckPunishCommand(PaperPunishmentService service, MessagesConfig messages) {
        this.service = service;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("bans.check") && !sender.hasPermission("bans.fullaccess")) {
            service.messageService().send(sender, messages.permissionDenied());
            return true;
        }
        if (args.length != 1) {
            sendUsage(sender, command);
            return true;
        }

        Optional<UUID> uuid = PlayerResolver.resolveUuid(args[0]);
        if (uuid.isEmpty()) {
            service.messageService().send(sender, messages.error("player_not_found"));
            return true;
        }

        UUID targetUuid = uuid.get();
        service.core().getActiveByUuid(targetUuid)
                .thenCombine(service.core().history(targetUuid), (active, history) -> new PlayerPunishments(active, history))
                .whenComplete((result, throwable) -> {
                    if (throwable != null) {
                        service.runSync(() -> service.messageService().send(sender, "<red>Не удалось получить наказания игрока из базы.</red>"));
                        return;
                    }
                    service.runSync(() -> sendDetails(sender, targetUuid, result));
                });
        return true;
    }

    private void sendUsage(CommandSender sender, Command command) {
        String usage = command.getUsage();
        if (usage == null || usage.isBlank()) {
            service.messageService().send(sender, messages.error("usage"));
            return;
        }
        service.messageService().send(sender, "<red>Использование:</red> <white>" + usage + "</white>");
    }

    private void sendDetails(CommandSender sender, UUID uuid, PlayerPunishments punishments) {
        OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(uuid);
        String playerName = offlinePlayer.getName() == null ? uuid.toString() : offlinePlayer.getName();

        List<PunishmentRecord> active = new ArrayList<>(punishments.active().all());
        active.sort(Comparator.comparing(PunishmentRecord::startTime).reversed());

        List<PunishmentHistoryRecord> history = new ArrayList<>(punishments.history());
        history.sort(Comparator.comparing(PunishmentHistoryRecord::actionTime).reversed());

        service.messageService().send(sender, "<gray>----------</gray> <aqua>Наказания игрока</aqua> <gray>----------</gray>");
        service.messageService().send(sender, "<gray>Игрок:</gray> <white>" + playerName + "</white> <dark_gray>|</dark_gray> <gray>UUID:</gray> <white>" + uuid + "</white>");
        service.messageService().send(sender, "<gray>Активных:</gray> <white>" + active.size() + "</white> <dark_gray>|</dark_gray> <gray>Всего в истории:</gray> <white>" + history.size() + "</white>");

        service.messageService().send(sender, "<yellow>Активные наказания:</yellow>");
        if (active.isEmpty()) {
            service.messageService().send(sender, "<gray>• Нет активных наказаний.</gray>");
        } else {
            for (PunishmentRecord record : active) {
                String endAt = record.endTime() == null ? "Навсегда" : DATE_FORMATTER.format(record.endTime());
                service.messageService().send(
                        sender,
                        "<gray>•</gray> <white>" + record.type().name() + "</white>"
                                + " <dark_gray>|</dark_gray> <gray>ID:</gray> <yellow>" + record.internalId() + "</yellow>"
                                + " <dark_gray>|</dark_gray> <gray>До:</gray> <white>" + endAt + "</white>"
                                + " <dark_gray>|</dark_gray> <gray>Причина:</gray> <white>" + record.reason() + "</white>"
                );
            }
        }

        service.messageService().send(sender, "<yellow>История наказаний:</yellow>");
        if (history.isEmpty()) {
            service.messageService().send(sender, "<gray>• История пустая.</gray>");
        } else {
            for (PunishmentHistoryRecord record : history) {
                String actionTime = DATE_FORMATTER.format(record.actionTime());
                service.messageService().send(
                        sender,
                        "<gray>•</gray> <white>" + record.type().name() + "</white>"
                                + " <dark_gray>[" + record.action() + "]</dark_gray>"
                                + " <dark_gray>|</dark_gray> <gray>ID:</gray> <yellow>" + record.internalId() + "</yellow>"
                                + " <dark_gray>|</dark_gray> <gray>Кто:</gray> <white>" + record.actor() + "</white>"
                                + " <dark_gray>|</dark_gray> <gray>Когда:</gray> <white>" + actionTime + "</white>"
                                + " <dark_gray>|</dark_gray> <gray>Причина:</gray> <white>" + record.reason() + "</white>"
                );
            }
        }
        service.messageService().send(sender, "<gray>------------------------------------------</gray>");
    }

    private record PlayerPunishments(ActivePunishment active, List<PunishmentHistoryRecord> history) {
    }
}
