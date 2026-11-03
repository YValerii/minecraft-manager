package com.mcmanager.settings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.http.HttpStatus;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mcmanager.common.ApiException;
import com.mcmanager.config.AppProperties;
import com.mcmanager.content.ContentType;

class SettingsServiceTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private String osName;
    private String userHome;

    @TempDir
    Path temp;

    @BeforeEach
    void remember() {
        osName = System.getProperty("os.name");
        userHome = System.getProperty("user.home");
    }

    @AfterEach
    void restore() {
        System.setProperty("os.name", osName);
        System.setProperty("user.home", userHome);
    }

    private AppProperties props(String minecraftPath, String gameVersion, String loader, String provider) {
        return new AppProperties(minecraftPath, gameVersion, loader, provider, " key ", 60,
                temp.resolve("data").toString(), null);
    }

    private SettingsService service() {
        return new SettingsService(mapper, props(temp.resolve("mc").toString(), "1.21", "FABRIC", "curseforge"));
    }

    private static SettingsDto dto(String path, String version, String loader, String provider) {
        return new SettingsDto(path, version, loader, provider, null, null, null, null, null, null);
    }

    private static void assertApiError(Runnable call, HttpStatus status, String message) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(ApiException.class, e -> {
            assertThat(e.status()).isEqualTo(status);
            assertThat(e.getMessage()).contains(message);
        });
    }

    @Test
    void firstRunIsSeededFromConfiguration() {
        SettingsDto s = service().get();

        assertThat(s.minecraftPath()).isEqualTo(temp.resolve("mc").toString());
        assertThat(s.gameVersion()).isEqualTo("1.21");
        assertThat(s.loader()).isEqualTo("fabric");
        assertThat(s.provider()).isEqualTo("CURSEFORGE");
        assertThat(s.curseforgeApiKey()).isEqualTo("key");
        assertThat(s.modsDir()).isEqualTo("mods");
        assertThat(s.savesDir()).isEqualTo("saves");
        assertThat(temp.resolve("data/settings.json")).doesNotExist();
    }

    @Test
    void missingConfigurationFallsBackToDefaults() {
        SettingsDto s = new SettingsService(mapper, props(temp.toString(), null, " ", null)).get();

        assertThat(s.gameVersion()).isEqualTo("26.3");
        assertThat(s.loader()).isEqualTo("neoforge");
        assertThat(s.provider()).isEqualTo("MODRINTH");
    }

    @Test
    void valuesAreCachedAfterTheFirstRead() {
        SettingsService service = service();
        assertThat(service.get()).isSameAs(service.get());
    }

    @Test
    void updateNormalizesAndPersists() {
        SettingsService service = service();
        SettingsDto saved = service.update(new SettingsDto(" /mc ", " 1.20 ", " Forge ", " curseforge ", " k ",
                " m ", "", null, " ", "s"));

        assertThat(saved.gameVersion()).isEqualTo("1.20");
        assertThat(saved.loader()).isEqualTo("forge");
        assertThat(saved.provider()).isEqualTo("CURSEFORGE");
        assertThat(saved.curseforgeApiKey()).isEqualTo("k");
        assertThat(saved.minecraftPath()).isEqualTo("/mc");
        assertThat(saved.modsDir()).isEqualTo("m");
        assertThat(saved.resourcepacksDir()).isEqualTo("resourcepacks");
        assertThat(saved.shaderpacksDir()).isEqualTo("shaderpacks");
        assertThat(saved.datapacksDir()).isEqualTo("datapacks");
        assertThat(saved.savesDir()).isEqualTo("s");

        // A new instance reads what was saved, not the configured values.
        assertThat(service().get().gameVersion()).isEqualTo("1.20");
    }

    @Test
    void blankProviderMeansModrinth() {
        assertThat(service().update(dto(null, "1", "l", " ")).provider()).isEqualTo("MODRINTH");
    }

    @Test
    void unknownProviderIsRejected() {
        SettingsService service = service();
        assertApiError(() -> service.update(dto(null, "1", "l", "steam")), HttpStatus.BAD_REQUEST,
                "Unknown source: steam");
    }

    @Test
    void versionAndLoaderAreRequired() {
        SettingsService service = service();
        assertApiError(() -> service.update(dto(null, " ", "l", null)), HttpStatus.BAD_REQUEST, "Minecraft version");
        assertApiError(() -> service.update(dto(null, "1", null, null)), HttpStatus.BAD_REQUEST, "Mod loader");
    }

    @Test
    void aFailedSaveIsReportedAndNothingChanges() throws Exception {
        Path notAFolder = Files.writeString(temp.resolve("file"), "x");
        AppProperties p = new AppProperties(temp.toString(), "1", "l", null, null, 60, notAFolder.toString(), null);
        SettingsService service = new SettingsService(mapper, p);

        assertApiError(() -> service.update(dto(null, "2", "l", null)), HttpStatus.INTERNAL_SERVER_ERROR,
                "Failed to save settings");
        assertThat(service.get().gameVersion()).isEqualTo("1");
    }

    @Test
    void savedSettingsFileWinsOverConfiguration() throws Exception {
        Files.createDirectories(temp.resolve("data"));
        Files.writeString(temp.resolve("data/settings.json"),
                mapper.writeValueAsString(dto(temp.toString(), "9.9", "quilt", "MODRINTH")));

        assertThat(service().get().gameVersion()).isEqualTo("9.9");
    }

    @Test
    void relativeFoldersResolveAgainstTheMinecraftFolder() {
        SettingsService service = service();
        Path root = temp.resolve("mc");
        SettingsDto s = new SettingsDto(root.toString(), "1", "l", "MODRINTH", null,
                "custom/mods", " ", "../outside", temp.resolve("abs").toString(), null);

        assertThat(service.minecraftRoot(s)).isEqualTo(root.toAbsolutePath().normalize());
        assertThat(service.dir(s, ContentType.MODS)).isEqualTo(root.resolve("custom/mods").toAbsolutePath());
        assertThat(service.dir(s, ContentType.RESOURCEPACKS)).isEqualTo(root.resolve("resourcepacks").toAbsolutePath());
        assertThat(service.dir(s, ContentType.SHADERPACKS)).isEqualTo(temp.resolve("outside").toAbsolutePath());
        assertThat(service.dir(s, ContentType.DATAPACKS)).isEqualTo(temp.resolve("abs").toAbsolutePath());
        assertThat(service.savesDir(s)).isEqualTo(root.resolve("saves").toAbsolutePath());
    }

    @ParameterizedTest
    @EnumSource(ContentType.class)
    void everyContentTypeHasADefaultFolder(ContentType type) {
        SettingsDto s = dto(temp.toString(), "1", "l", null);
        assertThat(service().dir(s, type)).isEqualTo(temp.resolve(type.defaultDir()).toAbsolutePath());
    }

    @Test
    void pathsReportsEveryFolderAndWhetherItExists() throws Exception {
        SettingsService service = service();
        Files.createDirectories(temp.resolve("mc/mods"));

        List<SettingsService.PathInfo> paths = service.paths();

        assertThat(paths).extracting(SettingsService.PathInfo::key)
                .containsExactly("root", "mods", "resourcepacks", "shaderpacks", "datapacks", "saves");
        assertThat(paths.get(0).label()).isEqualTo("Minecraft folder");
        assertThat(paths.get(0).exists()).isTrue();
        assertThat(paths.get(1).exists()).isTrue();
        assertThat(paths.get(2).exists()).isFalse();
    }

    @Test
    void blankMinecraftPathIsDetected() {
        SettingsService service = new SettingsService(mapper, props(" ", "1", "l", null));
        Path detected = SettingsService.detectMinecraftPath();

        assertThat(service.get().minecraftPath()).isEqualTo(detected.toString());
        assertThat(service.minecraftRoot(dto(null, "1", "l", null))).isEqualTo(detected);
    }

    @Test
    void detectionPrefersAppData() {
        assertThat(SettingsService.detectMinecraftPath("C:/AppData")).isEqualTo(Path.of("C:/AppData", ".minecraft"));
    }

    @Test
    void detectionOnMacUsesApplicationSupport() {
        System.setProperty("os.name", "Mac OS X");
        System.setProperty("user.home", "/home/me");

        assertThat(SettingsService.detectMinecraftPath(null))
                .isEqualTo(Path.of("/home/me", "Library", "Application Support", "minecraft"));
    }

    @Test
    void detectionOnLinuxUsesTheHomeFolder() {
        System.setProperty("os.name", "Linux");
        System.setProperty("user.home", "/home/me");

        assertThat(SettingsService.detectMinecraftPath(" ")).isEqualTo(Path.of("/home/me", ".minecraft"));
    }
}
