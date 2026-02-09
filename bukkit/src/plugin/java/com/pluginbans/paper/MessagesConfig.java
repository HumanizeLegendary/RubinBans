package com.pluginbans.paper;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.HashMap;
import java.util.Map;

public record MessagesConfig(
        String prefix,
        String banScreen,
        String muteMessage,
        String warnMessage,
        String permissionDenied,
        String alreadyPunished,
        String joinDenied,
        Map<String, String> errors
) {
    public static MessagesConfig from(FileConfiguration configuration) {
        String prefix = configuration.getString("prefix", "<red>[PluginBans]</red> ");
        String banScreen = configuration.getString("ban_screen", "");
        String muteMessage = configuration.getString("mute_message", "");
        String warnMessage = configuration.getString("warn_message", "");
        String permissionDenied = configuration.getString("permission_denied", "");
        String alreadyPunished = configuration.getString("already_punished", "");
        String joinDenied = configuration.getString("join_denied", "");
        Map<String, String> errors = new HashMap<>();
        ConfigurationSection section = configuration.getConfigurationSection("errors");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                errors.put(key, section.getString(key, ""));
            }
        }
        return new MessagesConfig(prefix, banScreen, muteMessage, warnMessage, permissionDenied, alreadyPunished, joinDenied, errors);
    }

    public String error(String key) {
        return errors.getOrDefault(key, "<red>Неизвестная ошибка.</red>");
    }
}
