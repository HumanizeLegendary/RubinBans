package com.pluginbans.paper;

import com.pluginbans.core.DurationFormatter;
import com.pluginbans.core.PunishmentRecord;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class CheckPunishCommand implements CommandExecutor {
    private final PaperPunishmentService service;
    private final MessagesConfig messages;

    public CheckPunishCommand(PaperPunishmentService service, MessagesConfig messages) {
        this.service = service;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("ban.check") && !sender.hasPermission("ban.fullaccess")) {
            service.messageService().send(sender, messages.permissionDenied());
            return true;
        }
        if (args.length < 1) {
            service.messageService().send(sender, messages.error("usage"));
            return true;
        }
        Optional<UUID> uuid = PlayerResolver.resolveUuid(args[0]);
        if (uuid.isEmpty()) {
            service.messageService().send(sender, messages.error("player_not_found"));
            return true;
        }
        service.getActiveByUuid(uuid.get()).thenAccept(records -> sendList(sender, records));
        return true;
    }

    private void sendList(CommandSender sender, List<PunishmentRecord> records) {
        if (records.isEmpty()) {
            service.runSync(() -> service.messageService().send(sender, messages.error("none")));
            return;
        }
        service.runSync(() -> {
            service.messageService().send(sender, "<gray>Активные наказания:</gray>");
            for (PunishmentRecord record : records) {
                String label = record.nnr() ? "<red>[NNR]</red>" : "<green>[АКТИВНО]</green>";
                String line = "%s %s <gray>Тип:</gray> %s <gray>Длительность:</gray> %s <gray>ID:</gray> %s"
                        .formatted(label, record.reason(), record.type(), DurationFormatter.formatSeconds(record.durationSeconds()), record.id());
                service.messageService().send(sender, line);
            }
        });
    }
}
