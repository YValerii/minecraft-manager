package com.mcmanager.content;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.mcmanager.settings.SettingsDto;
import com.mcmanager.settings.SettingsService;

/** Finds mods, resource packs, shaders, and datapacks in the configured folders. */
@Component
public class FileScanner {

    private static final Logger log = LoggerFactory.getLogger(FileScanner.class);

    private final SettingsService settings;
    private final HashCache hashes;

    public FileScanner(SettingsService settings, HashCache hashes) {
        this.settings = settings;
        this.hashes = hashes;
    }

    public List<LocalFile> scan(ContentType type, SettingsDto s) {
        Path root = settings.minecraftRoot(s);
        List<LocalFile> out = new ArrayList<>();
        scanDir(type, settings.dir(s, type), root, null, out);

        // Datapacks usually also live in saves/<world>/datapacks — include those too.
        if (type == ContentType.DATAPACKS) {
            Path saves = settings.savesDir(s);
            if (Files.isDirectory(saves)) {
                try (DirectoryStream<Path> worlds = Files.newDirectoryStream(saves, Files::isDirectory)) {
                    for (Path world : worlds) {
                        scanDir(type, world.resolve("datapacks"), root, world.getFileName().toString(), out);
                    }
                } catch (IOException e) {
                    log.warn("Failed to read {}: {}", saves, e.getMessage());
                }
            }
        }
        out.sort(Comparator.comparing((LocalFile f) -> f.location() == null ? "" : f.location().toLowerCase(Locale.ROOT))
                .thenComparing(f -> f.fileName().toLowerCase(Locale.ROOT)));
        return out;
    }

    private void scanDir(ContentType type, Path dir, Path root, String location, List<LocalFile> out) {
        if (!Files.isDirectory(dir)) {
            return;
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            for (Path p : stream) {
                String name = p.getFileName().toString();
                if (name.startsWith(".")) {
                    continue;
                }
                boolean isDir = Files.isDirectory(p);
                if (!accepts(type, name, isDir)) {
                    continue;
                }
                BasicFileAttributes attrs = Files.readAttributes(p, BasicFileAttributes.class);
                long size = isDir ? 0 : attrs.size();
                long modified = attrs.lastModifiedTime().toMillis();
                String sha1 = isDir ? null : hashes.sha1(p, size, modified);
                Supplier<Long> fingerprint = () -> isDir ? null : hashes.fingerprint(p, size, modified);
                out.add(new LocalFile(idOf(root, p), name, p, isDir, size, modified, location, sha1, fingerprint));
            }
        } catch (IOException e) {
            log.warn("Failed to read {}: {}", dir, e.getMessage());
        }
    }

    private static boolean accepts(ContentType type, String name, boolean isDir) {
        String lower = name.toLowerCase(Locale.ROOT);
        return switch (type) {
            case MODS -> !isDir && lower.endsWith(".jar");
            case RESOURCEPACKS, SHADERPACKS, DATAPACKS -> isDir || lower.endsWith(".zip");
        };
    }

    private static String idOf(Path root, Path p) {
        Path abs = p.toAbsolutePath().normalize();
        if (abs.startsWith(root)) {
            return root.relativize(abs).toString().replace('\\', '/');
        }
        return abs.toString().replace('\\', '/');
    }
}
