package com.mcmanager.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

class JsonFileStoreTest {

    private static final TypeReference<List<String>> STRINGS = new TypeReference<>() {
    };

    private final ObjectMapper mapper = new ObjectMapper();

    @TempDir
    Path dir;

    @Test
    void writesThenReadsBack() {
        Path file = dir.resolve("nested/folder/data.json");
        JsonFileStore.write(mapper, file, List.of("a", "b"));

        assertThat(JsonFileStore.read(mapper, file, STRINGS)).containsExactly("a", "b");
        assertThat(JsonFileStore.read(mapper, file, String[].class)).containsExactly("a", "b");
    }

    @Test
    void leavesNoTempFileBehindAndOverwrites() {
        Path file = dir.resolve("data.json");
        JsonFileStore.write(mapper, file, List.of("old"));
        JsonFileStore.write(mapper, file, List.of("new"));

        assertThat(JsonFileStore.read(mapper, file, STRINGS)).containsExactly("new");
        assertThat(dir.resolve("data.json.tmp")).doesNotExist();
    }

    @Test
    void missingFileReadsAsNull() {
        Path file = dir.resolve("missing.json");
        assertThat(JsonFileStore.read(mapper, file, String[].class)).isNull();
        assertThat(JsonFileStore.read(mapper, file, STRINGS)).isNull();
    }

    @Test
    void corruptFileReadsAsNull() throws Exception {
        Path file = Files.writeString(dir.resolve("bad.json"), "{not json");
        assertThat(JsonFileStore.read(mapper, file, String[].class)).isNull();
        assertThat(JsonFileStore.read(mapper, file, STRINGS)).isNull();
    }

    @Test
    void writeFailureIsReportedToTheCaller() throws Exception {
        Path notAFolder = Files.writeString(dir.resolve("file"), "x");
        Path target = notAFolder.resolve("data.json");
        assertThatThrownBy(() -> JsonFileStore.write(mapper, target, List.of()))
                .isInstanceOf(UncheckedIOException.class)
                .hasMessageContaining("Failed to write");
    }
}
