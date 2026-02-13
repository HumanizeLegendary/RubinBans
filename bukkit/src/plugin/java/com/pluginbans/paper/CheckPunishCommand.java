package com.pluginbans.paper;

import com.pluginbans.core.DurationFormatter;
import com.pluginbans.core.PunishmentRecord;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public final class CheckPunishCommand implements CommandExecutor {
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    private final PaperPunishmentService service;
    private final MessagesConfig messages;

    public CheckPunishCommand(PaperPunishmentService service, MessagesConfig messages) {
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
        String punishmentId = args[0].trim().toUpperCase(Locale.ROOT);
        service.core().findByInternalId(punishmentId).whenComplete((recordOptional, throwable) -> {
            if (throwable != null) {
                service.runSync(() -> service.messageService().send(sender, "<red>Не удалось получить наказание из базы.</red>"));
                return;
            }
            if (recordOptional.isEmpty()) {
                service.runSync(() -> service.messageService().send(sender, "<red>Наказание с ID " + punishmentId + " не найдено.</red>"));
                return;
            }
            PunishmentRecord record = recordOptional.get();
            service.runSync(() -> sendDetails(sender, record));
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

    private void sendDetails(CommandSender sender, PunishmentRecord record) {
        OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(record.uuid());
        String playerName = offlinePlayer.getName() == null ? record.uuid().toString() : offlinePlayer.getName();
        String endAt = record.endTime() == null ? "Навсегда" : DATE_FORMATTER.format(record.endTime());
        String remaining = record.endTime() == null || !record.active()
                ? "Нет"
                : DurationFormatter.formatSeconds(Math.max(0L, record.endTime().getEpochSecond() - Instant.now().getEpochSecond()));

        service.messageService().send(sender, "<gray>----------</gray> <aqua>Наказание</aqua> <gray>----------</gray>");
        service.messageService().send(sender, "<gray>ID:</gray> <white>" + record.internalId() + "</white>");
        service.messageService().send(sender, "<gray>Игрок:</gray> <white>" + playerName + "</white>");
        service.messageService().send(sender, "<gray>Тип:</gray> <white>" + record.type().name() + "</white>");
        service.messageService().send(sender, "<gray>Статус:</gray> <white>" + (record.active() ? "Активно" : "Неактивно") + "</white>");
        service.messageService().send(sender, "<gray>Причина:</gray> <white>" + record.reason() + "</white>");
        service.messageService().send(sender, "<gray>Выдал:</gray> <white>" + record.actor() + "</white>");
        service.messageService().send(sender, "<gray>Выдано:</gray> <white>" + DATE_FORMATTER.format(record.startTime()) + "</white>");
        service.messageService().send(sender, "<gray>Окончание:</gray> <white>" + endAt + "</white>");
        service.messageService().send(sender, "<gray>Осталось:</gray> <white>" + remaining + "</white>");
        if (record.active()) {
            String command = "/unpunish " + record.internalId() + " Снято_через_checkpunish";
            String button = "<click:run_command:'" + command + "'>"
                    + "<hover:show_text:'<green>Нажмите, чтобы снять наказание</green>'>"
                    + "<green><bold>[ РАЗБАНИТЬ ]</bold></green>"
                    + "</hover></click>";
            service.messageService().send(sender, button);
        }
        service.messageService().send(sender, "<gray>--------------------------------</gray>");
    }
}
