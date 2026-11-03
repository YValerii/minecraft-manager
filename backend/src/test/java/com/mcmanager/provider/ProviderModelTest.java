package com.mcmanager.provider;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.mcmanager.content.ContentType;
import com.mcmanager.content.LocalFile;
import com.mcmanager.settings.SettingsDto;

class ProviderModelTest {

    @Test
    void unknownCheckHasNoUpdate() {
        assertThat(UpdateCheck.unknown()).isEqualTo(new UpdateCheck(UpdateStatus.UNKNOWN, null));
    }

    @Test
    void prepareDownloadReturnsTheUpdateUnchangedByDefault() {
        ContentProvider provider = new ContentProvider() {
            @Override
            public ProviderType type() {
                return ProviderType.MODRINTH;
            }

            @Override
            public Map<String, ProjectInfo> identify(ContentType type, List<LocalFile> files, SettingsDto s) {
                return Map.of();
            }

            @Override
            public Map<String, UpdateCheck> latest(ContentType type, List<LocalFile> files,
                    Map<String, ProjectInfo> known, SettingsDto s) {
                return Map.of();
            }
        };
        UpdateInfo update = new UpdateInfo("v", "1", "f.jar", "url", null, null, null);

        assertThat(provider.prepareDownload(null, update, null)).isSameAs(update);
    }
}
