package com.mcmanager.provider;

/** The project (mod / pack) on Modrinth or CurseForge that the installed file belongs to. */
public record ProjectInfo(
        ProviderType provider,
        String projectId,
        String name,
        String description,
        String iconUrl,
        String pageUrl,
        String versionId,
        String versionNumber) {
}
