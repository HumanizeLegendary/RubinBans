package com.pluginbans.core;

import java.util.List;
import java.util.Optional;

public final class ActivePunishment {
    private final List<PunishmentRecord> records;

    public ActivePunishment(List<PunishmentRecord> records) {
        this.records = List.copyOf(records);
    }

    public Optional<PunishmentRecord> get(PunishmentType type) {
        return records.stream().filter(record -> record.type() == type).findFirst();
    }

    public boolean has(PunishmentType type) {
        return records.stream().anyMatch(record -> record.type() == type);
    }

    public List<PunishmentRecord> all() {
        return records;
    }
}
