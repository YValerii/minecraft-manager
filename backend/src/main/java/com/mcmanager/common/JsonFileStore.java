package com.mcmanager.common;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Reads/writes a single JSON file. Writes go through a temp file + atomic move, so a crash never leaves a half-written file. */
public final class JsonFileStore {

    private static final Logger log = LoggerFactory.getLogger(JsonFileStore.class);

    private JsonFileStore() {
    }

    public static <T> T read(ObjectMapper mapper, Path file, Class<T> type) {
        if (!Files.isRegularFile(file)) {
            return null;
        }
        try {
            return mapper.readValue(file.toFile(), type);
        } catch (IOException e) {
            log.warn("Failed to read {}: {}", file, e.getMessage());
            return null;
        }
    }

    public static <T> T read(ObjectMapper mapper, Path file, TypeReference<T> type) {
        if (!Files.isRegularFile(file)) {
            return null;
        }
        try {
            return mapper.readValue(file.toFile(), type);
        } catch (IOException e) {
            log.warn("Failed to read {}: {}", file, e.getMessage());
            return null;
        }
    }

    /** @throws UncheckedIOException if the file can't be written; callers decide whether that is fatal. */
    public static synchronized void write(ObjectMapper mapper, Path file, Object value) {
        try {
            Files.createDirectories(file.getParent());
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            mapper.writerWithDefaultPrettyPrinter().writeValue(tmp.toFile(), value);
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write " + file, e);
        }
    }
}
