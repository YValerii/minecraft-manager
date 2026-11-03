package com.mcmanager.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mcmanager.common.Http;
import com.mcmanager.content.ContentType;
import com.mcmanager.content.LocalFile;
import com.mcmanager.settings.SettingsDto;

class ModrinthProviderTest {

    private static final String VERSION_FILES = "https://api.modrinth.com/v2/version_files";
    private static final String UPDATE = VERSION_FILES + "/update";
    private static final SettingsDto SETTINGS = new SettingsDto("/mc", "1.21", "fabric", "MODRINTH", null, null, null,
            null, null, null);

    private final ObjectMapper mapper = new ObjectMapper();
    private final Http http = mock(Http.class);
    private final ModrinthProvider provider = new ModrinthProvider(http);

    private JsonNode json(String s) throws Exception {
        return mapper.readTree(s);
    }

    private static LocalFile file(String id, String sha1) {
        return new LocalFile(id, id, Path.of(id), false, 1, 1, null, sha1, () -> null);
    }

    private static ProjectInfo known(String versionId) {
        return new ProjectInfo(ProviderType.MODRINTH, "p", "n", null, null, null, versionId, "1");
    }

    private UpdateCheck latestForA() {
        return provider.latest(ContentType.MODS, List.of(file("a", "s")), Map.of("a", known("v1")), SETTINGS).get("a");
    }

    @Test
    void typeIsModrinth() {
        assertThat(provider.type()).isEqualTo(ProviderType.MODRINTH);
    }

    // ---- identify

    @Test
    void identifySkipsFoldersAndUnhashedFilesWithoutCallingTheApi() {
        LocalFile folder = new LocalFile("d", "d", Path.of("d"), true, 0, 1, null, "sha", () -> null);

        assertThat(provider.identify(ContentType.MODS, List.of(folder, file("x", null)), SETTINGS)).isEmpty();
        verify(http, never()).post(any(), any(), anyMap());
    }

    @Test
    void identifyMatchesFilesByHash() throws Exception {
        when(http.post(eq(VERSION_FILES), any(), anyMap())).thenReturn(json("""
                {"sha1":{"id":"v1","project_id":"p1","version_number":"1.2","name":"ignored"}}"""));
        when(http.get(any(), anyMap())).thenReturn(json("""
                [{"id":"p1","title":"Sodium","description":"Fast","icon_url":"http://i","slug":"sodium","project_type":"mod"}]"""));

        Map<String, ProjectInfo> out = provider.identify(ContentType.MODS,
                List.of(file("a", "sha1"), file("copy-of-a", "sha1"), file("unknown", "other")), SETTINGS);

        ProjectInfo expected = new ProjectInfo(ProviderType.MODRINTH, "p1", "Sodium", "Fast", "http://i",
                "https://modrinth.com/mod/sodium", "v1", "1.2");
        assertThat(out).containsOnlyKeys("a", "copy-of-a");
        assertThat(out.get("a")).isEqualTo(expected);
        assertThat(out.get("copy-of-a")).isEqualTo(expected);
    }

    @Test
    void identifyAsksForHashesAsSha1() throws Exception {
        when(http.post(any(), any(), anyMap())).thenReturn(json("{}"));

        provider.identify(ContentType.MODS, List.of(file("a", "s")), SETTINGS);

        verify(http).post(VERSION_FILES, Map.of("hashes", List.of("s"), "algorithm", "sha1"), Map.of());
    }

    @Test
    void identifyFallsBackForOptionalFields() throws Exception {
        when(http.post(any(), any(), anyMap())).thenReturn(json("""
                {"s":{"id":"v1","project_id":"p1","name":"Display Name"}}"""));
        when(http.get(any(), anyMap())).thenReturn(json("[{\"id\":\"p1\",\"title\":\"T\"}]"));

        ProjectInfo p = provider.identify(ContentType.MODS, List.of(file("a", "s")), SETTINGS).get("a");

        assertThat(p.versionNumber()).isEqualTo("Display Name");
        assertThat(p.pageUrl()).isEqualTo("https://modrinth.com/mod/p1");
    }

    @Test
    void identifyIgnoresVersionsWhoseProjectIsMissing() throws Exception {
        when(http.post(any(), any(), anyMap())).thenReturn(json("""
                {"a":{"id":"v","project_id":"gone"},"b":{"id":"v"}}"""));
        when(http.get(any(), anyMap())).thenReturn(json("[]"));

        assertThat(provider.identify(ContentType.MODS, List.of(file("a", "a"), file("b", "b")), SETTINGS)).isEmpty();
    }

    @Test
    void identifySplitsLargeRequestsIntoBatches() throws Exception {
        List<LocalFile> files = new ArrayList<>();
        for (int i = 0; i < 101; i++) {
            files.add(file("f" + i, "s" + i));
        }
        when(http.post(any(), any(), anyMap())).thenReturn(json("{}"));

        provider.identify(ContentType.MODS, files, SETTINGS);

        verify(http, times(2)).post(eq(VERSION_FILES), any(), anyMap());
    }

