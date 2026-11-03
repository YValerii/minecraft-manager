package com.mcmanager.content;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class HashCacheTest {

    private final HashCache cache = new HashCache();

    @TempDir
    Path dir;

    @Test
    void computesTheSha1() throws Exception {
        Path f = Files.writeString(dir.resolve("f"), "abc");
        assertThat(cache.sha1(f, 3, 1)).isEqualTo("a9993e364706816aba3e25717850c26c9cd0d89d");
    }

    @Test
    void reusesTheHashWhileSizeAndTimeStayTheSame() throws Exception {
        Path f = Files.writeString(dir.resolve("f"), "abc");
        String first = cache.sha1(f, 3, 1);
        Long fingerprint = cache.fingerprint(f, 3, 1);

        Files.writeString(f, "different");

        assertThat(cache.sha1(f, 3, 1)).isEqualTo(first);
        assertThat(cache.fingerprint(f, 3, 1)).isEqualTo(fingerprint);
    }

    @Test
    void aChangedFileIsHashedAgain() throws Exception {
        Path f = Files.writeString(dir.resolve("f"), "abc");
        String first = cache.sha1(f, 3, 1);
        Long fingerprint = cache.fingerprint(f, 3, 1);

        Files.writeString(f, "different");

        assertThat(cache.sha1(f, 9, 2)).isNotEqualTo(first);
        assertThat(cache.fingerprint(f, 9, 2)).isNotEqualTo(fingerprint);
    }

    @Test
    void computesTheFingerprint() throws Exception {
        Path f = Files.writeString(dir.resolve("f"), "a b c d");
        assertThat(cache.fingerprint(f, 7, 1)).isEqualTo(3376380438L);
    }

    @Test
    void anUnreadableFileHasNoHash() {
        Path missing = dir.resolve("missing");
        assertThat(cache.sha1(missing, 1, 1)).isNull();
        assertThat(cache.fingerprint(missing, 1, 1)).isNull();
    }
}
