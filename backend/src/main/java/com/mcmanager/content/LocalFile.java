package com.mcmanager.content;

import java.nio.file.Path;
import java.util.function.Supplier;

/**
 * A content file (or folder) on disk.
 *
 * @param id          stable identifier: path relative to the Minecraft folder (with "/")
 * @param location    null for the main folder, world name for datapacks from saves/&lt;world&gt;/datapacks
 * @param sha1        null for folders
 * @param fingerprint lazily computed murmur2 for CurseForge
 */
public record LocalFile(
        String id,
        String fileName,
        Path path,
        boolean directory,
        long size,
        long modified,
        String location,
        String sha1,
        Supplier<Long> fingerprint) {
}
