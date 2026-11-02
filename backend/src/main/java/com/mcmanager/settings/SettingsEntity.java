package com.mcmanager.settings;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** The single row (id = 1) holding the app's settings. */
@Entity
@Table(name = "app_settings")
public class SettingsEntity {

    @Id
    private Long id = 1L;
    private String minecraftPath;
    private String gameVersion;
    private String loader;
    private String provider;
    private String curseforgeApiKey;
    private String modsDir;
    private String resourcepacksDir;
    private String shaderpacksDir;
    private String datapacksDir;
    private String savesDir;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getMinecraftPath() { return minecraftPath; }
    public void setMinecraftPath(String v) { this.minecraftPath = v; }
    public String getGameVersion() { return gameVersion; }
    public void setGameVersion(String v) { this.gameVersion = v; }
    public String getLoader() { return loader; }
    public void setLoader(String v) { this.loader = v; }
    public String getProvider() { return provider; }
    public void setProvider(String v) { this.provider = v; }
    public String getCurseforgeApiKey() { return curseforgeApiKey; }
    public void setCurseforgeApiKey(String v) { this.curseforgeApiKey = v; }
    public String getModsDir() { return modsDir; }
    public void setModsDir(String v) { this.modsDir = v; }
    public String getResourcepacksDir() { return resourcepacksDir; }
    public void setResourcepacksDir(String v) { this.resourcepacksDir = v; }
    public String getShaderpacksDir() { return shaderpacksDir; }
    public void setShaderpacksDir(String v) { this.shaderpacksDir = v; }
    public String getDatapacksDir() { return datapacksDir; }
    public void setDatapacksDir(String v) { this.datapacksDir = v; }
    public String getSavesDir() { return savesDir; }
    public void setSavesDir(String v) { this.savesDir = v; }
}
