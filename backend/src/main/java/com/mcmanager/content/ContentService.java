package com.mcmanager.content;

import static com.mcmanager.common.Util.blank;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

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
import com.mcmanager.settings.SettingsDto;
import com.mcmanager.settings.SettingsService;

@Service
public class ContentService {

    private static final Logger log = LoggerFactory.getLogger(ContentService.class);
    private static final String BACKUP_DIR = ".mc-manager-backup";

    private record CachedCheck(Instant at, UpdateCheck check) {
    }

    private final SettingsService settings;
    private final FileScanner scanner;
    private final LocalMetadataReader metadata;
    private final IdentityCache identities;
    private final Map<ProviderType, ContentProvider> providers;
    private final Http http;
    private final Duration ttl;
    private final Map<String, CachedCheck> updateCache = new ConcurrentHashMap<>();

    public ContentService(SettingsService settings, FileScanner scanner, LocalMetadataReader metadata,
            IdentityCache identities, List<ContentProvider> providerList, Http http, AppProperties props) {
        this.settings = settings;
        this.scanner = scanner;
        this.metadata = metadata;
        this.identities = identities;
        this.providers = providerList.stream().collect(Collectors.toMap(ContentProvider::type, Function.identity()));
        this.http = http;
        this.ttl = Duration.ofMinutes(Math.max(1, props.cacheTtlMinutes()));
    }

    // ------------------------------------------------------------------ list

    public ContentList list(ContentType type, boolean refresh) {
        SettingsDto s = settings.get();
        List<LocalFile> files = scanner.scan(type, s);
        Path dir = settings.dir(s, type);

        Map<String, ProjectInfo> known = Map.of();
        String warning = null;
        try {
            known = identify(type, files, s, refresh);
        } catch (ApiException e) {
            warning = e.getMessage();
            log.warn("Failed to get data from {}: {}", s.provider(), e.getMessage());
        }

        List<ContentItem> items = new ArrayList<>();
        for (LocalFile f : files) {
            items.add(toItem(type, f, known.get(f.id())));
        }
        return new ContentList(dir.toString(), Files.isDirectory(dir), s.provider(), items, warning);
    }

    private ContentItem toItem(ContentType type, LocalFile f, ProjectInfo p) {
        LocalMetadataReader.Meta m = metadata.read(type, f);
        String name = first(p == null ? null : p.name(), m.name(), stripExtension(f.fileName()));
        return new ContentItem(
                f.id(), f.fileName(), f.directory(), f.size(), f.modified(), f.location(),
                name,
                first(p == null ? null : p.description(), m.description()),
                m.authors(),
                first(p == null ? null : p.versionNumber(), m.version()),
                p == null ? null : p.iconUrl(),
                m.iconEntry() != null,
                p == null ? null : p.pageUrl(),
                p == null ? "LOCAL" : p.provider().name(),
                p != null);
    }

    // ------------------------------------------------------------------ identification

    private Map<String, ProjectInfo> identify(ContentType type, List<LocalFile> files, SettingsDto s, boolean refresh) {
        ProviderType pt = providerType(s);
        ContentProvider provider = providers.get(pt);
        List<LocalFile> hashable = files.stream().filter(f -> f.sha1() != null).toList();
        if (hashable.isEmpty()) {
            return Map.of();
        }

        Map<String, IdentityCache.Entry> cached = identities
                .findAll(hashable.stream().map(f -> IdentityCache.key(pt, f.sha1())).distinct().toList());

        Instant negativeFresh = Instant.now().minus(ttl);
        Map<String, ProjectInfo> result = new HashMap<>();
        List<LocalFile> lookup = new ArrayList<>();
        for (LocalFile f : hashable) {
            IdentityCache.Entry c = cached.get(IdentityCache.key(pt, f.sha1()));
            if (c != null && !refresh && c.found()) {
                result.put(f.id(), c.toProject(pt));
            } else if (c != null && !refresh && c.checkedAt().isAfter(negativeFresh)) {
                // checked recently — this file isn't on the source
            } else {
                lookup.add(f);
            }
        }

        if (!lookup.isEmpty()) {
            Map<String, ProjectInfo> found = provider.identify(type, lookup, s);
            Map<String, IdentityCache.Entry> toSave = new LinkedHashMap<>();
            for (LocalFile f : lookup) {
                ProjectInfo p = found.get(f.id());
                IdentityCache.Entry e = IdentityCache.Entry.of(pt, f.sha1(), p);
                toSave.put(e.cacheKey(), e);
                if (p != null) {
                    result.put(f.id(), p);
                }
            }
            identities.saveAll(List.copyOf(toSave.values()));
        }
        return result;
    }

