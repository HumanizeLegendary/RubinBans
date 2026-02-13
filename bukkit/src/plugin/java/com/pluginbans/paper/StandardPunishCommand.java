package com.pluginbans.paper;

import com.pluginbans.core.DurationParser;
import com.pluginbans.core.PunishmentType;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

import java.util.Optional;
import java.util.UUID;

public final class StandardPunishCommand implements CommandExecutor {
    enum Type {
        BAN("BAN", "bans.ban"),
        TEMPBAN("TEMPBAN", "bans.tempban"),
        IPBAN("IPBAN", "bans.ipban"),
        MUTE("MUTE", "bans.mute"),
        WARN("WARN", "bans.warn"),
        CHECK("CHECK", "bans.check");

        private final String typeName;
        private final String permission;

        Type(String typeName, String permission) {
            this.typeName = typeName;
            this.permission = permission;
        }
    }

    private final PaperPunishmentService service;
    private final MessagesConfig messages;
    private final Type type;

    public StandardPunishCommand(PaperPunishmentService service, MessagesConfig messages, Type type) {
        this.service = service;
        this.messages = messages;
        this.type = type;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission(type.permission) && !sender.hasPermission("bans.fullaccess")) {
            service.messageService().send(sender, messages.permissionDenied());
            return true;
        }
        if (args.length < 2) {
            service.messageService().send(sender, messages.error("usage"));
            return true;
        }
        Optional<UUID> uuid = PlayerResolver.resolveUuid(args[0]);
        if (uuid.isEmpty()) {
            service.messageService().send(sender, messages.error("player_not_found"));
            return true;
        }
        long durationSeconds = type == Type.WARN ? service.config().warnDurationSeconds() : parseDuration(args[1], sender);
        if (durationSeconds < 0) {
            return true;
        }
        if (type == Type.TEMPBAN && durationSeconds == 0L) {
            service.messageService().send(sender, "<red>Для временного бана укажите срок больше 0.</red>");
            return true;
        }
        String reason = joinArgs(args, type == Type.WARN ? 1 : 2);
        if (reason.isBlank()) {
            service.messageService().send(sender, messages.error("reason"));
            return true;
        }
        boolean silent = hasFlag(args, "-s");
        boolean nnr = hasFlag(args, "-nnr");
        UUID target = uuid.get();
        String actor = sender.getName();
        String ip = PlayerResolver.resolveIp(target).orElse(null);
        if (type == Type.IPBAN && (ip == null || ip.isBlank())) {
            service.messageService().send(sender, "<red>Для IP-бана игрок должен быть онлайн.</red>");
            return true;
        }
        service.issuePunishment(target, type.typeName, reason.trim(), durationSeconds, actor, ip, silent, nnr)
                .thenAccept(record -> {
                    if (type == Type.WARN) {
                        service.core().getActiveByUuid(target).thenAccept(active -> {
                            long warnCount = active.all().stream().filter(p -> p.type() == PunishmentType.WARN).count();
                            boolean hasActiveBan = active.all().stream().anyMatch(p ->
                                    p.type() == PunishmentType.BAN || p.type() == PunishmentType.TEMPBAN || p.type() == PunishmentType.IPBAN);
                            if (warnCount >= 3 && !hasActiveBan) {
                                service.issuePunishment(target, PunishmentType.BAN.name(), service.config().autoBanReason(), 0L, "Система", ip, false, false);
                            }
                        });
                    }
                });
        return true;
    }

    private long parseDuration(String input, CommandSender sender) {
        try {
            return DurationParser.parseToSeconds(input);
        } catch (IllegalArgumentException exception) {
            service.messageService().send(sender, messages.error("duration"));
            return -1;
        }
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
