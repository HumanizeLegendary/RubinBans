package com.pluginbans.paper;

import com.pluginbans.core.DurationParser;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

public final class CustomPunishCommand implements CommandExecutor {
    private final PaperPunishmentService service;
    private final MessagesConfig messages;

    public CustomPunishCommand(PaperPunishmentService service, MessagesConfig messages) {
        this.service = service;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("ban.punish") && !sender.hasPermission("ban.fullaccess")) {
            service.messageService().send(sender, messages.permissionDenied());
            return true;
        }
        if (args.length < 4) {
            service.messageService().send(sender, messages.error("usage"));
            return true;
        }
        Optional<UUID> uuid = PlayerResolver.resolveUuid(args[0]);
        if (uuid.isEmpty()) {
            service.messageService().send(sender, messages.error("player_not_found"));
            return true;
        }
        String type = args[1].toUpperCase(Locale.ROOT);
        if (!service.config().punishTypes().contains(type)) {
            service.messageService().send(sender, messages.error("type"));
            return true;
        }
        long durationSeconds;
        try {
            durationSeconds = DurationParser.parseToSeconds(args[2]);
        } catch (IllegalArgumentException exception) {
            service.messageService().send(sender, messages.error("duration"));
            return true;
        }
        String reason = joinArgs(args, 3);
        if (reason.isBlank()) {
            service.messageService().send(sender, messages.error("reason"));
            return true;
        }
        UUID target = uuid.get();
        String issuedBy = sender.getName();
        String ip = PlayerResolver.resolveIp(target).orElse(null);
        service.issuePunishment(target, type, reason, durationSeconds, issuedBy, ip);
        return true;
    }

    private String joinArgs(String[] args, int start) {
        StringBuilder builder = new StringBuilder();
        for (int i = start; i < args.length; i++) {
            if (i > start) {
                builder.append(' ');
            }
            builder.append(args[i]);
        }
        return builder.toString();
    }
}