    // ------------------------------------------------------------------ updates

    public UpdatesResult updates(ContentType type, boolean refresh) {
        SettingsDto s = settings.get();
        List<LocalFile> files = scanner.scan(type, s);
        Map<String, UpdateCheck> checks = new HashMap<>();
        String warning = null;
        try {
            Map<String, ProjectInfo> known = identify(type, files, s, false);
            checks = checkUpdates(type, files, known, s, refresh);
        } catch (ApiException e) {
            warning = e.getMessage();
            log.warn("Update check failed: {}", e.getMessage());
        }

        List<UpdateItem> items = new ArrayList<>();
        for (LocalFile f : files) {
            UpdateCheck c = checks.getOrDefault(f.id(), UpdateCheck.unknown());
            UpdateInfo u = c.update();
            items.add(new UpdateItem(f.id(), c.status(),
                    u == null ? null : u.versionNumber(),
                    u == null ? null : u.fileName(),
                    c.status() == UpdateStatus.UPDATE_AVAILABLE && u != null ? u.changelog() : null));
        }
        return new UpdatesResult(items, warning);
    }

    private Map<String, UpdateCheck> checkUpdates(ContentType type, List<LocalFile> files,
            Map<String, ProjectInfo> known, SettingsDto s, boolean refresh) {
        ContentProvider provider = providers.get(providerType(s));
        Map<String, UpdateCheck> out = new HashMap<>();
        List<LocalFile> need = new ArrayList<>();
        Instant fresh = Instant.now().minus(ttl);

        for (LocalFile f : files) {
            if (!known.containsKey(f.id())) {
                continue;
            }
            CachedCheck c = updateCache.get(cacheKey(type, s, f));
            if (!refresh && c != null && c.at().isAfter(fresh)) {
                out.put(f.id(), c.check());
            } else {
                need.add(f);
            }
        }
        if (!need.isEmpty()) {
            Map<String, UpdateCheck> res = provider.latest(type, need, known, s);
            Instant now = Instant.now();
            for (LocalFile f : need) {
                UpdateCheck c = res.getOrDefault(f.id(), UpdateCheck.unknown());
                updateCache.put(cacheKey(type, s, f), new CachedCheck(now, c));
                out.put(f.id(), c);
            }
        }
        return out;
    }

    private static String cacheKey(ContentType type, SettingsDto s, LocalFile f) {
        return String.join("|", s.provider(), type.name(), s.gameVersion(), s.loader(), f.sha1());
    }

    // ------------------------------------------------------------------ install

