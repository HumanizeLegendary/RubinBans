package com.pluginbans.paper;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.CommandSender;

import java.util.Map;

public final class MessageService {
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
        return miniMessage.deserialize(messages.prefix() + message);
    }

    public String applyPlaceholders(String template, Map<String, String> placeholders) {
        String result = template;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            result = result.replace(entry.getKey(), entry.getValue());
        }
        return result;
    }
}
