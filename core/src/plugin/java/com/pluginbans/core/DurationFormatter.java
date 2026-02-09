package com.pluginbans.core;

import java.util.ArrayList;
import java.util.List;

public final class DurationFormatter {
    private DurationFormatter() {
    }

    public static String formatSeconds(long seconds) {
        if (seconds <= 0) {
            return "Навсегда";
        }
        long remaining = seconds;
        long days = remaining / 86400;
        remaining %= 86400;
        long hours = remaining / 3600;
        remaining %= 3600;
        long minutes = remaining / 60;
        long secs = remaining % 60;
        List<String> parts = new ArrayList<>();
        if (days > 0) {
            parts.add(days + "д");
        }
        if (hours > 0) {
            parts.add(hours + "ч");
        }
        if (minutes > 0) {
            parts.add(minutes + "м");
        }
        if (secs > 0) {
            parts.add(secs + "с");
        }
        return String.join(" ", parts);
    }
}
