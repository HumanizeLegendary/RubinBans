package com.pluginbans.core;

import java.security.SecureRandom;

public final class PunishmentIdGenerator {
    private static final String ALPHANUM = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final int ID_LENGTH = 6;
    private static final SecureRandom RANDOM = new SecureRandom();

    private PunishmentIdGenerator() {
    }

    public static String generate(String typeCode) {
        StringBuilder builder = new StringBuilder(ID_LENGTH);
        for (int i = 0; i < ID_LENGTH; i++) {
            builder.append(ALPHANUM.charAt(RANDOM.nextInt(ALPHANUM.length())));
        }
        return builder.toString();
    }
}
