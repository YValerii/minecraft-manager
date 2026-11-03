package com.mcmanager.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.mcmanager.config.AppProperties;

class IdentityCacheTest {

    private static final ProjectInfo PROJECT = new ProjectInfo(ProviderType.MODRINTH, "pid", "Name", "Desc", "icon",
            "page", "vid", "1.0");

    private final ObjectMapper mapper = JsonMapper.builder().findAndAddModules().build();

    @TempDir
    Path temp;

    private IdentityCache cache(String dataDir) {
        return new IdentityCache(mapper, new AppProperties(null, null, null, null, null, 60, dataDir, null));
    }

    private IdentityCache cache() {
        return cache(temp.toString());
    }

    @Test
    void keyCombinesProviderAndHash() {
        assertThat(IdentityCache.key(ProviderType.CURSEFORGE, "abc")).isEqualTo("CURSEFORGE:abc");
    }

    @Test
    void aFoundEntryConvertsBackToTheProject() {
        IdentityCache.Entry e = IdentityCache.Entry.of(ProviderType.MODRINTH, "sha", PROJECT);

        assertThat(e.cacheKey()).isEqualTo("MODRINTH:sha");
        assertThat(e.found()).isTrue();
        assertThat(e.toProject(ProviderType.MODRINTH)).isEqualTo(PROJECT);
    }

    @Test
    void aNotFoundEntryIsRemembered() {
        Instant before = Instant.now();
        IdentityCache.Entry e = IdentityCache.Entry.of(ProviderType.MODRINTH, "sha", null);

        assertThat(e.found()).isFalse();
        assertThat(e.projectId()).isNull();
        assertThat(e.checkedAt()).isAfterOrEqualTo(before);
    }

    @Test
    void longValuesAreCut() {
        String huge = "x".repeat(5000);
        ProjectInfo p = new ProjectInfo(ProviderType.MODRINTH, "p", huge, huge, huge, huge, "v", huge);

        IdentityCache.Entry e = IdentityCache.Entry.of(ProviderType.MODRINTH, "sha", p);

        assertThat(e.name()).hasSize(512);
        assertThat(e.description()).hasSize(4000);
        assertThat(e.iconUrl()).hasSize(1024);
        assertThat(e.pageUrl()).hasSize(1024);
        assertThat(e.versionNumber()).hasSize(512);
    }

    @Test
    void nullValuesStayNull() {
        ProjectInfo p = new ProjectInfo(ProviderType.MODRINTH, "p", null, null, null, null, "v", null);
        assertThat(IdentityCache.Entry.of(ProviderType.MODRINTH, "sha", p).name()).isNull();
    }

    @Test
    void findAllReturnsOnlyKnownKeys() {
        IdentityCache cache = cache();
        IdentityCache.Entry e = IdentityCache.Entry.of(ProviderType.MODRINTH, "a", PROJECT);
        cache.saveAll(List.of(e));

        Map<String, IdentityCache.Entry> found = cache.findAll(List.of("MODRINTH:a", "MODRINTH:b"));

        assertThat(found).containsOnlyKeys("MODRINTH:a");
    }

    @Test
    void entriesSurviveARestart() {
        cache().saveAll(List.of(IdentityCache.Entry.of(ProviderType.MODRINTH, "a", PROJECT)));

        IdentityCache.Entry loaded = cache().findAll(List.of("MODRINTH:a")).get("MODRINTH:a");

        assertThat(loaded.toProject(ProviderType.MODRINTH)).isEqualTo(PROJECT);
    }

    @Test
    void savingNothingWritesNothing() {
        cache().saveAll(List.of());
        assertThat(temp.resolve("identity-cache.json")).doesNotExist();
    }

    @Test
    void aCorruptFileStartsEmpty() throws Exception {
        Files.writeString(temp.resolve("identity-cache.json"), "{broken");
        assertThat(cache().findAll(List.of("MODRINTH:a"))).isEmpty();
    }

    @Test
    void aFailedWriteStillKeepsTheEntryInMemory() throws Exception {
        Path notAFolder = Files.writeString(temp.resolve("file"), "x");
        IdentityCache cache = cache(notAFolder.toString());
        IdentityCache.Entry e = IdentityCache.Entry.of(ProviderType.MODRINTH, "a", PROJECT);

        assertThatCode(() -> cache.saveAll(List.of(e))).doesNotThrowAnyException();
        assertThat(cache.findAll(List.of("MODRINTH:a"))).containsKey("MODRINTH:a");
    }

    @Test
    void aMissingDataDirFallsBackToTheDefaultFolder() {
        assertThat(cache(null).findAll(List.of("nothing"))).isEmpty();
        assertThat(cache(" ").findAll(List.of("nothing"))).isEmpty();
    }
}
