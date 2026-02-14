package com.pluginbans.core;

public final class PunishmentRules {
    private PunishmentRules() {
    }

    public static boolean isBanLike(PunishmentType type) {
        return type == PunishmentType.BAN
                || type == PunishmentType.TEMPBAN
                || type == PunishmentType.IPBAN;
    }

    public static boolean blocksLogin(PunishmentType type) {
        return isBanLike(type) || type == PunishmentType.WARN;
    }
}
