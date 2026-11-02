package com.mcmanager.provider;

import java.util.List;
import java.util.Map;

import com.mcmanager.content.ContentType;
import com.mcmanager.content.LocalFile;
import com.mcmanager.settings.SettingsDto;

/** Source of content information (Modrinth or CurseForge). The key in all maps is {@link LocalFile#id()}. */
public interface ContentProvider {

    ProviderType type();

    /** Identifies the project and current version by the file's hash. Unrecognized files are omitted from the result. */
    Map<String, ProjectInfo> identify(ContentType type, List<LocalFile> files, SettingsDto settings);

    /** Looks up the latest version for the configured game version and loader. */
    Map<String, UpdateCheck> latest(ContentType type, List<LocalFile> files,
            Map<String, ProjectInfo> known, SettingsDto settings);

    /** Fills in the download link and hash right before installing. */
    default UpdateInfo prepareDownload(ProjectInfo project, UpdateInfo update, SettingsDto settings) {
        return update;
    }
}
