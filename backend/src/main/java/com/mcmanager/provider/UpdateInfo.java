package com.mcmanager.provider;

/**
 * The latest compatible version. {@code downloadUrl} may be null until
 * {@link ContentProvider#prepareDownload} is called (as with CurseForge).
 *
 * @param hashAlgo "sha512", "sha1", or "md5"; hash may be null
 */
public record UpdateInfo(
        String versionId,
        String versionNumber,
        String fileName,
        String downloadUrl,
        String hashAlgo,
        String hash,
        String changelog) {
}
