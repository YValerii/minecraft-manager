package com.mcmanager.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class HashingTest {

    @TempDir
    Path dir;

    private Path file(byte[] content) throws Exception {
        return Files.write(dir.resolve("f.bin"), content);
    }

    @Test
    void sha1MatchesTheStandardTestVector() throws Exception {
        assertThat(Hashing.sha1(file("abc".getBytes(StandardCharsets.UTF_8))))
                .isEqualTo("a9993e364706816aba3e25717850c26c9cd0d89d");
    }

    @Test
    void digestSupportsOtherAlgorithms() throws Exception {
        Path f = file("abc".getBytes(StandardCharsets.UTF_8));
        assertThat(Hashing.digest(f, "MD5")).isEqualTo("900150983cd24fb0d6963f7d28e17f72");
        assertThat(Hashing.digest(f, "SHA-512")).startsWith("ddaf35a193617aba");
    }

    @Test
    void digestRejectsAnUnknownAlgorithm() throws Exception {
        Path f = file(new byte[] { 1 });
        assertThatThrownBy(() -> Hashing.digest(f, "NOPE")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void hexKeepsLeadingZerosAndHandlesHighBytes() {
        assertThat(Hashing.hex(new byte[] { 0x00, 0x0f, (byte) 0xff })).isEqualTo("000fff");
    }

    /** Expected values come from a separate implementation of the algorithm, not from this code. */
    @ParameterizedTest
    @CsvSource({
            "'', 1540447798",
            "a, 626045324",
            "ab, 1692487918",
            "abc, 1621425345",
            "abcd, 3376380438",
            "abcde, 3469237630",
            "minecraft, 1532164859",
    })
    void murmur2MatchesReferenceValues(String input, long expected) {
        assertThat(Hashing.murmur2(input.getBytes(StandardCharsets.UTF_8), 1)).isEqualTo(expected);
    }

    @Test
    void murmur2TreatsBytesAsUnsigned() {
        byte[] data = { (byte) 0xff, (byte) 0x80, (byte) 0xfe, (byte) 0x81, (byte) 0xff };
        assertThat(Hashing.murmur2(data, 1)).isEqualTo(3713271125L);
    }

    @Test
    void curseForgeFingerprintIgnoresWhitespace() throws Exception {
        long spaced = Hashing.curseForgeFingerprint(file("a b\tc\r\nd".getBytes(StandardCharsets.UTF_8)));
        assertThat(spaced).isEqualTo(3376380438L);
    }
}
