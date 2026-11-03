package com.mcmanager.content;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mcmanager.content.LocalMetadataReader.Meta;

class LocalMetadataReaderTest {

    private final LocalMetadataReader reader = new LocalMetadataReader(new ObjectMapper());
    private int counter;

    @TempDir
    Path dir;

    private static Map<String, Object> entries(Object... pairs) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            m.put((String) pairs[i], pairs[i + 1]);
        }
        return m;
    }

    /** A .jar with the given entries; values are text or bytes. A name ending in "/" is a folder. */
    private LocalFile zip(Map<String, Object> content) throws Exception {
        Path file = dir.resolve("file" + (counter++) + ".jar");
        try (OutputStream raw = Files.newOutputStream(file); ZipOutputStream zip = new ZipOutputStream(raw)) {
            for (Map.Entry<String, Object> e : content.entrySet()) {
                zip.putNextEntry(new ZipEntry(e.getKey()));
                Object v = e.getValue();
                zip.write(v instanceof byte[] b ? b : v.toString().getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
        return local(file, false);
    }

    private static LocalFile local(Path file, boolean directory) throws Exception {
        return new LocalFile(file.getFileName().toString(), file.getFileName().toString(), file, directory,
                directory ? 0 : Files.size(file), 1, null, null, () -> null);
    }

    private Meta mod(Object... pairs) throws Exception {
        return reader.read(ContentType.MODS, zip(entries(pairs)));
    }

    private Meta pack(Object... pairs) throws Exception {
        return reader.read(ContentType.RESOURCEPACKS, zip(entries(pairs)));
    }

    // ---- neoforge / forge

    @Test
    void readsNeoForgeToml() throws Exception {
        Meta m = mod("META-INF/neoforge.mods.toml", """
                logoFile="logo.png"
                authors="Root Author"
                [[mods]]
                modId="x"
                displayName="My Mod"
                version="1.2.3"
                description='''
                §aHello
                  world
                '''
                """, "logo.png", "png");

        assertThat(m).isEqualTo(new Meta("My Mod", "1.2.3", "Hello world", "Root Author", "logo.png"));
    }

    @Test
    void readsForgeTomlWithValuesOnTheMod() throws Exception {
        Meta m = mod("META-INF/mods.toml", """
                logoFile="root.png"
                authors="Root"
                [[mods]]
                displayName="Forge Mod"
                authors="Mod Author"
                logoFile="/mod.png"
                """, "mod.png", "png", "root.png", "png");

        assertThat(m.authors()).isEqualTo("Mod Author");
        assertThat(m.iconEntry()).isEqualTo("mod.png");
    }

    @Test
    void aPlaceholderVersionComesFromTheManifest() throws Exception {
        Meta m = mod(
                "META-INF/MANIFEST.MF", "Manifest-Version: 1.0\r\nImplementation-Version: 4.5.6\r\n\r\n",
                "META-INF/mods.toml", "[[mods]]\ndisplayName=\"X\"\nversion=\"${file.jarVersion}\"\n");

        assertThat(m.version()).isEqualTo("4.5.6");
    }

    @Test
    void aManifestWithAPlaceholderGivesNoVersion() throws Exception {
        Meta m = mod(
                "META-INF/MANIFEST.MF", "Manifest-Version: 1.0\r\nImplementation-Version: ${version}\r\n\r\n",
                "META-INF/mods.toml", "[[mods]]\ndisplayName=\"X\"\nversion=\"${file.jarVersion}\"\n");

        assertThat(m.version()).isNull();
    }

    @Test
    void aManifestWithoutAVersionGivesNoVersion() throws Exception {
        Meta m = mod(
                "META-INF/MANIFEST.MF", "Manifest-Version: 1.0\r\n\r\n",
                "META-INF/mods.toml", "[[mods]]\ndisplayName=\"X\"\n");

        assertThat(m.version()).isNull();
    }

    @Test
    void aMalformedManifestIsIgnored() throws Exception {
        Meta m = mod(
                "META-INF/MANIFEST.MF", "no colon on this line\r\n\r\n",
                "META-INF/mods.toml", "[[mods]]\ndisplayName=\"X\"\nversion=\"1\"\n");

        assertThat(m.name()).isEqualTo("X");
        assertThat(m.version()).isEqualTo("1");
    }

    @Test
    void neoForgeTomlWithoutModsFallsThroughToForgeToml() throws Exception {
        Meta m = mod(
                "META-INF/neoforge.mods.toml", "modLoader=\"javafml\"\n",
                "META-INF/mods.toml", "[[mods]]\ndisplayName=\"Forge\"\n");

        assertThat(m.name()).isEqualTo("Forge");
    }

    @Test
    void brokenTomlFallsThroughToOtherFormats() throws Exception {
        Meta m = mod(
                "META-INF/mods.toml", "this = = is not toml",
                "fabric.mod.json", "{\"name\":\"Fabric Fallback\"}");

        assertThat(m.name()).isEqualTo("Fabric Fallback");
    }

    @Test
    void aMissingLogoFileIsNotReported() throws Exception {
        Meta m = mod("META-INF/mods.toml", "[[mods]]\ndisplayName=\"X\"\nlogoFile=\"missing.png\"\n");
        assertThat(m.iconEntry()).isNull();
    }

    // ---- fabric

    @Test
    void readsFabric() throws Exception {
        Meta m = mod("fabric.mod.json", """
                {"name":"Fabric Mod","version":"2.0","description":"  Does  things ",
                 "authors":["Alice",{"name":"Bob"},{"other":"x"}],"icon":"assets/icon.png"}""",
                "assets/icon.png", "png");

        assertThat(m).isEqualTo(new Meta("Fabric Mod", "2.0", "Does things", "Alice, Bob", "assets/icon.png"));
    }

    @Test
    void fabricIconObjectUsesTheLastEntry() throws Exception {
        Meta m = mod("fabric.mod.json", "{\"icon\":{\"16\":\"small.png\",\"128\":\"big.png\"}}",
                "small.png", "x", "big.png", "x");

        assertThat(m.iconEntry()).isEqualTo("big.png");
    }

    @Test
    void fabricWithoutOptionalFieldsIsEmptyButValid() throws Exception {
        Meta m = mod("fabric.mod.json", "{\"icon\":5,\"authors\":[]}");

        assertThat(m).isEqualTo(new Meta(null, null, null, null, null));
    }

    @Test
    void fabricAuthorsWithNoUsableNamesAreNull() throws Exception {
        assertThat(mod("fabric.mod.json", "{\"authors\":[{\"other\":\"x\"}]}").authors()).isNull();
    }

    @Test
    void brokenFabricJsonIsEmpty() throws Exception {
        assertThat(mod("fabric.mod.json", "{broken")).isEqualTo(Meta.EMPTY);
    }

    // ---- quilt

    @Test
    void readsQuilt() throws Exception {
        Meta m = mod("quilt.mod.json", """
                {"quilt_loader":{"version":"3.0","metadata":{"name":"Quilt Mod","description":"Soft",
                 "contributors":{"Carol":"Owner","Dan":"Dev"},"icon":"q.png"}}}""", "q.png", "x");

        assertThat(m).isEqualTo(new Meta("Quilt Mod", "3.0", "Soft", "Carol, Dan", "q.png"));
    }

    @Test
    void quiltWithoutContributorsHasNoAuthors() throws Exception {
        assertThat(mod("quilt.mod.json", "{\"quilt_loader\":{}}").authors()).isNull();
    }

    // ---- mcmod.info

    @Test
    void readsLegacyMcmodInfoAsAnArray() throws Exception {
        Meta m = mod("mcmod.info", """
                [{"name":"Old Mod","version":"1.7","description":"Classic","authorList":["Eve"],"logoFile":"l.png"}]""",
                "l.png", "x");

        assertThat(m).isEqualTo(new Meta("Old Mod", "1.7", "Classic", "Eve", "l.png"));
    }

    @Test
    void readsLegacyMcmodInfoWithAModList() throws Exception {
        Meta m = mod("mcmod.info", "{\"modList\":[{\"name\":\"Listed\",\"authors\":[\"Fay\"]}]}");

        assertThat(m.name()).isEqualTo("Listed");
        assertThat(m.authors()).isEqualTo("Fay");
    }

    @Test
    void textualAuthorsAreKept() throws Exception {
        assertThat(mod("mcmod.info", "[{\"authors\":\"Gil\"}]").authors()).isEqualTo("Gil");
    }

    @Test
    void anEmptyMcmodInfoIsEmpty() throws Exception {
        assertThat(mod("mcmod.info", "[]")).isEqualTo(Meta.EMPTY);
    }

    @Test
    void aJarWithoutMetadataIsEmpty() throws Exception {
        assertThat(mod("some/File.class", "x")).isEqualTo(Meta.EMPTY);
    }

    // ---- packs

    @Test
    void readsAPackWithAPlainDescription() throws Exception {
        Meta m = pack("pack.mcmeta", "{\"pack\":{\"description\":\"§6Gold §rpack\"}}", "pack.png", "png");

        assertThat(m).isEqualTo(new Meta(null, null, "Gold pack", null, "pack.png"));
    }

    @Test
    void flattensADescriptionObject() throws Exception {
        Meta m = pack("pack.mcmeta", """
                {"pack":{"description":{"text":"Hello ","extra":[{"text":"big "},"world"]}}}""");

        assertThat(m.description()).isEqualTo("Hello big world");
    }

    @Test
    void flattensADescriptionArrayIgnoringOtherValues() throws Exception {
        Meta m = pack("pack.mcmeta", "{\"pack\":{\"description\":[\"a\",{\"text\":\"b\"},null,5]}}");

        assertThat(m.description()).isEqualTo("ab");
    }

    @Test
    void aMissingDescriptionIsNull() throws Exception {
        assertThat(pack("pack.mcmeta", "{\"pack\":{}}").description()).isNull();
        assertThat(pack("pack.mcmeta", "{\"pack\":{\"description\":null}}").description()).isNull();
    }

    @Test
    void aDescriptionOfOnlyColorCodesIsNull() throws Exception {
        assertThat(pack("pack.mcmeta", "{\"pack\":{\"description\":\"§a§b\"}}").description()).isNull();
    }

    @Test
    void brokenPackMetadataIsIgnored() throws Exception {
        Meta m = pack("pack.mcmeta", "{broken", "pack.png", "png");

        assertThat(m.description()).isNull();
        assertThat(m.iconEntry()).isEqualTo("pack.png");
    }

    @Test
    void readsAPackFolder() throws Exception {
        Path folder = Files.createDirectories(dir.resolve("pack"));
        Files.writeString(folder.resolve("pack.mcmeta"), "{\"pack\":{\"description\":\"Folder pack\"}}");
        Files.writeString(folder.resolve("pack.png"), "png");

        Meta m = reader.read(ContentType.RESOURCEPACKS, local(folder, true));

        assertThat(m).isEqualTo(new Meta(null, null, "Folder pack", null, "pack.png"));
        assertThat(reader.readIcon(local(folder, true), m)).asString().isEqualTo("png");
    }

    @Test
    void anEmptyPackFolderHasNoMetadata() throws Exception {
        Path folder = Files.createDirectories(dir.resolve("empty"));
        assertThat(reader.read(ContentType.RESOURCEPACKS, local(folder, true)).iconEntry()).isNull();
    }

    // ---- safety and limits

    @Test
    void aModFolderCantPointOutsideItself() throws Exception {
        Files.writeString(dir.resolve("secret.png"), "secret");
        Path folder = Files.createDirectories(dir.resolve("mod"));
        Files.writeString(folder.resolve("fabric.mod.json"), "{\"icon\":\"../secret.png\"}");

        assertThat(reader.read(ContentType.MODS, local(folder, true)).iconEntry()).isNull();
    }

    @Test
    void oversizedEntriesAreSkipped() throws Exception {
        byte[] huge = new byte[8 * 1024 * 1024 + 1];
        assertThat(pack("pack.mcmeta", huge).description()).isNull();

        Path folder = Files.createDirectories(dir.resolve("big"));
        Files.write(folder.resolve("pack.mcmeta"), huge);
        assertThat(reader.read(ContentType.RESOURCEPACKS, local(folder, true)).description()).isNull();
    }

    @Test
    void zipFolderEntriesDontCountAsFiles() throws Exception {
        assertThat(pack("pack.png/", "").iconEntry()).isNull();
        assertThat(pack("pack.mcmeta/", "").description()).isNull();
    }

    @Test
    void aFileThatIsNotAZipIsEmpty() throws Exception {
        Path notZip = Files.writeString(dir.resolve("broken.jar"), "not a zip");

        assertThat(reader.read(ContentType.MODS, local(notZip, false))).isEqualTo(Meta.EMPTY);
    }

    // ---- icons and cache

    @Test
    void readIconReturnsTheBytes() throws Exception {
        LocalFile f = zip(entries("pack.png", new byte[] { 1, 2, 3 }));

        assertThat(reader.readIcon(f, reader.read(ContentType.RESOURCEPACKS, f))).containsExactly(1, 2, 3);
    }

    @Test
    void readIconWithoutAnIconIsNull() throws Exception {
        LocalFile f = zip(entries("x", "y"));

        assertThat(reader.readIcon(f, null)).isNull();
        assertThat(reader.readIcon(f, Meta.EMPTY)).isNull();
    }

    @Test
    void readIconOfAMissingFileIsNull() {
        LocalFile f = new LocalFile("x", "x", dir.resolve("gone.jar"), false, 1, 1, null, null, () -> null);

        assertThat(reader.readIcon(f, new Meta(null, null, null, null, "pack.png"))).isNull();
    }

    @Test
    void metadataIsCachedPerFileVersion() throws Exception {
        LocalFile f = zip(entries("fabric.mod.json", "{\"name\":\"Cached\"}"));
        Meta first = reader.read(ContentType.MODS, f);

        Files.delete(f.path());

        assertThat(reader.read(ContentType.MODS, f)).isSameAs(first);
        LocalFile changed = new LocalFile(f.id(), f.fileName(), f.path(), false, f.size() + 1, 2, null, null, () -> null);
        assertThat(reader.read(ContentType.MODS, changed)).isEqualTo(Meta.EMPTY);
    }

    @Test
    void utf8DecodesBytes() {
        assertThat(LocalMetadataReader.utf8("é".getBytes(StandardCharsets.UTF_8))).isEqualTo("é");
    }
}
