package com.pluginbans.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

public final class AuditLogger {
    private final Path file;

    public AuditLogger(Path file) {
        this.file = Objects.requireNonNull(file, "file");
    }

    public synchronized void log(String message) {
        String line = "[%s] %s%n".formatted(DateTimeFormatter.ISO_INSTANT.format(Instant.now()), message);
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, line, java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
        } catch (IOException exception) {
            throw new IllegalStateException("Не удалось записать запись аудита.", exception);
        }
    }
}
