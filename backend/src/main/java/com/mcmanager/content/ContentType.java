package com.mcmanager.content;

import org.springframework.http.HttpStatus;

import com.mcmanager.common.ApiException;

public enum ContentType {

    MODS("mods", "mods"),
    RESOURCEPACKS("resourcepacks", "resourcepacks"),
    SHADERPACKS("shaderpacks", "shaderpacks"),
    DATAPACKS("datapacks", "datapacks");

    private final String slug;
    private final String defaultDir;

    ContentType(String slug, String defaultDir) {
        this.slug = slug;
        this.defaultDir = defaultDir;
    }

    public String slug() {
        return slug;
    }

    public String defaultDir() {
        return defaultDir;
    }

    public static ContentType fromSlug(String slug) {
        for (ContentType t : values()) {
            if (t.slug.equalsIgnoreCase(slug)) {
                return t;
            }
        }
        throw new ApiException(HttpStatus.NOT_FOUND, "Unknown content type: " + slug);
    }
}
