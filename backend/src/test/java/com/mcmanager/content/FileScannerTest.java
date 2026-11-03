package com.mcmanager.content;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mcmanager.config.AppProperties;
import com.mcmanager.settings.SettingsDto;
import com.mcmanager.settings.SettingsService;

class FileScannerTest {

    @TempDir
    Path temp;

    private Path root;
    private SettingsDto settings;
    private FileScanner scanner;

    @BeforeEach
    void setUp() throws Exception {
        root = temp.resolve("mc").toAbsolutePath().normalize();
        Files.createDirectories(root);
        AppProperties props = new AppProperties(root.toString(), "1.21", "fabric", "MODRINTH", null, 60,
                temp.resolve("data").toString(), null);
        SettingsService service = new SettingsService(new ObjectMapper(), props);
        settings = service.get();
        scanner = new FileScanner(service, new HashCache());
    }

    private Path write(String relative, String content) throws Exception {
        Path p = root.resolve(relative);
        Files.createDirectories(p.getParent());
        return Files.writeString(p, content);
    }

    private List<String> ids(ContentType type) {
        return scanner.scan(type, settings).stream().map(LocalFile::id).toList();
    }

    @Test
    void aMissingFolderIsEmpty() {
        assertThat(scanner.scan(ContentType.MODS, settings)).isEmpty();
    }

    @Test
    void modsAreJarFilesOnly() throws Exception {
        write("mods/a.jar", "a");
        write("mods/UPPER.JAR", "b");
        write("mods/readme.txt", "c");
        write("mods/pack.zip", "d");
        write("mods/.hidden.jar", "e");
        Files.createDirectories(root.resolve("mods/folder.jar"));

        assertThat(ids(ContentType.MODS)).containsExactly("mods/a.jar", "mods/UPPER.JAR");
    }

    @Test
    void packsAreZipFilesOrFolders() throws Exception {
        write("resourcepacks/a.zip", "a");
        write("resourcepacks/b.jar", "b");
        Files.createDirectories(root.resolve("resourcepacks/folder-pack"));
        Files.createDirectories(root.resolve("resourcepacks/.hidden"));

        assertThat(ids(ContentType.RESOURCEPACKS)).containsExactly("resourcepacks/a.zip", "resourcepacks/folder-pack");
    }

    @Test
    void shaderpacksAreScannedInTheirFolder() throws Exception {
        write("shaderpacks/s.zip", "a");
        assertThat(ids(ContentType.SHADERPACKS)).containsExactly("shaderpacks/s.zip");
    }

    @Test
    void aFileCarriesItsDetails() throws Exception {
        Path f = write("mods/a.jar", "abc");

        LocalFile file = scanner.scan(ContentType.MODS, settings).get(0);

        assertThat(file.fileName()).isEqualTo("a.jar");
        assertThat(file.path()).isEqualTo(f);
        assertThat(file.directory()).isFalse();
        assertThat(file.size()).isEqualTo(3);
        assertThat(file.modified()).isEqualTo(Files.getLastModifiedTime(f).toMillis());
        assertThat(file.location()).isNull();
        assertThat(file.sha1()).isEqualTo("a9993e364706816aba3e25717850c26c9cd0d89d");
        assertThat(file.fingerprint().get()).isNotNull();
    }

    @Test
    void aFolderHasNoSizeOrHashes() throws Exception {
        Files.createDirectories(root.resolve("resourcepacks/pack"));
        Files.writeString(root.resolve("resourcepacks/pack/pack.mcmeta"), "{}");

        LocalFile folder = scanner.scan(ContentType.RESOURCEPACKS, settings).get(0);

        assertThat(folder.directory()).isTrue();
        assertThat(folder.size()).isZero();
        assertThat(folder.sha1()).isNull();
        assertThat(folder.fingerprint().get()).isNull();
    }

    @Test
    void datapacksAlsoComeFromEachWorld() throws Exception {
        write("datapacks/global.zip", "a");
        write("saves/World B/datapacks/b.zip", "b");
        write("saves/world a/datapacks/a2.zip", "c");
        write("saves/world a/datapacks/a1.zip", "d");
        Files.createDirectories(root.resolve("saves/no-datapacks"));
        write("saves/file.txt", "not a world");

        List<LocalFile> files = scanner.scan(ContentType.DATAPACKS, settings);

        assertThat(files).extracting(LocalFile::id).containsExactly(
                "datapacks/global.zip",
                "saves/world a/datapacks/a1.zip",
                "saves/world a/datapacks/a2.zip",
                "saves/World B/datapacks/b.zip");
        assertThat(files).extracting(LocalFile::location).containsExactly(null, "world a", "world a", "World B");
    }

    @Test
    void otherTypesIgnoreWorldFolders() throws Exception {
        write("saves/w/datapacks/a.zip", "a");
        assertThat(ids(ContentType.RESOURCEPACKS)).isEmpty();
    }

    @Test
    void aFolderOutsideMinecraftUsesItsAbsolutePathAsTheId() throws Exception {
        Path outside = temp.resolve("elsewhere");
        Files.createDirectories(outside);
        Path mod = Files.writeString(outside.resolve("x.jar"), "x");
        SettingsDto custom = new SettingsDto(root.toString(), "1.21", "fabric", "MODRINTH", null, outside.toString(),
                null, null, null, null);

        LocalFile file = scanner.scan(ContentType.MODS, custom).get(0);

        assertThat(file.id()).isEqualTo(mod.toAbsolutePath().normalize().toString().replace('\\', '/'));
    }
}
