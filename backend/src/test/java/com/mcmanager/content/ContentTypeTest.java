package com.mcmanager.content;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import com.mcmanager.common.ApiException;

class ContentTypeTest {

    @Test
    void slugsAreMatchedIgnoringCase() {
        assertThat(ContentType.fromSlug("mods")).isEqualTo(ContentType.MODS);
        assertThat(ContentType.fromSlug("ShaderPacks")).isEqualTo(ContentType.SHADERPACKS);
        assertThat(ContentType.DATAPACKS.slug()).isEqualTo("datapacks");
        assertThat(ContentType.RESOURCEPACKS.defaultDir()).isEqualTo("resourcepacks");
    }

    @Test
    void anUnknownSlugIsNotFound() {
        assertThatThrownBy(() -> ContentType.fromSlug("nope")).isInstanceOfSatisfying(ApiException.class, e -> {
            assertThat(e.status()).isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(e.getMessage()).contains("nope");
        });
    }
}
