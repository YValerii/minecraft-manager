package com.mcmanager.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpStatus;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mcmanager.common.ApiException;
import com.mcmanager.common.Http;
import com.mcmanager.content.ContentType;
import com.mcmanager.content.LocalFile;
import com.mcmanager.settings.SettingsDto;

class CurseForgeProviderTest {

    private static final String API = "https://api.curseforge.com/v1";
    private static final String FINGERPRINTS = API + "/fingerprints/432";
    private static final String MODS = API + "/mods";
    private static final Map<String, String> AUTH = Map.of("x-api-key", "key");

    private final ObjectMapper mapper = new ObjectMapper();
    private final Http http = mock(Http.class);
    private final CurseForgeProvider provider = new CurseForgeProvider(http);

    private JsonNode json(String s) throws Exception {
        return mapper.readTree(s);
    }

    private static SettingsDto settings(String loader, String key) {
        return new SettingsDto("/mc", "1.21", loader, "CURSEFORGE", key, null, null, null, null, null);
    }

    private static SettingsDto settings() {
        return settings("fabric", " key ");
    }

    private static LocalFile file(String id, Long fingerprint) {
        return new LocalFile(id, id, Path.of(id), false, 1, 1, null, "sha", () -> fingerprint);
    }

    private static ProjectInfo known(String projectId, String versionId) {
        return new ProjectInfo(ProviderType.CURSEFORGE, projectId, "n", null, null, null, versionId, "1");
    }

    /** The mod's latest files, as latestFilesIndexes entries in JSON. */
    private void modWith(String indexes) throws Exception {
        when(http.post(eq(MODS), any(), anyMap()))
                .thenReturn(json("{\"data\":[{\"id\":1,\"latestFilesIndexes\":[" + indexes + "]}]}"));
    }

    private UpdateCheck latest(ContentType type, SettingsDto s, String currentVersion) {
        return provider.latest(type, List.of(file("a", 1L)), Map.of("a", known("1", currentVersion)), s).get("a");
    }

    @Test
    void typeIsCurseForge() {
        assertThat(provider.type()).isEqualTo(ProviderType.CURSEFORGE);
    }

    // ---- API key

    @Test
    void everyCallRequiresAnApiKey() {
        for (String key : new String[] { null, " " }) {
            SettingsDto s = settings("fabric", key);
            assertThatThrownBy(() -> provider.identify(ContentType.MODS, List.of(), s))
                    .isInstanceOfSatisfying(ApiException.class, e -> {
                        assertThat(e.status()).isEqualTo(HttpStatus.BAD_REQUEST);
                        assertThat(e.getMessage()).contains("API key");
                    });
        }
    }

    // ---- identify

    @Test
    void identifySkipsFoldersAndFilesWithoutAFingerprint() {
        LocalFile folder = new LocalFile("d", "d", Path.of("d"), true, 0, 1, null, null, () -> 5L);

        assertThat(provider.identify(ContentType.MODS, List.of(folder, file("x", null)), settings())).isEmpty();
        verify(http, never()).post(any(), any(), anyMap());
    }

    @Test
    void identifyMatchesFilesByFingerprint() throws Exception {
        when(http.post(eq(FINGERPRINTS), any(), anyMap())).thenReturn(json("""
                {"data":{"exactMatches":[{"file":{"id":10,"modId":1,"fileFingerprint":100,
                  "displayName":"JEI 1.0","fileName":"jei.jar"}}]}}"""));
        when(http.post(eq(MODS), any(), anyMap())).thenReturn(json("""
                {"data":[{"id":1,"name":"JEI","summary":"Items","logo":{"thumbnailUrl":"thumb","url":"full"},
                  "links":{"websiteUrl":"http://site"}}]}"""));

        Map<String, ProjectInfo> out = provider.identify(ContentType.MODS,
                List.of(file("a", 100L), file("a-copy", 100L), file("other", 200L)), settings());

        ProjectInfo expected = new ProjectInfo(ProviderType.CURSEFORGE, "1", "JEI", "Items", "thumb", "http://site",
                "10", "JEI 1.0");
        assertThat(out).containsOnlyKeys("a", "a-copy");
        assertThat(out.get("a")).isEqualTo(expected);
        verify(http).post(FINGERPRINTS, Map.of("fingerprints", List.of(100L, 200L)), AUTH);
    }

