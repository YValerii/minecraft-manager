package com.mcmanager.provider;

import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mcmanager.common.JsonFileStore;
import com.mcmanager.config.AppProperties;

/**
 * "File hash -> project" cache, persisted to a local JSON file (app.data-dir/identity-cache.json)
 * instead of a database. The hash uniquely identifies the version, so positive entries live
 * forever; "not found" entries are re-checked by the caller once their TTL expires (see
 * ContentService).
 */
@Component
public class IdentityCache {

    public record Entry(
            String cacheKey,
            boolean found,
            String projectId,
            String name,
            String description,
            String iconUrl,
            String pageUrl,
            String versionId,
            String versionNumber,
            Instant checkedAt) {

        public static Entry of(ProviderType provider, String sha1, ProjectInfo p) {
            String key = key(provider, sha1);
            Instant now = Instant.now();
            if (p == null) {
                return new Entry(key, false, null, null, null, null, null, null, null, now);
            }
            return new Entry(key, true, p.projectId(), cut(p.name(), 512), cut(p.description(), 4000),
                    cut(p.iconUrl(), 1024), cut(p.pageUrl(), 1024), p.versionId(), cut(p.versionNumber(), 512), now);
        }

        public ProjectInfo toProject(ProviderType provider) {
            return new ProjectInfo(provider, projectId, name, description, iconUrl, pageUrl, versionId, versionNumber);
        }

        private static String cut(String s, int max) {
            return s == null || s.length() <= max ? s : s.substring(0, max);
        }
    }

    public static String key(ProviderType provider, String sha1) {
        return provider.name() + ":" + sha1;
    }

    private static final Logger log = LoggerFactory.getLogger(IdentityCache.class);

    private final ObjectMapper mapper;
    private final Path file;
    private final Map<String, Entry> entries = new ConcurrentHashMap<>();

    public IdentityCache(ObjectMapper mapper, AppProperties props) {
        this.mapper = mapper;
        String dir = props.dataDir() == null || props.dataDir().isBlank() ? "./data" : props.dataDir().trim();
        this.file = Path.of(dir).resolve("identity-cache.json").toAbsolutePath().normalize();
        List<Entry> loaded = JsonFileStore.read(mapper, file, new TypeReference<List<Entry>>() {
        });
        if (loaded != null) {
            loaded.forEach(e -> entries.put(e.cacheKey(), e));
        }
    }

    public Map<String, Entry> findAll(List<String> keys) {
        Map<String, Entry> out = new HashMap<>();
        for (String k : keys) {
            Entry e = entries.get(k);
            if (e != null) {
                out.put(k, e);
            }
        }
        return out;
    }

    /** Synchronized so an older snapshot can't overwrite a newer one. A failed write only costs a re-lookup later. */
    public synchronized void saveAll(List<Entry> toSave) {
        if (toSave.isEmpty()) {
            return;
        }
        toSave.forEach(e -> entries.put(e.cacheKey(), e));
        try {
            JsonFileStore.write(mapper, file, List.copyOf(entries.values()));
        } catch (UncheckedIOException e) {
            log.warn("Identity cache not persisted: {}", e.getMessage());
        }
    }
}
