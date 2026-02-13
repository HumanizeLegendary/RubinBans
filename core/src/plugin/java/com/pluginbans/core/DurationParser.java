package com.pluginbans.core;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class DurationParser {
    private static final Pattern TOKEN = Pattern.compile("(\\d+)([smhd])");
    private static final Set<String> PERMANENT_VALUES = Set.of(
            "perm",
            "permanent",
            "forever",
            "навсегда"
    );

    private DurationParser() {
    }

    public static long parseToSeconds(String input) {
        String normalized = input.trim().toLowerCase(Locale.ROOT);
        if (PERMANENT_VALUES.contains(normalized)) {
            return 0L;
        }
        Matcher matcher = TOKEN.matcher(normalized);
        long total = 0L;
        int matches = 0;
        while (matcher.find()) {
            matches++;
            long value = Long.parseLong(matcher.group(1));
            switch (matcher.group(2)) {
                case "s" -> total += value;
                case "m" -> total += value * 60L;
                case "h" -> total += value * 3600L;
                case "d" -> total += value * 86400L;
                default -> {
                }
            }
        }
        if (matches == 0 || total < 0 || !normalized.replaceAll("\\d+[smhd]", "").isEmpty()) {
            throw new IllegalArgumentException("Неверный формат длительности.");
        }
        return total;
    }
}
