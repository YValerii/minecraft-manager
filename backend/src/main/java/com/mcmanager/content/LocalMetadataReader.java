package com.mcmanager.content;

import static com.mcmanager.common.Util.blank;
import static com.mcmanager.common.Util.text;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.toml.TomlMapper;

/**
 * Reads metadata directly from the file: neoforge.mods.toml / mods.toml / fabric.mod.json /
 * quilt.mod.json / mcmod.info for mods, and pack.mcmeta + pack.png for packs.
 * Used when the file isn't found on Modrinth / CurseForge, and also for local icons.
 */
@Component
public class LocalMetadataReader {

    private static final Logger log = LoggerFactory.getLogger(LocalMetadataReader.class);
    private static final int MAX_ENTRY_BYTES = 8 * 1024 * 1024;

    public record Meta(String name, String version, String description, String authors, String iconEntry) {
        static final Meta EMPTY = new Meta(null, null, null, null, null);
    }

    /** A single interface for accessing files inside a zip/jar or a plain folder. */
    private interface Entries extends AutoCloseable {
        byte[] get(String name) throws IOException;

        boolean has(String name);

        @Override
        void close() throws IOException;
    }

    private static final class ZipEntries implements Entries {
        private final ZipFile zip;

        ZipEntries(Path file) throws IOException {
            this.zip = new ZipFile(file.toFile());
        }

        @Override
        public byte[] get(String name) throws IOException {
            ZipEntry e = zip.getEntry(name);
            if (e == null || e.isDirectory() || e.getSize() > MAX_ENTRY_BYTES) {
                return null;
            }
            try (InputStream in = zip.getInputStream(e)) {
                return in.readNBytes(MAX_ENTRY_BYTES);
            }
        }

        @Override
        public boolean has(String name) {
            ZipEntry e = zip.getEntry(name);
            return e != null && !e.isDirectory();
        }

        @Override
        public void close() throws IOException {
            zip.close();
        }
    }

    private static final class DirEntries implements Entries {
        private final Path base;

        DirEntries(Path base) {
            this.base = base.toAbsolutePath().normalize();
        }

        private Path resolve(String name) {
            Path p = base.resolve(name).normalize();
            return p.startsWith(base) && Files.isRegularFile(p) ? p : null;
        }

        @Override
        public byte[] get(String name) throws IOException {
            Path p = resolve(name);
            if (p == null || Files.size(p) > MAX_ENTRY_BYTES) {
                return null;
            }
            return Files.readAllBytes(p);
        }

        @Override
        public boolean has(String name) {
            return resolve(name) != null;
        }

        @Override
        public void close() {
        }
    }

    private final ObjectMapper json;
    private final TomlMapper toml = new TomlMapper();
    private final Map<String, Meta> cache = new ConcurrentHashMap<>();

    public LocalMetadataReader(ObjectMapper json) {
        this.json = json;
    }

    public Meta read(ContentType type, LocalFile f) {
        String key = type + "|" + f.path() + "|" + f.size() + "|" + f.modified();
        return cache.computeIfAbsent(key, k -> {
            try (Entries entries = open(f)) {
                return type == ContentType.MODS ? readMod(entries) : readPack(entries);
            } catch (Exception e) {
                log.debug("Metadata for {} not read: {}", f.fileName(), e.getMessage());
                return Meta.EMPTY;
            }
        });
    }

    /** Icon bytes, or null. */
    public byte[] readIcon(LocalFile f, Meta meta) {
        if (meta == null || meta.iconEntry() == null) {
            return null;
        }
        try (Entries entries = open(f)) {
            return entries.get(meta.iconEntry());
        } catch (Exception e) {
            return null;
        }
    }

    private static Entries open(LocalFile f) throws IOException {
        return f.directory() ? new DirEntries(f.path()) : new ZipEntries(f.path());
    }

    // ------------------------------------------------------------------ mods

    private Meta readMod(Entries e) throws IOException {
        String manifestVersion = manifestVersion(e.get("META-INF/MANIFEST.MF"));

        for (String name : List.of("META-INF/neoforge.mods.toml", "META-INF/mods.toml")) {
            byte[] b = e.get(name);
            if (b != null) {
                Meta m = fromToml(b, manifestVersion, e);
                if (m != null) {
                    return m;
                }
            }
        }
        byte[] fabric = e.get("fabric.mod.json");
        if (fabric != null) {
            return fromFabric(fabric, manifestVersion, e);
        }
        byte[] quilt = e.get("quilt.mod.json");
        if (quilt != null) {
            return fromQuilt(quilt, e);
        }
        byte[] legacy = e.get("mcmod.info");
        if (legacy != null) {
            return fromMcmodInfo(legacy, e);
        }
        return Meta.EMPTY;
    }

    private Meta fromToml(byte[] bytes, String manifestVersion, Entries e) {
        try {
            JsonNode root = toml.readTree(bytes);
            JsonNode mod = root.path("mods").path(0);
            if (mod.isMissingNode()) {
                return null;
            }
            String logo = firstNonBlank(text(mod, "logoFile"), text(root, "logoFile"));
            return new Meta(
                    text(mod, "displayName"),
                    resolveVersion(text(mod, "version"), manifestVersion),
                    clean(text(mod, "description")),
                    firstNonBlank(text(mod, "authors"), text(root, "authors")),
                    existing(e, logo));
        } catch (Exception ex) {
            log.debug("TOML not parsed: {}", ex.getMessage());
            return null;
        }
    }

