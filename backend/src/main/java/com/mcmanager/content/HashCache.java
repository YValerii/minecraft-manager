package com.mcmanager.content;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.mcmanager.common.Hashing;

/** In-memory hash cache: key = path + size + modified time, so a file change automatically invalidates the entry. */
@Component
public class HashCache {

    private static final Logger log = LoggerFactory.getLogger(HashCache.class);

    private record Key(String path, long size, long modified) {
    }

    private final Map<Key, String> sha1 = new ConcurrentHashMap<>();
    private final Map<Key, Long> fingerprints = new ConcurrentHashMap<>();

    /** SHA-1, or null if the file couldn't be read. */
    public String sha1(Path file, long size, long modified) {
        String v = sha1.computeIfAbsent(new Key(file.toString(), size, modified), k -> {
            try {
                return Hashing.sha1(file);
            } catch (IOException e) {
                log.warn("Failed to compute SHA-1 for {}: {}", file, e.getMessage());
                return "";
            }
        });
        return v.isEmpty() ? null : v;
    }

    /** CurseForge fingerprint, or null if the file couldn't be read. */
    public Long fingerprint(Path file, long size, long modified) {
        long v = fingerprints.computeIfAbsent(new Key(file.toString(), size, modified), k -> {
            try {
                return Hashing.curseForgeFingerprint(file);
            } catch (IOException e) {
                log.warn("Failed to compute fingerprint for {}: {}", file, e.getMessage());
                return -1L;
            }
        });
        return v < 0 ? null : v;
    }
}
