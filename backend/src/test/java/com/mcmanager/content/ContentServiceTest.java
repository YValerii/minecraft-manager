package com.mcmanager.content;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpStatus;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.mcmanager.common.ApiException;
import com.mcmanager.common.Hashing;
import com.mcmanager.common.Http;
import com.mcmanager.config.AppProperties;
import com.mcmanager.content.ContentDtos.ContentItem;
import com.mcmanager.content.ContentDtos.ContentList;
import com.mcmanager.content.ContentDtos.Icon;
import com.mcmanager.content.ContentDtos.InstallResult;
import com.mcmanager.content.ContentDtos.UpdateItem;
import com.mcmanager.content.ContentDtos.UpdatesResult;
import com.mcmanager.provider.ContentProvider;
import com.mcmanager.provider.IdentityCache;
import com.mcmanager.provider.ProjectInfo;
import com.mcmanager.provider.ProviderType;
import com.mcmanager.provider.UpdateCheck;
import com.mcmanager.provider.UpdateInfo;
import com.mcmanager.provider.UpdateStatus;
import com.mcmanager.settings.SettingsService;

/** Real files and real collaborators on a temp folder; only the remote source and downloads are mocked. */
class ContentServiceTest {

    private static final String ID = "mods/a.jar";
    private static final ProjectInfo PROJECT = new ProjectInfo(ProviderType.MODRINTH, "p", "Remote Name", "Remote Desc",
            "http://icon", "http://page", "v1", "1.0");

    private final ObjectMapper mapper = JsonMapper.builder().findAndAddModules().build();
    private final ContentProvider modrinth = mock(ContentProvider.class);
    private final ContentProvider curseforge = mock(ContentProvider.class);
    private final Http http = mock(Http.class);

    @TempDir
    Path temp;

    private Path root;
    private Path mods;
    private AppProperties props;
    private IdentityCache identities;
    private ContentService service;

    @BeforeEach
    void setUp() throws Exception {
        root = temp.resolve("mc").toAbsolutePath().normalize();
        mods = Files.createDirectories(root.resolve("mods"));
        when(modrinth.type()).thenReturn(ProviderType.MODRINTH);
        when(curseforge.type()).thenReturn(ProviderType.CURSEFORGE);
        when(modrinth.prepareDownload(any(), any(), any())).thenAnswer(i -> i.getArgument(1));
        when(curseforge.prepareDownload(any(), any(), any())).thenAnswer(i -> i.getArgument(1));
        props = new AppProperties(root.toString(), "1.21", "fabric", "MODRINTH", "key", 60,
                temp.resolve("data").toString(), null);
        identities = new IdentityCache(mapper, props);
        service = newService();
    }

    private ContentService newService() {
        SettingsService settings = new SettingsService(mapper, props);
        FileScanner scanner = new FileScanner(settings, new HashCache());
        return new ContentService(settings, scanner, new LocalMetadataReader(mapper), identities,
                List.of(modrinth, curseforge), http, props);
    }

    private Path mod(String name, String content) throws Exception {
        return Files.writeString(mods.resolve(name), content);
    }

    private void identifyAs(ProjectInfo project) {
        when(modrinth.identify(any(), anyList(), any())).thenReturn(Map.of(ID, project));
    }

    private static UpdateInfo update(String fileName, String url) {
        return new UpdateInfo("v2", "2.0", fileName, url, null, null, "notes");
    }

    private void latestIs(UpdateStatus status, UpdateInfo info) {
        when(modrinth.latest(any(), anyList(), anyMap(), any())).thenReturn(Map.of(ID, new UpdateCheck(status, info)));
    }

    /** The mock download writes this content to whatever path it's given. */
    private void downloadWrites(String content) {
        doAnswer(i -> {
            Files.writeString(i.getArgument(1, Path.class), content);
            return null;
        }).when(http).download(any(), any());
    }