    @Test
    void identifyFallsBackForOptionalFields() throws Exception {
        when(http.post(eq(FINGERPRINTS), any(), anyMap())).thenReturn(json("""
                {"data":{"exactMatches":[{"file":{"id":10,"modId":1,"fileFingerprint":100,"fileName":"jei.jar"}}]}}"""));
        when(http.post(eq(MODS), any(), anyMap())).thenReturn(json("""
                {"data":[{"id":1,"name":"JEI","logo":{"url":"full"}}]}"""));

        ProjectInfo p = provider.identify(ContentType.MODS, List.of(file("a", 100L)), settings()).get("a");

        assertThat(p.iconUrl()).isEqualTo("full");
        assertThat(p.versionNumber()).isEqualTo("jei.jar");
        assertThat(p.pageUrl()).isNull();
    }

    @Test
    void identifyIgnoresFilesWhoseModIsMissing() throws Exception {
        when(http.post(eq(FINGERPRINTS), any(), anyMap())).thenReturn(json("""
                {"data":{"exactMatches":[{"file":{"id":10,"modId":1,"fileFingerprint":100}}]}}"""));
        when(http.post(eq(MODS), any(), anyMap())).thenReturn(json("{\"data\":[]}"));

        assertThat(provider.identify(ContentType.MODS, List.of(file("a", 100L)), settings())).isEmpty();
    }

    @Test
    void identifySplitsLargeRequestsIntoBatches() throws Exception {
        List<LocalFile> files = new ArrayList<>();
        for (long i = 0; i < 201; i++) {
            files.add(file("f" + i, i));
        }
        when(http.post(eq(FINGERPRINTS), any(), anyMap())).thenReturn(json("{\"data\":{\"exactMatches\":[]}}"));

        provider.identify(ContentType.MODS, files, settings());

        verify(http, times(2)).post(eq(FINGERPRINTS), any(), anyMap());
    }

    // ---- latest

    @Test
    void latestSkipsUnidentifiedFiles() {
        assertThat(provider.latest(ContentType.MODS, List.of(file("a", 1L)), Map.of(), settings())).isEmpty();
    }

    @Test
    void aModTheApiDoesntKnowIsUnknown() throws Exception {
        when(http.post(eq(MODS), any(), anyMap())).thenReturn(json("{\"data\":[]}"));

        assertThat(latest(ContentType.MODS, settings(), "5")).isEqualTo(UpdateCheck.unknown());
    }

    @Test
    void theNewestMatchingFileIsAnUpdate() throws Exception {
        modWith("""
                {"gameVersion":"1.21","modLoader":4,"releaseType":1,"fileId":50,"filename":"old.jar"},
                {"gameVersion":"1.21","modLoader":4,"releaseType":2,"fileId":90,"filename":"new-1.2.jar"},
                {"gameVersion":"1.21","modLoader":4,"releaseType":1,"fileId":70,"filename":"middle.jar"}""");

        UpdateCheck c = latest(ContentType.MODS, settings(), "60");

        assertThat(c.status()).isEqualTo(UpdateStatus.UPDATE_AVAILABLE);
        assertThat(c.update()).isEqualTo(new UpdateInfo("90", "new-1.2", "new-1.2.jar", null, null, null, null));
    }

    @Test
    void theCurrentOrAnOlderFileIsUpToDate() throws Exception {
        modWith("{\"gameVersion\":\"1.21\",\"modLoader\":4,\"fileId\":50,\"filename\":\"f.jar\"}");

        assertThat(latest(ContentType.MODS, settings(), "50").status()).isEqualTo(UpdateStatus.UP_TO_DATE);
        assertThat(latest(ContentType.MODS, settings(), "60").status()).isEqualTo(UpdateStatus.UP_TO_DATE);
    }

    @Test
    void filesForOtherVersionsLoadersOrAlphasDontCount() throws Exception {
        modWith("""
                {"gameVersion":"1.20","modLoader":4,"fileId":1,"filename":"wrong-version.jar"},
                {"gameVersion":"1.21","modLoader":1,"fileId":2,"filename":"wrong-loader.jar"},
                {"gameVersion":"1.21","modLoader":4,"releaseType":3,"fileId":3,"filename":"alpha.jar"}""");

        assertThat(latest(ContentType.MODS, settings(), "0"))
                .isEqualTo(new UpdateCheck(UpdateStatus.NO_COMPATIBLE, null));
    }

