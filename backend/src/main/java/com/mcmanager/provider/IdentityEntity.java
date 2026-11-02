package com.mcmanager.provider;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * "File hash -> project" cache in Postgres. The hash uniquely identifies the version, so
 * positive entries live forever; "not found" entries are re-checked after the TTL expires.
 */
@Entity
@Table(name = "file_identity")
public class IdentityEntity {

    @Id
    @Column(name = "cache_key", length = 160)
    private String cacheKey;

    private boolean found;
    private String projectId;

    @Column(length = 512)
    private String name;

    @Column(length = 4000)
    private String description;

    @Column(length = 1024)
    private String iconUrl;

    @Column(length = 1024)
    private String pageUrl;

    private String versionId;

    @Column(length = 512)
    private String versionNumber;

    private Instant checkedAt;

    protected IdentityEntity() {
    }

    public static String key(ProviderType provider, String sha1) {
        return provider.name() + ":" + sha1;
    }

    public static IdentityEntity of(ProviderType provider, String sha1, ProjectInfo p) {
        IdentityEntity e = new IdentityEntity();
        e.cacheKey = key(provider, sha1);
        e.checkedAt = Instant.now();
        if (p != null) {
            e.found = true;
            e.projectId = p.projectId();
            e.name = cut(p.name(), 512);
            e.description = cut(p.description(), 4000);
            e.iconUrl = cut(p.iconUrl(), 1024);
            e.pageUrl = cut(p.pageUrl(), 1024);
            e.versionId = p.versionId();
            e.versionNumber = cut(p.versionNumber(), 512);
        }
        return e;
    }

    public ProjectInfo toProject(ProviderType provider) {
        return new ProjectInfo(provider, projectId, name, description, iconUrl, pageUrl, versionId, versionNumber);
    }

    public String getCacheKey() { return cacheKey; }
    public boolean isFound() { return found; }
    public Instant getCheckedAt() { return checkedAt; }

    private static String cut(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }
}