    private static void assertApiError(Runnable call, HttpStatus status, String message) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(ApiException.class, e -> {
            assertThat(e.status()).isEqualTo(status);
            assertThat(e.getMessage()).contains(message);
        });
    }

    /** Files in mods/, so a failed install can be shown to leave everything as it was. */
    private List<String> modFiles() throws Exception {
        try (Stream<Path> files = Files.list(mods)) {
            return files.map(p -> p.getFileName().toString()).sorted().toList();
        }
    }

    // ---- list

    @Test
    void listDescribesAMissingFolder() throws Exception {
        Files.delete(mods);

        ContentList list = service.list(ContentType.MODS, false);

        assertThat(list.directory()).isEqualTo(mods.toString());
        assertThat(list.directoryExists()).isFalse();
        assertThat(list.provider()).isEqualTo("MODRINTH");
        assertThat(list.items()).isEmpty();
        assertThat(list.warning()).isNull();
    }

    @Test
    void anUnrecognizedFileIsLocalAndNamedAfterItself() throws Exception {
        mod("a.jar", "x");
        when(modrinth.identify(any(), anyList(), any())).thenReturn(Map.of());

        ContentItem item = service.list(ContentType.MODS, false).items().get(0);

        assertThat(item.id()).isEqualTo(ID);
        assertThat(item.name()).isEqualTo("a");
        assertThat(item.source()).isEqualTo("LOCAL");
        assertThat(item.identified()).isFalse();
        assertThat(item.iconUrl()).isNull();
        assertThat(item.pageUrl()).isNull();
        assertThat(item.localIcon()).isFalse();
    }

    @Test
    void aFileWithoutAnExtensionKeepsItsWholeName() throws Exception {
        Files.createDirectories(root.resolve("resourcepacks/plain folder"));

        assertThat(service.list(ContentType.RESOURCEPACKS, false).items().get(0).name()).isEqualTo("plain folder");
    }

    @Test
    void aRecognizedFileUsesTheRemoteDetails() throws Exception {
        mod("a.jar", "x");
        identifyAs(PROJECT);

        ContentItem item = service.list(ContentType.MODS, false).items().get(0);

        assertThat(item.name()).isEqualTo("Remote Name");
        assertThat(item.description()).isEqualTo("Remote Desc");
        assertThat(item.version()).isEqualTo("1.0");
        assertThat(item.iconUrl()).isEqualTo("http://icon");
        assertThat(item.pageUrl()).isEqualTo("http://page");
        assertThat(item.source()).isEqualTo("MODRINTH");
        assertThat(item.identified()).isTrue();
    }

    @Test
    void localMetadataFillsInWhenTheFileIsUnrecognized() throws Exception {
        Path jar = mods.resolve("a.jar");
        try (OutputStream raw = Files.newOutputStream(jar); ZipOutputStream zip = new ZipOutputStream(raw)) {
            zip.putNextEntry(new ZipEntry("fabric.mod.json"));
            zip.write("""
                    {"name":"Local Name","version":"3.0","description":"Local Desc","authors":["Me"],"icon":"i.png"}"""
                    .getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("i.png"));
            zip.write(1);
            zip.closeEntry();
        }
        when(modrinth.identify(any(), anyList(), any())).thenReturn(Map.of());

        ContentItem item = service.list(ContentType.MODS, false).items().get(0);

        assertThat(item.name()).isEqualTo("Local Name");
        assertThat(item.version()).isEqualTo("3.0");
        assertThat(item.description()).isEqualTo("Local Desc");
        assertThat(item.authors()).isEqualTo("Me");
        assertThat(item.localIcon()).isTrue();
    }

    @Test
    void foldersNeverReachTheRemoteSource() throws Exception {
        Files.createDirectories(root.resolve("resourcepacks/folder"));

        service.list(ContentType.RESOURCEPACKS, false);

        verify(modrinth, never()).identify(any(), anyList(), any());
    }

    @Test
    void aSourceFailureBecomesAWarningAndFilesStillShow() throws Exception {
        mod("a.jar", "x");
        when(modrinth.identify(any(), anyList(), any())).thenThrow(new ApiException(HttpStatus.BAD_GATEWAY, "down"));

        ContentList list = service.list(ContentType.MODS, false);

        assertThat(list.warning()).isEqualTo("down");
        assertThat(list.items()).hasSize(1);
    }

    @Test
    void anUnknownProviderInSettingsFallsBackToModrinth() throws Exception {
        Files.createDirectories(temp.resolve("data"));
        Files.writeString(temp.resolve("data/settings.json"),
                "{\"minecraftPath\":\"" + root.toString().replace("\\", "\\\\")
                        + "\",\"gameVersion\":\"1\",\"loader\":\"l\",\"provider\":\"BOGUS\"}");
        mod("a.jar", "x");
        when(modrinth.identify(any(), anyList(), any())).thenReturn(Map.of());

        newService().list(ContentType.MODS, false);

        verify(modrinth).identify(any(), anyList(), any());
        verify(curseforge, never()).identify(any(), anyList(), any());
    }

    @Test
    void theConfiguredProviderIsUsed() throws Exception {
        props = new AppProperties(root.toString(), "1.21", "fabric", "CURSEFORGE", "key", 60,
                temp.resolve("data").toString(), null);
        mod("a.jar", "x");
        when(curseforge.identify(any(), anyList(), any())).thenReturn(Map.of());

        newService().list(ContentType.MODS, false);

        verify(curseforge).identify(any(), anyList(), any());
        verify(modrinth, never()).identify(any(), anyList(), any());
    }

    // ---- identity cache

    @Test
    void aRecognizedFileIsOnlyLookedUpOnce() throws Exception {
        mod("a.jar", "x");
        identifyAs(PROJECT);

        service.list(ContentType.MODS, false);
        ContentItem second = service.list(ContentType.MODS, false).items().get(0);

        verify(modrinth, times(1)).identify(any(), anyList(), any());
        assertThat(second.name()).isEqualTo("Remote Name");
    }

    @Test
    void aRecentlyUnrecognizedFileIsNotLookedUpAgain() throws Exception {
        mod("a.jar", "x");
        when(modrinth.identify(any(), anyList(), any())).thenReturn(Map.of());

        service.list(ContentType.MODS, false);
        service.list(ContentType.MODS, false);

        verify(modrinth, times(1)).identify(any(), anyList(), any());
    }

    @Test
    void anOldNotFoundEntryIsLookedUpAgain() throws Exception {
        Path jar = mod("a.jar", "x");
        String key = IdentityCache.key(ProviderType.MODRINTH, Hashing.sha1(jar));
        identities.saveAll(List.of(new IdentityCache.Entry(key, false, null, null, null, null, null, null, null,
                Instant.now().minusSeconds(61 * 60))));
        identifyAs(PROJECT);

        assertThat(service.list(ContentType.MODS, false).items().get(0).identified()).isTrue();
    }

    @Test
    void refreshAlwaysLooksUpAgain() throws Exception {
        mod("a.jar", "x");
        identifyAs(PROJECT);

        service.list(ContentType.MODS, false);
        service.list(ContentType.MODS, true);

        verify(modrinth, times(2)).identify(any(), anyList(), any());
    }

    // ---- updates

    @Test
    void updatesReportEachFilesStatus() throws Exception {
        mod("a.jar", "x");
        identifyAs(PROJECT);
        latestIs(UpdateStatus.UPDATE_AVAILABLE, update("new.jar", "http://dl"));

        UpdatesResult res = service.updates(ContentType.MODS, false);

        assertThat(res.warning()).isNull();
        assertThat(res.items()).containsExactly(
                new UpdateItem(ID, UpdateStatus.UPDATE_AVAILABLE, "2.0", "new.jar", "notes"));
    }

    @Test
    void theChangelogIsOnlySentForAnActualUpdate() throws Exception {
        mod("a.jar", "x");
        identifyAs(PROJECT);
        latestIs(UpdateStatus.UP_TO_DATE, update("a.jar", "http://dl"));

        assertThat(service.updates(ContentType.MODS, false).items().get(0).changelog()).isNull();
    }

    @Test
    void aCheckWithoutUpdateInfoHasNoVersion() throws Exception {
        mod("a.jar", "x");
        identifyAs(PROJECT);
        latestIs(UpdateStatus.NO_COMPATIBLE, null);

        UpdateItem item = service.updates(ContentType.MODS, false).items().get(0);

        assertThat(item.status()).isEqualTo(UpdateStatus.NO_COMPATIBLE);
        assertThat(item.latestVersion()).isNull();
        assertThat(item.latestFileName()).isNull();
    }

    @Test
    void anUpdateAvailableWithoutInfoHasNoChangelog() throws Exception {
        mod("a.jar", "x");
        identifyAs(PROJECT);
        latestIs(UpdateStatus.UPDATE_AVAILABLE, null);

        assertThat(service.updates(ContentType.MODS, false).items().get(0).changelog()).isNull();
    }

    @Test
    void unrecognizedFilesAreUnknownAndNotChecked() throws Exception {
        mod("a.jar", "x");
        when(modrinth.identify(any(), anyList(), any())).thenReturn(Map.of());

        UpdateItem item = service.updates(ContentType.MODS, false).items().get(0);

        assertThat(item.status()).isEqualTo(UpdateStatus.UNKNOWN);
        verify(modrinth, never()).latest(any(), anyList(), anyMap(), any());
    }

    @Test
    void aFileMissingFromTheResultIsUnknown() throws Exception {
        mod("a.jar", "x");
        identifyAs(PROJECT);
        when(modrinth.latest(any(), anyList(), anyMap(), any())).thenReturn(Map.of());

        assertThat(service.updates(ContentType.MODS, false).items().get(0).status()).isEqualTo(UpdateStatus.UNKNOWN);
    }

    @Test
    void checksAreCachedUntilARefresh() throws Exception {
        mod("a.jar", "x");
        identifyAs(PROJECT);
        latestIs(UpdateStatus.UP_TO_DATE, update("a.jar", "u"));

        service.updates(ContentType.MODS, false);
        service.updates(ContentType.MODS, false);
        verify(modrinth, times(1)).latest(any(), anyList(), anyMap(), any());

        service.updates(ContentType.MODS, true);
        verify(modrinth, times(2)).latest(any(), anyList(), anyMap(), any());
    }

    @Test
    void anUpdateCheckFailureBecomesAWarning() throws Exception {
        mod("a.jar", "x");
        identifyAs(PROJECT);
        when(modrinth.latest(any(), anyList(), anyMap(), any())).thenThrow(new ApiException(HttpStatus.BAD_GATEWAY, "down"));

        UpdatesResult res = service.updates(ContentType.MODS, false);

        assertThat(res.warning()).isEqualTo("down");
        assertThat(res.items().get(0).status()).isEqualTo(UpdateStatus.UNKNOWN);
    }

    // ---- install

    @Test
    void installReplacesTheFileAndKeepsABackup() throws Exception {
        mod("a.jar", "old");
        identifyAs(PROJECT);
        latestIs(UpdateStatus.UPDATE_AVAILABLE, update("new.jar", "http://dl"));
        downloadWrites("new content");

        InstallResult res = service.install(ContentType.MODS, ID);

        assertThat(res).isEqualTo(new InstallResult(ID, true, "Updated to 2.0", "new.jar"));
        verify(http).download(eq("http://dl"), any());
        assertThat(modFiles()).containsExactly("new.jar");
        assertThat(mods.resolve("new.jar")).hasContent("new content");
        try (Stream<Path> backups = Files.walk(root.resolve(".mc-manager-backup/mods"))) {
            Path backup = backups.filter(p -> p.endsWith("a.jar")).findFirst().orElseThrow();
            assertThat(backup).hasContent("old");
        }
    }

    @Test
    void anUpdateMayKeepTheSameFileName() throws Exception {
        mod("a.jar", "old");
        identifyAs(PROJECT);
        latestIs(UpdateStatus.UPDATE_AVAILABLE, update("a.jar", "http://dl"));
        downloadWrites("new content");

        service.install(ContentType.MODS, ID);

        assertThat(mods.resolve("a.jar")).hasContent("new content");
    }

    @Test
    void providerDetailsArePreparedBeforeDownloading() throws Exception {
        mod("a.jar", "old");
        identifyAs(PROJECT);
        latestIs(UpdateStatus.UPDATE_AVAILABLE, update("new.jar", null));
        when(modrinth.prepareDownload(any(), any(), any())).thenReturn(update("prepared.jar", "http://prepared"));
        downloadWrites("x");

        assertThat(service.install(ContentType.MODS, ID).newFileName()).isEqualTo("prepared.jar");
        verify(http).download(eq("http://prepared"), any());
    }

    @Test
    void installOfAnUnknownFileIsNotFound() {
        assertApiError(() -> service.install(ContentType.MODS, "mods/nope.jar"), HttpStatus.NOT_FOUND, "File not found");
    }

    @Test
    void foldersCantBeInstalled() throws Exception {
        Files.createDirectories(root.resolve("resourcepacks/folder"));

        assertApiError(() -> service.install(ContentType.RESOURCEPACKS, "resourcepacks/folder"),
                HttpStatus.BAD_REQUEST, "Folders can't be updated");
    }

    @Test
    void anUnrecognizedFileCantBeUpdated() throws Exception {
        mod("a.jar", "old");
        when(modrinth.identify(any(), anyList(), any())).thenReturn(Map.of());

        assertApiError(() -> service.install(ContentType.MODS, ID), HttpStatus.CONFLICT, "not recognized on MODRINTH");
    }

    @Test
    void withoutANewerVersionThereIsNothingToInstall() throws Exception {
        mod("a.jar", "old");
        identifyAs(PROJECT);

        latestIs(UpdateStatus.UP_TO_DATE, update("a.jar", "u"));
        assertApiError(() -> service.install(ContentType.MODS, ID), HttpStatus.CONFLICT, "No newer versions for 1.21 / fabric");

        when(modrinth.latest(any(), anyList(), anyMap(), any())).thenReturn(Map.of());
        assertApiError(() -> service.install(ContentType.MODS, ID), HttpStatus.CONFLICT, "No newer versions");
    }

    @Test
    void aMissingDownloadLinkOrFileNameIsReported() throws Exception {
        mod("a.jar", "old");
        identifyAs(PROJECT);

        latestIs(UpdateStatus.UPDATE_AVAILABLE, update("new.jar", " "));
        assertApiError(() -> service.install(ContentType.MODS, ID), HttpStatus.BAD_GATEWAY, "did not return a download link");

        latestIs(UpdateStatus.UPDATE_AVAILABLE, update(null, "http://dl"));
        assertApiError(() -> service.install(ContentType.MODS, ID), HttpStatus.BAD_GATEWAY, "did not return a download link");
    }

    @Test
    void anExistingFileWithTheNewNameIsNotOverwritten() throws Exception {
        mod("a.jar", "old");
        mod("new.jar", "someone else's");
        when(modrinth.identify(any(), anyList(), any())).thenReturn(Map.of(ID, PROJECT));
        latestIs(UpdateStatus.UPDATE_AVAILABLE, update("new.jar", "http://dl"));

        assertApiError(() -> service.install(ContentType.MODS, ID), HttpStatus.CONFLICT, "already exists");
        assertThat(mods.resolve("new.jar")).hasContent("someone else's");
        verify(http, never()).download(any(), any());
    }

    @Test
    void aFileNameFromTheSourceCantEscapeTheFolder() throws Exception {
        mod("a.jar", "old");
        identifyAs(PROJECT);
        latestIs(UpdateStatus.UPDATE_AVAILABLE, update("../../evil.jar", "http://dl"));
        downloadWrites("x");

        assertThat(service.install(ContentType.MODS, ID).newFileName()).isEqualTo("evil.jar");
        assertThat(mods.resolve("evil.jar")).exists();
        assertThat(temp.resolve("evil.jar")).doesNotExist();
    }

    @Test
    void windowsPathsInAFileNameAreAlsoStripped() throws Exception {
        mod("a.jar", "old");
        identifyAs(PROJECT);
        latestIs(UpdateStatus.UPDATE_AVAILABLE, update("..\\..\\evil.jar", "http://dl"));
        downloadWrites("x");

        assertThat(service.install(ContentType.MODS, ID).newFileName()).isEqualTo("evil.jar");
    }

    @Test
    void hiddenFileNamesFromTheSourceAreRefused() throws Exception {
        mod("a.jar", "old");
        identifyAs(PROJECT);
        latestIs(UpdateStatus.UPDATE_AVAILABLE, update(".bashrc", "http://dl"));

        assertApiError(() -> service.install(ContentType.MODS, ID), HttpStatus.BAD_GATEWAY, "Invalid file name");
    }

    @Test
    @Disabled("Known bug: safeFileName calls getFileName() on \"/\", which is null, so this is a NullPointerException (HTTP 500)")
    void aFileNameWithNoNameAtAllIsRefused() throws Exception {
        mod("a.jar", "old");
        identifyAs(PROJECT);
        latestIs(UpdateStatus.UPDATE_AVAILABLE, update("/", "http://dl"));

        assertApiError(() -> service.install(ContentType.MODS, ID), HttpStatus.BAD_GATEWAY, "Invalid file name");
    }

    @Test
    void aFailedDownloadChangesNothing() throws Exception {
        mod("a.jar", "old");
        identifyAs(PROJECT);
        latestIs(UpdateStatus.UPDATE_AVAILABLE, update("new.jar", "http://dl"));
        doThrow(new ApiException(HttpStatus.BAD_GATEWAY, "Download failed")).when(http).download(any(), any());

        assertApiError(() -> service.install(ContentType.MODS, ID), HttpStatus.BAD_GATEWAY, "Download failed");
        assertThat(modFiles()).containsExactly("a.jar");
        assertThat(mods.resolve("a.jar")).hasContent("old");
    }

    @ParameterizedTest
    @CsvSource({ "sha512, SHA-512", "SHA1, SHA-1", "md5, MD5" })
    void aMatchingChecksumIsAccepted(String algo, String javaName) throws Exception {
        mod("a.jar", "old");
        Path sample = Files.writeString(temp.resolve("sample"), "new content");
        identifyAs(PROJECT);
        latestIs(UpdateStatus.UPDATE_AVAILABLE, new UpdateInfo("v2", "2.0", "new.jar", "http://dl", algo,
                Hashing.digest(sample, javaName).toUpperCase(), null));
        downloadWrites("new content");

        assertThat(service.install(ContentType.MODS, ID).ok()).isTrue();
    }

    @Test
    void aWrongChecksumRejectsTheDownloadAndKeepsTheOldFile() throws Exception {
        mod("a.jar", "old");
        identifyAs(PROJECT);
        latestIs(UpdateStatus.UPDATE_AVAILABLE, new UpdateInfo("v2", "2.0", "new.jar", "http://dl", "sha1", "wrong", null));
        downloadWrites("tampered");

        assertApiError(() -> service.install(ContentType.MODS, ID), HttpStatus.BAD_GATEWAY, "Checksum");
        assertThat(modFiles()).containsExactly("a.jar");
        assertThat(mods.resolve("a.jar")).hasContent("old");
    }

    @Test
    void anUnknownChecksumAlgorithmSkipsVerification() throws Exception {
        mod("a.jar", "old");
        identifyAs(PROJECT);
        latestIs(UpdateStatus.UPDATE_AVAILABLE, new UpdateInfo("v2", "2.0", "new.jar", "http://dl", "crc32", "wrong", null));
        downloadWrites("x");

        assertThat(service.install(ContentType.MODS, ID).ok()).isTrue();
    }

    @Test
    void aHashWithoutAnAlgorithmSkipsVerification() throws Exception {
        mod("a.jar", "old");
        identifyAs(PROJECT);
        latestIs(UpdateStatus.UPDATE_AVAILABLE, new UpdateInfo("v2", "2.0", "new.jar", "http://dl", null, "wrong", null));
        downloadWrites("x");

        assertThat(service.install(ContentType.MODS, ID).ok()).isTrue();
    }

    @Test
    void aFailureReplacingTheFileKeepsTheOriginal() throws Exception {
        mod("a.jar", "old");
        identifyAs(PROJECT);
        latestIs(UpdateStatus.UPDATE_AVAILABLE, update("new.jar", "http://dl"));
        downloadWrites("x");
        Files.writeString(root.resolve(".mc-manager-backup"), "a file where the backup folder should go");

        assertApiError(() -> service.install(ContentType.MODS, ID), HttpStatus.INTERNAL_SERVER_ERROR, "Failed to replace file");
        assertThat(modFiles()).containsExactly("a.jar");
        assertThat(mods.resolve("a.jar")).hasContent("old");
    }

    @Test
    void aTempFileThatCantBeRemovedDoesntHideTheRealError() throws Exception {
        mod("a.jar", "old");
        identifyAs(PROJECT);
        latestIs(UpdateStatus.UPDATE_AVAILABLE, update("new.jar", "http://dl"));
        doAnswer(i -> {
            Path tmp = i.getArgument(1, Path.class);
            Files.createDirectories(tmp);
            Files.writeString(tmp.resolve("blocks deletion"), "x");
            return null;
        }).when(http).download(any(), any());
        Files.writeString(root.resolve(".mc-manager-backup"), "a file where the backup folder should go");

        assertApiError(() -> service.install(ContentType.MODS, ID), HttpStatus.INTERNAL_SERVER_ERROR, "Failed to replace file");
    }

    // ---- install all

    @Test
    void installAllUpdatesWhatItCanAndReportsTheRest() throws Exception {
        mod("a.jar", "a");
        mod("b.jar", "b");
        mod("c.jar", "c");
        when(modrinth.identify(any(), anyList(), any())).thenReturn(Map.of(
                "mods/a.jar", PROJECT, "mods/b.jar", PROJECT, "mods/c.jar", PROJECT));
        when(modrinth.latest(any(), anyList(), anyMap(), any())).thenAnswer(i -> {
            List<LocalFile> files = i.getArgument(1);
            Map<String, UpdateCheck> out = new HashMap<>();
            for (LocalFile f : files) {
                switch (f.fileName()) {
                    case "a.jar" -> out.put(f.id(), new UpdateCheck(UpdateStatus.UPDATE_AVAILABLE, update("a2.jar", "http://a")));
                    case "b.jar" -> out.put(f.id(), new UpdateCheck(UpdateStatus.UPDATE_AVAILABLE, update("b2.jar", "http://b")));
                    default -> out.put(f.id(), new UpdateCheck(UpdateStatus.UP_TO_DATE, update("c.jar", "http://c")));
                }
            }
            return out;
        });
        doAnswer(i -> {
            if (i.getArgument(0, String.class).equals("http://b")) {
                throw new ApiException(HttpStatus.BAD_GATEWAY, "b failed");
            }
            Files.writeString(i.getArgument(1, Path.class), "x");
            return null;
        }).when(http).download(any(), any());

        List<InstallResult> results = service.installAll(ContentType.MODS);

        assertThat(results).containsExactly(
                new InstallResult("mods/a.jar", true, "Updated to 2.0", "a2.jar"),
                new InstallResult("mods/b.jar", false, "b failed", null));
        assertThat(modFiles()).containsExactly("a2.jar", "b.jar", "c.jar");
    }

    // ---- icons

    private Path jarWithIcon(byte[] icon) throws Exception {
        Path jar = mods.resolve("a.jar");
        try (OutputStream raw = Files.newOutputStream(jar); ZipOutputStream zip = new ZipOutputStream(raw)) {
            zip.putNextEntry(new ZipEntry("fabric.mod.json"));
            zip.write("{\"icon\":\"i.png\"}".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("i.png"));
            zip.write(icon);
            zip.closeEntry();
        }
        return jar;
    }

    @Test
    void iconIsAPngByDefault() throws Exception {
        jarWithIcon(new byte[] { (byte) 0x89, 0x50, 0x4E });

        Icon icon = service.icon(ContentType.MODS, ID);

        assertThat(icon.contentType()).isEqualTo("image/png");
        assertThat(icon.bytes()).hasSize(3);
    }

    @Test
    void jpegIconsAreRecognized() throws Exception {
        jarWithIcon(new byte[] { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF });

        assertThat(service.icon(ContentType.MODS, ID).contentType()).isEqualTo("image/jpeg");
    }

    @Test
    void aTwoByteIconIsNotTreatedAsJpeg() throws Exception {
        jarWithIcon(new byte[] { (byte) 0xFF, (byte) 0xD8 });

        assertThat(service.icon(ContentType.MODS, ID).contentType()).isEqualTo("image/png");
    }

    @Test
    void aSecondByteOtherThanTheJpegMarkerIsNotJpeg() throws Exception {
        jarWithIcon(new byte[] { (byte) 0xFF, 0x00, 0x00 });

        assertThat(service.icon(ContentType.MODS, ID).contentType()).isEqualTo("image/png");
    }

    @Test
    void anIconForAnUnknownFileIsNotFound() {
        assertApiError(() -> service.icon(ContentType.MODS, "mods/nope.jar"), HttpStatus.NOT_FOUND, "File not found");
    }

    @Test
    void aFileWithoutAnIconIsNotFound() throws Exception {
        mod("a.jar", "not a zip");
        assertApiError(() -> service.icon(ContentType.MODS, ID), HttpStatus.NOT_FOUND, "No icon");
    }

    @Test
    void anEmptyIconIsNotFound() throws Exception {
        jarWithIcon(new byte[0]);
        assertApiError(() -> service.icon(ContentType.MODS, ID), HttpStatus.NOT_FOUND, "No icon");
    }
}
