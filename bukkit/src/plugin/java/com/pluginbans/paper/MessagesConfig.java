package com.pluginbans.paper;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.HashMap;
import java.util.Map;

public record MessagesConfig(
        String prefix,
        String banMessage,
        String muteMessage,
        String warnMessage,
        String checkMessage,
        String kickMessage,
        String checkBlockMessage,
        String mutedChatMessage,
        String permissionDenied,
        Map<String, String> errors
) {
    public static MessagesConfig from(FileConfiguration configuration) {
        String prefix = configuration.getString("prefix", "<red>[PluginBans]</red> ");
        String ban = configuration.getString("ban", "");
        String mute = configuration.getString("mute", "");
        String warn = configuration.getString("warn", "");
        String check = configuration.getString("check", "");
        String kick = configuration.getString("kick", "");
        String checkBlock = configuration.getString("check-block", "");
        String mutedChat = configuration.getString("muted-chat", "");
        String permissionDenied = configuration.getString("permission-denied", "<red>Недостаточно прав.</red>");
        Map<String, String> errors = new HashMap<>();
        ConfigurationSection section = configuration.getConfigurationSection("errors");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                errors.put(key, section.getString(key, ""));
            }
        }
        return new MessagesConfig(prefix, ban, mute, warn, check, kick, checkBlock, mutedChat, permissionDenied, errors);
    }

    public String error(String key) {
        return errors.getOrDefault(key, "<red>Неизвестная ошибка.</red>");
    }
}
