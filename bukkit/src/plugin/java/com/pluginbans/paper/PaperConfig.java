package com.pluginbans.paper;

import com.pluginbans.core.DatabaseConfig;

import java.util.List;

public record PaperConfig(
        DatabaseConfig databaseConfig,
        List<String> punishTypes,
        List<String> nnrTypes,
        long warnDurationSeconds,
        String nnrHiddenReason,
        String autoIpbanReason,
        boolean restEnabled,
        int restPort,
        String restBind
) {
}
