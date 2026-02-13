package com.pluginbans.paper;

import com.pluginbans.core.DurationParser;
import com.pluginbans.core.PunishmentType;
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
        if (!sender.hasPermission("bans.punish") && !sender.hasPermission("bans.fullaccess")) {
            service.messageService().send(sender, messages.permissionDenied());
            return true;
        }
        if (args.length < 4) {
            sendUsage(sender, command);
            return true;
        }
        Optional<UUID> uuid = PlayerResolver.resolveUuid(args[0]);
        if (uuid.isEmpty()) {
            service.messageService().send(sender, messages.error("player_not_found"));
            return true;
        }
        String typeName = args[1].toUpperCase(Locale.ROOT);
        PunishmentType type;
        try {
            type = PunishmentType.valueOf(typeName);
        } catch (IllegalArgumentException exception) {
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
        if (type == PunishmentType.TEMPBAN && durationSeconds == 0L) {
            service.messageService().send(sender, "<red>Для временного бана укажите срок больше 0.</red>");
            return true;
        }
        String reason = joinArgs(args, 3);
        if (reason.isBlank()) {
            service.messageService().send(sender, messages.error("reason"));
            return true;
        }
        boolean silent = hasFlag(args, "-s");
        boolean nnr = hasFlag(args, "-nnr");
        UUID target = uuid.get();
        String actor = sender.getName();
        String ip = PlayerResolver.resolveIp(target).orElse(null);
        if (type == PunishmentType.IPBAN && (ip == null || ip.isBlank())) {
            service.messageService().send(sender, "<red>Для IP-бана игрок должен быть онлайн.</red>");
            return true;
        }
        service.issuePunishment(target, type.name(), reason.trim(), durationSeconds, actor, ip, silent, nnr);
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

    private boolean hasFlag(String[] args, String flag) {
        for (String arg : args) {
            if (arg.equalsIgnoreCase(flag)) {
                return true;
            }
        }
        return false;
    }

    private String joinArgs(String[] args, int start) {
        StringBuilder builder = new StringBuilder();
        for (int i = start; i < args.length; i++) {
            String arg = args[i];
            if (arg.equalsIgnoreCase("-s") || arg.equalsIgnoreCase("-nnr")) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(arg);
        }
        return builder.toString();
    }
}
