package com.mcmanager.content;

import java.util.List;

import com.mcmanager.provider.UpdateStatus;

/** DTOs for the content REST API. */
public final class ContentDtos {

    private ContentDtos() {
    }

    /**
     * @param source     MODRINTH / CURSEFORGE if the file was recognized, otherwise LOCAL
     * @param iconUrl    remote project icon (may be null)
     * @param localIcon  whether an icon is embedded in the file (GET /icon?id=...)
     */
    public record ContentItem(
            String id,
            String fileName,
            boolean directory,
            long size,
            long modified,
            String location,
            String name,
            String description,
            String authors,
            String version,
            String iconUrl,
            boolean localIcon,
            String pageUrl,
            String source,
            boolean identified) {
    }

    public record ContentList(
            String directory,
            boolean directoryExists,
            String provider,
            List<ContentItem> items,
            String warning) {
    }

    public record UpdateItem(
            String id,
            UpdateStatus status,
            String latestVersion,
            String latestFileName,
            String changelog) {
    }

    public record UpdatesResult(List<UpdateItem> items, String warning) {
    }

    public record InstallResult(String id, boolean ok, String message, String newFileName) {
    }

    public record IdRequest(String id) {
    }

    public record Icon(byte[] bytes, String contentType) {
    }
}