    @Test
    void theLoaderOnlyMattersForMods() throws Exception {
        modWith("{\"gameVersion\":\"1.21\",\"modLoader\":0,\"fileId\":5,\"filename\":\"pack.zip\"}");

        assertThat(latest(ContentType.RESOURCEPACKS, settings(), "1").status()).isEqualTo(UpdateStatus.UPDATE_AVAILABLE);
    }

    @ParameterizedTest
    @CsvSource({ "forge, 1", "Fabric, 4", "quilt, 5", "neoforge, 6" })
    void loaderNamesMapToCurseForgeIds(String loader, int id) throws Exception {
        modWith("{\"gameVersion\":\"1.21\",\"modLoader\":" + id + ",\"fileId\":5,\"filename\":\"f\"}");

        assertThat(latest(ContentType.MODS, settings(loader, "key"), "1").status())
                .isEqualTo(UpdateStatus.UPDATE_AVAILABLE);
    }

    @Test
    void anUnknownOrMissingLoaderDoesntFilter() throws Exception {
        modWith("{\"gameVersion\":\"1.21\",\"modLoader\":99,\"fileId\":5,\"filename\":\"f.jar\"}");

        assertThat(latest(ContentType.MODS, settings("rift", "key"), "1").status())
                .isEqualTo(UpdateStatus.UPDATE_AVAILABLE);
        assertThat(latest(ContentType.MODS, settings(null, "key"), "1").status())
                .isEqualTo(UpdateStatus.UPDATE_AVAILABLE);
    }

    @Test
    void fileNamesWithoutAnExtensionOrAtAllAreHandled() throws Exception {
        modWith("{\"gameVersion\":\"1.21\",\"modLoader\":4,\"fileId\":5,\"filename\":\"noextension\"}");
        assertThat(latest(ContentType.MODS, settings(), "1").update().versionNumber()).isEqualTo("noextension");

        modWith("{\"gameVersion\":\"1.21\",\"modLoader\":4,\"fileId\":5}");
        assertThat(latest(ContentType.MODS, settings(), "1").update().versionNumber()).isNull();
    }

    @Test
    void modsAreRequestedInBatches() throws Exception {
        List<LocalFile> files = new ArrayList<>();
        Map<String, ProjectInfo> known = new HashMap<>();
        for (int i = 0; i < 101; i++) {
            files.add(file("f" + i, (long) i));
            known.put("f" + i, known(String.valueOf(i), "1"));
        }
        when(http.post(eq(MODS), any(), anyMap())).thenReturn(json("{\"data\":[]}"));

        provider.latest(ContentType.MODS, files, known, settings());

        verify(http, times(2)).post(eq(MODS), any(), anyMap());
    }

    // ---- prepareDownload

    private UpdateInfo prepare(String data) throws Exception {
        when(http.get(eq(MODS + "/1/files/1234567"), anyMap())).thenReturn(json("{\"data\":" + data + "}"));
        return provider.prepareDownload(known("1", "1"),
                new UpdateInfo("1234567", "1.0", "from update.jar", null, null, null, "notes"), settings());
    }

    @Test
    void prepareDownloadUsesTheLinkAndSha1FromTheApi() throws Exception {
        UpdateInfo u = prepare("""
                {"fileName":"real.jar","downloadUrl":"http://dl","hashes":[
                  {"algo":2,"value":"md5hash"},{"algo":1,"value":"sha1hash"}]}""");

        assertThat(u).isEqualTo(new UpdateInfo("1234567", "1.0", "real.jar", "http://dl", "sha1", "sha1hash", "notes"));
    }

    @Test
    void withoutADirectLinkTheCdnIsUsed() throws Exception {
        UpdateInfo u = prepare("{\"fileName\":\"my mod+1.jar\"}");

        assertThat(u.downloadUrl()).isEqualTo("https://edge.forgecdn.net/files/1234/567/my%20mod%2B1.jar");
    }

    @Test
    void theFileNameFallsBackToTheOneFromTheUpdateCheck() throws Exception {
        UpdateInfo u = prepare("{}");

        assertThat(u.fileName()).isEqualTo("from update.jar");
        assertThat(u.downloadUrl()).endsWith("/from%20update.jar");
    }

    @Test
    void onlySha1IsUsedForVerification() throws Exception {
        UpdateInfo u = prepare("{\"downloadUrl\":\"u\",\"hashes\":[{\"algo\":2,\"value\":\"md5hash\"}]}");

        assertThat(u.hash()).isNull();
        assertThat(u.hashAlgo()).isNull();
    }
}