    private Meta fromFabric(byte[] bytes, String manifestVersion, Entries e) throws IOException {
        JsonNode r = json.readTree(bytes);
        String icon = null;
        JsonNode iconNode = r.path("icon");
        if (iconNode.isTextual()) {
            icon = iconNode.asText();
        } else if (iconNode.isObject()) {
            for (var en : iconNode.properties()) { // take the last one (usually the biggest)
                icon = en.getValue().asText();
            }
        }
        return new Meta(
                text(r, "name"),
                resolveVersion(text(r, "version"), manifestVersion),
                clean(text(r, "description")),
                joinPeople(r.path("authors")),
                existing(e, icon));
    }

    private Meta fromQuilt(byte[] bytes, Entries e) throws IOException {
        JsonNode loader = json.readTree(bytes).path("quilt_loader");
        JsonNode md = loader.path("metadata");
        List<String> people = new ArrayList<>();
        md.path("contributors").properties().forEach(en -> people.add(en.getKey()));
        return new Meta(
                text(md, "name"),
                resolveVersion(text(loader, "version"), null),
                clean(text(md, "description")),
                people.isEmpty() ? null : String.join(", ", people),
                existing(e, text(md, "icon")));
    }

    private Meta fromMcmodInfo(byte[] bytes, Entries e) throws IOException {
        JsonNode r = json.readTree(bytes);
        JsonNode m = r.isArray() ? r.path(0) : r.path("modList").path(0);
        if (m.isMissingNode()) {
            return Meta.EMPTY;
        }
        return new Meta(
                text(m, "name"),
                resolveVersion(text(m, "version"), null),
                clean(text(m, "description")),
                joinPeople(firstNode(m.path("authorList"), m.path("authors"))),
                existing(e, text(m, "logoFile")));
    }

    // ------------------------------------------------------------------ packs

    private Meta readPack(Entries e) throws IOException {
        String description = null;
        byte[] mcmeta = e.get("pack.mcmeta");
        if (mcmeta != null) {
            try {
                description = clean(flatten(json.readTree(mcmeta).path("pack").path("description")));
            } catch (Exception ex) {
                log.debug("pack.mcmeta not parsed: {}", ex.getMessage());
            }
        }
        return new Meta(null, null, description, null, e.has("pack.png") ? "pack.png" : null);
    }

    /** The pack description can be a string, an object {"text":..,"extra":[..]}, or an array. */
    private static String flatten(JsonNode n) {
        if (n == null || n.isMissingNode() || n.isNull()) {
            return null;
        }
        if (n.isTextual()) {
            return n.asText();
        }
        StringBuilder sb = new StringBuilder();
        if (n.isArray()) {
            n.forEach(c -> sb.append(nullToEmpty(flatten(c))));
        } else if (n.isObject()) {
            sb.append(nullToEmpty(text(n, "text")));
            sb.append(nullToEmpty(flatten(n.path("extra"))));
        }
        return sb.toString();
    }

    // ------------------------------------------------------------------ utilities

    private static String manifestVersion(byte[] manifest) {
        if (manifest == null) {
            return null;
        }
        try {
            String v = new Manifest(new ByteArrayInputStream(manifest)).getMainAttributes()
                    .getValue("Implementation-Version");
            return blank(v) || v.contains("${") ? null : v.trim();
        } catch (IOException e) {
            return null;
        }
    }

    /** Versions like ${file.jarVersion} are substituted by the build — take the value from MANIFEST.MF instead. */
    private static String resolveVersion(String version, String manifestVersion) {
        if (blank(version) || version.contains("${")) {
            return manifestVersion;
        }
        return version.trim();
    }

    private static String existing(Entries e, String path) {
        if (blank(path)) {
            return null;
        }
        String p = path.startsWith("/") ? path.substring(1) : path;
        return e.has(p) ? p : null;
    }

    private static String joinPeople(JsonNode authors) {
        if (authors == null || !authors.isArray() || authors.isEmpty()) {
            return authors != null && authors.isTextual() ? authors.asText() : null;
        }
        List<String> names = new ArrayList<>();
        authors.forEach(a -> {
            String n = a.isTextual() ? a.asText() : text(a, "name");
            if (!blank(n)) {
                names.add(n);
            }
        });
        return names.isEmpty() ? null : String.join(", ", names);
    }

    private static JsonNode firstNode(JsonNode a, JsonNode b) {
        return a != null && !a.isMissingNode() ? a : b;
    }

    private static String firstNonBlank(String a, String b) {
        return blank(a) ? (blank(b) ? null : b) : a;
    }

    /** Strips §x color codes and extra whitespace / line breaks. */
    private static String clean(String s) {
        if (blank(s)) {
            return null;
        }
        String c = s.replaceAll("§.", "").replaceAll("\\s+", " ").trim();
        return c.isEmpty() ? null : c;
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    static String utf8(byte[] b) {
        return new String(b, StandardCharsets.UTF_8);
    }
}