    public InstallResult install(ContentType type, String id) {
        SettingsDto s = settings.get();
        List<LocalFile> files = scanner.scan(type, s);
        LocalFile file = files.stream().filter(f -> f.id().equals(id)).findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "File not found: " + id));
        if (file.directory() || file.sha1() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Folders can't be updated — only .jar / .zip files");
        }

        ProviderType pt = providerType(s);
        ContentProvider provider = providers.get(pt);
        Map<String, ProjectInfo> known = identify(type, List.of(file), s, false);
        ProjectInfo project = known.get(id);
        if (project == null) {
            throw new ApiException(HttpStatus.CONFLICT, "File not recognized on " + pt + " — can't update");
        }

        // Always a fresh check: the cache may be stale
        UpdateCheck check = provider.latest(type, List.of(file), known, s).get(id);
        if (check == null || check.status() != UpdateStatus.UPDATE_AVAILABLE) {
            throw new ApiException(HttpStatus.CONFLICT, "No newer versions for " + s.gameVersion() + " / " + s.loader());
        }
        UpdateInfo update = provider.prepareDownload(project, check.update(), s);
        if (blank(update.downloadUrl()) || blank(update.fileName())) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "The source did not return a download link");
        }

        Path parent = file.path().getParent();
        String newName = safeFileName(update.fileName());
        Path target = parent.resolve(newName);
        if (Files.exists(target) && !target.equals(file.path())) {
            throw new ApiException(HttpStatus.CONFLICT, "File " + newName + " already exists in the folder");
        }
        Path tmp = parent.resolve(newName + ".part");

        try {
            http.download(update.downloadUrl(), tmp);
            verify(tmp, update);

            Path backup = backupDir(settings.minecraftRoot(s), type);
            Files.createDirectories(backup);
            Files.move(file.path(), backup.resolve(file.fileName()));
            Files.move(tmp, target);
        } catch (IOException e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to replace file: " + e.getMessage());
        } finally {
            try {
                Files.deleteIfExists(tmp);
            } catch (IOException ignored) {
                // the temp file isn't critical
            }
        }
        log.info("Updated {} -> {}", file.fileName(), newName);
        return new InstallResult(id, true, "Updated to " + update.versionNumber(), newName);
    }

    public List<InstallResult> installAll(ContentType type) {
        List<InstallResult> results = new ArrayList<>();
        for (UpdateItem item : updates(type, true).items()) {
            if (item.status() != UpdateStatus.UPDATE_AVAILABLE) {
                continue;
            }
            try {
                results.add(install(type, item.id()));
            } catch (ApiException e) {
                results.add(new InstallResult(item.id(), false, e.getMessage(), null));
            }
        }
        return results;
    }

    private static void verify(Path file, UpdateInfo update) throws IOException {
        if (blank(update.hash()) || blank(update.hashAlgo())) {
            return;
        }
        String algo = switch (update.hashAlgo().toLowerCase()) {
            case "sha512" -> "SHA-512";
            case "sha1" -> "SHA-1";
            case "md5" -> "MD5";
            default -> null;
        };
        if (algo != null && !Hashing.digest(file, algo).equalsIgnoreCase(update.hash())) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "Checksum of the downloaded file doesn't match — file not replaced");
        }
    }

    private static Path backupDir(Path root, ContentType type) {
        String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        return root.resolve(BACKUP_DIR).resolve(type.slug()).resolve(stamp);
    }

    /** The file name comes from an external API — strip out any path components. */
    private static String safeFileName(String name) {
        String n = Path.of(name.replace('\\', '/')).getFileName().toString();
        if (n.isBlank() || n.startsWith(".")) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "Invalid file name from source: " + name);
        }
        return n;
    }

    // ------------------------------------------------------------------ icons

    public Icon icon(ContentType type, String id) {
        SettingsDto s = settings.get();
        LocalFile file = scanner.scan(type, s).stream().filter(f -> f.id().equals(id)).findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "File not found"));
        byte[] bytes = metadata.readIcon(file, metadata.read(type, file));
        if (bytes == null || bytes.length == 0) {
            throw new ApiException(HttpStatus.NOT_FOUND, "No icon");
        }
        boolean jpeg = bytes.length > 2 && (bytes[0] & 0xff) == 0xFF && (bytes[1] & 0xff) == 0xD8;
        return new Icon(bytes, jpeg ? "image/jpeg" : "image/png");
    }

    // ------------------------------------------------------------------ utilities

    private static ProviderType providerType(SettingsDto s) {
        try {
            return ProviderType.valueOf(s.provider().toUpperCase());
        } catch (Exception e) {
            return ProviderType.MODRINTH;
        }
    }

    private static String first(String... values) {
        for (String v : values) {
            if (!blank(v)) {
                return v;
            }
        }
        return null;
    }

    private static String stripExtension(String name) {
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }
}
