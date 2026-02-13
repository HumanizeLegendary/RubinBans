package com.pluginbans.paper;

import com.pluginbans.core.DatabaseConfig;

public record PaperConfig(
        DatabaseConfig databaseConfig,
        long warnDurationSeconds,
        String autoIpbanReason,
        long checkDurationSeconds,
        long checkTimeoutBanSeconds,
        String checkTimeoutBanReason,
        boolean muteBlockCommands
) {
}