    @Test
    void identifyEncodesProjectIdsIntoTheUrl() throws Exception {
        when(http.post(any(), any(), anyMap())).thenReturn(json("{\"s\":{\"id\":\"v\",\"project_id\":\"p1\"}}"));
        when(http.get(any(), anyMap())).thenReturn(json("[]"));

        provider.identify(ContentType.MODS, List.of(file("a", "s")), SETTINGS);

        verify(http).get(contains("/projects?ids=%5B%22p1%22%5D"), anyMap());
    }

    // ---- latest

    @Test
    void latestIsEmptyWithNothingIdentified() {
        assertThat(provider.latest(ContentType.MODS, List.of(file("a", "s")), Map.of(), SETTINGS)).isEmpty();
        verify(http, never()).post(any(), any(), anyMap());
    }

    @Test
    void latestReportsAnUpdateWithItsPrimaryFile() throws Exception {
        when(http.post(eq(UPDATE), any(), anyMap())).thenReturn(json("""
                {"s":{"id":"v2","version_number":"2.0","changelog":"notes","files":[
                  {"filename":"secondary.jar","url":"u1","primary":false,"hashes":{"sha512":"h1"}},
                  {"filename":"main.jar","url":"u2","primary":true,"hashes":{"sha512":"h2"}}
                ]}}"""));

        UpdateCheck c = latestForA();

        assertThat(c.status()).isEqualTo(UpdateStatus.UPDATE_AVAILABLE);
        assertThat(c.update()).isEqualTo(new UpdateInfo("v2", "2.0", "main.jar", "u2", "sha512", "h2", "notes"));
    }

    @Test
    void latestUsesTheFirstFileWhenNoneIsPrimary() throws Exception {
        when(http.post(any(), any(), anyMap())).thenReturn(json("""
                {"s":{"id":"v2","name":"Named","files":[{"filename":"first.jar","url":"u","hashes":{}}]}}"""));

        UpdateInfo u = latestForA().update();

        assertThat(u.fileName()).isEqualTo("first.jar");
        assertThat(u.versionNumber()).isEqualTo("Named");
        assertThat(u.hash()).isNull();
    }

    @Test
    void aVersionWithoutFilesHasNoDownload() throws Exception {
        when(http.post(any(), any(), anyMap())).thenReturn(json("{\"s\":{\"id\":\"v2\"}}"));

        UpdateInfo u = latestForA().update();

        assertThat(u.fileName()).isNull();
        assertThat(u.downloadUrl()).isNull();
    }

    @Test
    void theSameVersionIsUpToDate() throws Exception {
        when(http.post(any(), any(), anyMap())).thenReturn(json("{\"s\":{\"id\":\"v1\"}}"));

        assertThat(latestForA().status()).isEqualTo(UpdateStatus.UP_TO_DATE);
    }

    @Test
    void aVersionWithoutAnIdIsTreatedAsAnUpdate() throws Exception {
        when(http.post(any(), any(), anyMap())).thenReturn(json("{\"s\":{}}"));

        assertThat(latestForA().status()).isEqualTo(UpdateStatus.UPDATE_AVAILABLE);
    }

    @Test
    void noMatchMeansNoCompatibleVersion() throws Exception {
        when(http.post(any(), any(), anyMap())).thenReturn(json("{}"));

        assertThat(latestForA()).isEqualTo(new UpdateCheck(UpdateStatus.NO_COMPATIBLE, null));
    }

    @Test
    void longChangelogsAreCut() throws Exception {
        String longText = "x".repeat(5000);
        when(http.post(any(), any(), anyMap()))
                .thenReturn(json("{\"s\":{\"id\":\"v2\",\"changelog\":\"" + longText + "\"}}"));

        assertThat(latestForA().update().changelog()).hasSize(4001).endsWith("…");
    }

    @ParameterizedTest
    @CsvSource({
            "MODS, fabric",
            "RESOURCEPACKS, minecraft",
            "SHADERPACKS, iris|optifine|canvas|vanilla",
            "DATAPACKS, datapack",
    })
    @SuppressWarnings("unchecked")
    void latestAsksForLoadersMatchingTheContentType(ContentType type, String loaders) throws Exception {
        when(http.post(any(), any(), anyMap())).thenReturn(json("{}"));

        provider.latest(type, List.of(file("a", "s")), Map.of("a", known("v")), SETTINGS);

        ArgumentCaptor<Object> body = ArgumentCaptor.forClass(Object.class);
        verify(http).post(eq(UPDATE), body.capture(), anyMap());
        Map<String, Object> sent = (Map<String, Object>) body.getValue();
        assertThat(sent.get("loaders")).isEqualTo(List.of(loaders.split("\\|")));
        assertThat(sent.get("game_versions")).isEqualTo(List.of("1.21"));
        assertThat(sent.get("hashes")).isEqualTo(List.of("s"));
        assertThat(sent.get("algorithm")).isEqualTo("sha1");
    }
}
