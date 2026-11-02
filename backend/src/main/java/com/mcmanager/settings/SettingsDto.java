package com.mcmanager.settings;

public record SettingsDto(
        String minecraftPath,
        String gameVersion,
        String loader,
        String provider,
        String curseforgeApiKey,
        String modsDir,
        String resourcepacksDir,
        String shaderpacksDir,
        String datapacksDir,
        String savesDir) {
}
