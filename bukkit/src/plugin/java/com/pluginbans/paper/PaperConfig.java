package com.pluginbans.paper;

import com.pluginbans.core.DatabaseConfig;

import java.util.List;

public record PaperConfig(
        DatabaseConfig databaseConfig,
        long syncPollSeconds,
        long warnDurationSeconds,
        List<String> warnAllowedReasons,
        List<String> warnExternalActors,
        String autoBanReason,
        long checkDurationSeconds,
        long checkTimeoutBanSeconds,
        String checkTimeoutBanReason,
        boolean muteBlockCommands,
        boolean apiEnabled,
        String apiBind,
        int apiPort,
        String apiToken
) {
}
