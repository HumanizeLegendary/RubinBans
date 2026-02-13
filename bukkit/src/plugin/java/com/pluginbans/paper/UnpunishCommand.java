package com.pluginbans.paper;

import com.pluginbans.core.PunishmentRecord;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

import java.util.Locale;
import java.util.Optional;

public final class UnpunishCommand implements CommandExecutor {
    private final PaperPunishmentService service;
    private final MessagesConfig messages;

    public UnpunishCommand(PaperPunishmentService service, MessagesConfig messages) {
        this.service = service;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("bans.unpunish") && !sender.hasPermission("bans.fullaccess")) {
            service.messageService().send(sender, messages.permissionDenied());
            return true;
        }
        if (args.length < 1) {
            sendUsage(sender, command);
            return true;
        }
        String punishmentId = args[0].trim().toUpperCase(Locale.ROOT);
        String reason = args.length > 1 ? joinArgs(args, 1) : "Снято администратором";
        service.core().findByInternalId(punishmentId).whenComplete((optional, throwable) -> {
            if (throwable != null) {
                service.logError("Не удалось получить наказание перед снятием: " + punishmentId, throwable);
                service.runSync(() -> service.messageService().send(sender, "<red>Не удалось получить наказание из базы.</red>"));
                return;
            }
            if (optional.isEmpty()) {
                service.runSync(() -> service.messageService().send(sender, "<red>Наказание с ID " + punishmentId + " не найдено.</red>"));
                return;
            }
            PunishmentRecord record = optional.get();
            if (!record.active()) {
                service.runSync(() -> service.messageService().send(sender, "<yellow>Наказание уже снято: " + punishmentId + "</yellow>"));
                return;
            }
            service.core().removePunishment(punishmentId, sender.getName(), reason, "MANUAL_REMOVE").whenComplete((ignored, removeThrowable) -> {
                if (removeThrowable != null) {
                    service.logError("Не удалось снять наказание: " + punishmentId, removeThrowable);
                    service.runSync(() -> service.messageService().send(sender, "<red>Не удалось снять наказание.</red>"));
                    return;
                }
                service.runSync(() -> service.messageService().send(sender,
                        "<green>Наказание снято:</green> <white>" + punishmentId + "</white>"));
            });
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

    private String joinArgs(String[] args, int start) {
        StringBuilder builder = new StringBuilder();
        for (int i = start; i < args.length; i++) {
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(args[i]);
        }
        return builder.toString().trim();
    }
}
