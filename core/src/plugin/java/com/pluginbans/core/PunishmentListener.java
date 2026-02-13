package com.pluginbans.core;

public interface PunishmentListener {
    default void onCreate(PunishmentCreateEvent event) {
    }

    default void onRemove(PunishmentRemoveEvent event) {
    }
}
