package com.mcmanager.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Initial settings values (from application.yml / environment variables). */
@ConfigurationProperties(prefix = "app")
public record AppProperties(
        String minecraftPath,
        String gameVersion,
        String loader,
        String provider,
        String curseforgeApiKey,
        int cacheTtlMinutes,
        String dataDir) {
}
