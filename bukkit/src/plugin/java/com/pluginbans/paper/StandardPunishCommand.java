package com.pluginbans.paper;

import com.pluginbans.core.DurationParser;
import com.pluginbans.core.PunishmentRecord;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

import java.util.Optional;
import java.util.UUID;

public final class StandardPunishCommand implements CommandExecutor {
    enum Type {
        BAN("BAN", "ban.ban"),
        IPBAN("IPBAN", "ban.ipban"),
        MUTE("MUTE", "ban.mute"),
        WARN("WARN", "ban.warn"),
        IDBAN("IDBAN", "ban.idban");

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
        if (!sender.hasPermission(type.permission) && !sender.hasPermission("ban.fullaccess")) {
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
        long durationSeconds;
        String reasonStart;
        if (type == Type.WARN) {
            durationSeconds = service.config().warnDurationSeconds();
            reasonStart = joinArgs(args, 1);
        } else {
            if (args.length < 3) {
                service.messageService().send(sender, messages.error("usage"));
                return true;
            }
            try {
                durationSeconds = DurationParser.parseToSeconds(args[1]);
            } catch (IllegalArgumentException exception) {
                service.messageService().send(sender, messages.error("duration"));
                return true;
            }
            reasonStart = joinArgs(args, 2);
        }
        String reason = reasonStart.trim();
        if (reason.isEmpty()) {
            service.messageService().send(sender, messages.error("reason"));
            return true;
        }
        UUID target = uuid.get();
        service.getActiveByUuid(target).thenAccept(records -> {
            boolean already = records.stream().anyMatch(record -> record.type().equalsIgnoreCase(type.typeName));
            if (already) {
                service.runSync(() -> service.messageService().send(sender, messages.alreadyPunished()));
                return;
            }
            String issuedBy = sender.getName();
            String ip = PlayerResolver.resolveIp(target).orElse(null);
            service.issuePunishment(target, type.typeName, reason, durationSeconds, issuedBy, ip)
                    .thenAccept(record -> {
                        handlePostIssue(record, type);
                        if (type == Type.WARN) {
                            service.countActiveWarns(target).thenAccept(count -> {
                                if (count >= 3 && ip != null) {
                                    service.issuePunishment(target, "IPBAN", service.config().autoIpbanReason(), 0, "Автоматически", ip)
                                            .thenAccept(autoRecord -> service.kickIfOnline(autoRecord.uuid(), autoRecord.reason(), autoRecord.durationSeconds(), autoRecord.id(), autoRecord.nnr()));
                                }
                            });
                        }
                    });
        });
        return true;
    }

    private void handlePostIssue(PunishmentRecord record, Type type) {
        switch (type) {
            case BAN, IPBAN, IDBAN -> service.kickIfOnline(record.uuid(), record.reason(), record.durationSeconds(), record.id(), record.nnr());
            case MUTE -> service.sendMuteMessage(record.uuid(), record.reason(), record.durationSeconds(), record.id(), record.nnr());
            case WARN -> service.sendWarnMessage(record.uuid(), record.reason(), record.durationSeconds(), record.id(), record.nnr());
            default -> {
            }
        }
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
