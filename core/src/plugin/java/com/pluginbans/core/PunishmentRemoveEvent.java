package com.pluginbans.core;

public record PunishmentRemoveEvent(PunishmentRecord record, String reason) {
}
