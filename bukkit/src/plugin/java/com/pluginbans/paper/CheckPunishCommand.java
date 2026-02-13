package com.pluginbans.paper;

import com.pluginbans.core.DurationParser;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

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
        if (!sender.hasPermission("bans.check") && !sender.hasPermission("bans.fullaccess")) {
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
        long duration = service.config().checkDurationSeconds();
        String reason = "Проверка";
        if (args.length >= 2) {
            try {
                duration = DurationParser.parseToSeconds(args[1]);
                if (args.length > 2) {
                    reason = joinArgs(args, 2);
                }
            } catch (IllegalArgumentException exception) {
                reason = joinArgs(args, 1);
            }
        }
        boolean silent = hasFlag(args, "-s");
        boolean nnr = hasFlag(args, "-nnr");
        UUID target = uuid.get();
        String actor = sender.getName();
        String ip = PlayerResolver.resolveIp(target).orElse(null);
        service.issuePunishment(target, "CHECK", reason.trim(), duration, actor, ip, silent, nnr);
        return true;
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
