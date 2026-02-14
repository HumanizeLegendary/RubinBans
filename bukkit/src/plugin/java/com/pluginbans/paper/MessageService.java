package com.pluginbans.paper;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.CommandSender;

import java.util.Map;
import java.util.regex.Pattern;

public final class MessageService {
    private static final String LEGACY_PREFIX = "<gray>[<aqua>PluginBans</aqua>]</gray> ";
    private static final String DEFAULT_PREFIX = "<red>БАНЫ | </red>";
    private static final Pattern MINI_ISSUER_SEGMENT = Pattern.compile(
            "\\s*<gray>Выдал:</gray>\\s*<white>[^<]*</white>",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );
    private static final Pattern PLAIN_ISSUER_LINE = Pattern.compile("(?im)^\\s*Выдал:.*(?:\\R|$)");
    private static final Pattern EXTRA_NEWLINES = Pattern.compile("(\\R){3,}");

    private final MessagesConfig messages;
    private final MiniMessage miniMessage;

    public MessageService(MessagesConfig messages) {
        this.messages = messages;
        this.miniMessage = MiniMessage.miniMessage();
    }

    public void send(CommandSender sender, String message) {
        sender.sendMessage(format(message));
    }

    public Component format(String message) {
        String configuredPrefix = messages.prefix();
        String prefix = configuredPrefix;
        if (prefix == null || prefix.isBlank() || LEGACY_PREFIX.equals(prefix)) {
            prefix = DEFAULT_PREFIX;
        }
        return miniMessage.deserialize(prefix + message);
    }

    public Component formatRaw(String message) {
        return miniMessage.deserialize(message);
    }

    public String hideIssuerDetails(String message) {
        if (message == null || message.isBlank()) {
            return message;
        }
        String result = message.replace("%actor%", "");
        result = MINI_ISSUER_SEGMENT.matcher(result).replaceAll("");
        result = PLAIN_ISSUER_LINE.matcher(result).replaceAll("");
        return EXTRA_NEWLINES.matcher(result).replaceAll("\n\n");
    }

    public String applyPlaceholders(String template, Map<String, String> placeholders) {
        String result = template;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            result = result.replace(entry.getKey(), entry.getValue());
        }
        return result;
    }
}
