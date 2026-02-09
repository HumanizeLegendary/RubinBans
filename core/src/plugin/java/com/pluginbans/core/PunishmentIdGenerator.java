package com.pluginbans.core;

import java.security.SecureRandom;

public final class PunishmentIdGenerator {
    private static final String ALPHANUM = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private PunishmentIdGenerator() {
    }

    public static String generate(String typeCode) {
        StringBuilder builder = new StringBuilder("PBRB-").append(typeCode).append("-");
        for (int i = 0; i < 5; i++) {
            builder.append(ALPHANUM.charAt(RANDOM.nextInt(ALPHANUM.length())));
        }
        return builder.toString();
    }
}
