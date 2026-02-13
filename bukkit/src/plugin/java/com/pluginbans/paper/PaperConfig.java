package com.pluginbans.paper;

import com.pluginbans.core.DatabaseConfig;

public record PaperConfig(
        DatabaseConfig databaseConfig,
        long warnDurationSeconds,
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
