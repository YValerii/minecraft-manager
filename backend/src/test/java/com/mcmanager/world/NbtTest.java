package com.mcmanager.world;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.fasterxml.jackson.databind.JsonNode;

class NbtTest {

    @TempDir
    Path dir;

    private JsonNode read(Map<String, ?> values) throws IOException {
        return Nbt.readGzipFile(NbtWriter.write(dir.resolve("t.dat"), values));
    }

    @Test
    void readsEveryScalarType() throws Exception {
        JsonNode n = read(Map.of(
                "byte", (byte) -5,
                "short", (short) 300,
                "int", 70000,
                "long", 5_000_000_000L,
                "float", 1.5f,
                "double", 2.25,
                "string", "héllo"));

        assertThat(n.get("byte").asInt()).isEqualTo(-5);
        assertThat(n.get("short").asInt()).isEqualTo(300);
        assertThat(n.get("int").asInt()).isEqualTo(70000);
        assertThat(n.get("long").asLong()).isEqualTo(5_000_000_000L);
        assertThat(n.get("float").floatValue()).isEqualTo(1.5f);
        assertThat(n.get("double").asDouble()).isEqualTo(2.25);
        assertThat(n.get("string").asText()).isEqualTo("héllo");
    }

    @Test
    void readsListsAndNestedCompounds() throws Exception {
        JsonNode n = read(Map.of(
                "list", List.of(1, 2, 3),
                "empty", List.of(),
                "nested", Map.of("inner", Map.of("x", "y"))));

        assertThat(n.get("list")).hasSize(3);
        assertThat(n.get("list").get(2).asInt()).isEqualTo(3);
        assertThat(n.get("empty")).isEmpty();
        assertThat(n.at("/nested/inner/x").asText()).isEqualTo("y");
    }

    @Test
    void readsSmallPrimitiveArrays() throws Exception {
        JsonNode n = read(Map.of(
                "bytes", new byte[] { 1, -2 },
                "ints", new int[] { 3, 4 },
                "longs", new long[] { 5L, Long.MAX_VALUE }));

        assertThat(n.get("bytes").get(1).asInt()).isEqualTo(-2);
        assertThat(n.get("ints").get(1).asInt()).isEqualTo(4);
        assertThat(n.get("longs").get(0).asLong()).isEqualTo(5L);
        assertThat(n.get("longs").get(1).asText()).isEqualTo(Long.toString(Long.MAX_VALUE));
    }

    @Test
    void largeArraysAreDescribedInsteadOfExpanded() throws Exception {
        JsonNode n = read(Map.of(
                "bytes", new byte[65],
                "ints", new int[100],
                "longs", new long[65],
                "after", "still read"));

        assertThat(n.get("bytes").asText()).isEqualTo("byte[65]");
        assertThat(n.get("ints").asText()).isEqualTo("int[100]");
        assertThat(n.get("longs").asText()).isEqualTo("long[65]");
        assertThat(n.get("after").asText()).isEqualTo("still read");
    }

    @Test
    void arraysAtTheLimitAreStillExpanded() throws Exception {
        assertThat(read(Map.of("ints", new int[64])).get("ints").isArray()).isTrue();
    }

    @Test
    void longsBeyondWhatJavaScriptCanRepresentBecomeStrings() throws Exception {
        long safe = 1L << 53;
        JsonNode n = read(Map.of("safe", safe, "negSafe", -safe, "big", safe + 1, "negBig", -safe - 1));

        assertThat(n.get("safe").isNumber()).isTrue();
        assertThat(n.get("negSafe").isNumber()).isTrue();
        assertThat(n.get("big").asText()).isEqualTo(Long.toString(safe + 1));
        assertThat(n.get("big").isTextual()).isTrue();
        assertThat(n.get("negBig").isTextual()).isTrue();
    }

    @Test
    void veryLongListsAreTruncatedButTheRestOfTheFileIsRead() throws Exception {
        List<Integer> many = new ArrayList<>(Collections.nCopies(600, 7));
        JsonNode n = read(Map.of("many", many, "after", 1));

        assertThat(n.get("many")).hasSize(512);
        assertThat(n.get("after").asInt()).isEqualTo(1);
    }

    @Test
    void theRootTagMustBeACompound() throws Exception {
        Path file = NbtWriter.writeRaw(dir.resolve("t.dat"), 8, out -> out.writeUTF("x"));
        assertThatThrownBy(() -> Nbt.readGzipFile(file)).isInstanceOf(IOException.class)
                .hasMessageContaining("must be Compound");
    }

    @Test
    void unknownTagTypesAreRejected() throws Exception {
        Path file = NbtWriter.writeRaw(dir.resolve("t.dat"), 10, out -> {
            out.writeByte(99);
            out.writeUTF("bad");
        });
        assertThatThrownBy(() -> Nbt.readGzipFile(file)).isInstanceOf(IOException.class)
                .hasMessageContaining("Unknown NBT tag type: 99");
    }

    @Test
    void excessiveNestingIsRejected() throws Exception {
        Map<String, Object> deep = Map.of("x", 1);
        for (int i = 0; i < 70; i++) {
            deep = Map.of("d", deep);
        }
        Map<String, Object> root = deep;
        assertThatThrownBy(() -> read(root)).isInstanceOf(IOException.class)
                .hasMessageContaining("nesting too deep");
    }

    @Test
    void aFileThatIsNotGzipIsRejected() throws Exception {
        Path file = Files.writeString(dir.resolve("t.dat"), "plain text");
        assertThatThrownBy(() -> Nbt.readGzipFile(file)).isInstanceOf(IOException.class);
    }

    @Test
    void aTruncatedFileIsRejected() throws Exception {
        Path file = NbtWriter.writeRaw(dir.resolve("t.dat"), 10, out -> out.writeByte(3));
        assertThatThrownBy(() -> Nbt.readGzipFile(file)).isInstanceOf(IOException.class);
    }
}
