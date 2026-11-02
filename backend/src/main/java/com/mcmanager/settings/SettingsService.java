package com.mcmanager.settings;

import static com.mcmanager.common.Util.blank;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mcmanager.common.ApiException;
import com.mcmanager.config.AppProperties;
import com.mcmanager.content.ContentType;
import com.mcmanager.provider.ProviderType;

@Service
public class SettingsService {

    public record PathInfo(String key, String label, String path, boolean exists) {
    }

    private final SettingsRepository repo;
    private final AppProperties props;

    public SettingsService(SettingsRepository repo, AppProperties props) {
        this.repo = repo;
        this.props = props;
    }

    @Transactional
    public SettingsDto get() {
        SettingsEntity e = repo.findById(1L).orElseGet(() -> repo.save(defaults()));
        return toDto(e);
    }

    @Transactional
    public SettingsDto update(SettingsDto in) {
        SettingsEntity e = repo.findById(1L).orElseGet(this::defaults);
        e.setMinecraftPath(trim(in.minecraftPath()));
        e.setGameVersion(required(in.gameVersion(), "Minecraft version"));
        e.setLoader(required(in.loader(), "Mod loader").toLowerCase(Locale.ROOT));
        e.setProvider(parseProvider(in.provider()).name());
        e.setCurseforgeApiKey(trim(in.curseforgeApiKey()));
        e.setModsDir(orDefault(in.modsDir(), ContentType.MODS.defaultDir()));
        e.setResourcepacksDir(orDefault(in.resourcepacksDir(), ContentType.RESOURCEPACKS.defaultDir()));
        e.setShaderpacksDir(orDefault(in.shaderpacksDir(), ContentType.SHADERPACKS.defaultDir()));
        e.setDatapacksDir(orDefault(in.datapacksDir(), ContentType.DATAPACKS.defaultDir()));
        e.setSavesDir(orDefault(in.savesDir(), "saves"));
        return toDto(repo.save(e));
    }

    public Path minecraftRoot(SettingsDto s) {
        if (!blank(s.minecraftPath())) {
            return Path.of(s.minecraftPath().trim()).toAbsolutePath().normalize();
        }
        return detectMinecraftPath();
    }

    public Path dir(SettingsDto s, ContentType type) {
        String d = switch (type) {
            case MODS -> s.modsDir();
            case RESOURCEPACKS -> s.resourcepacksDir();
            case SHADERPACKS -> s.shaderpacksDir();
            case DATAPACKS -> s.datapacksDir();
        };
        return resolve(s, orDefault(d, type.defaultDir()));
    }

    public Path savesDir(SettingsDto s) {
        return resolve(s, orDefault(s.savesDir(), "saves"));
    }

    public List<PathInfo> paths() {
        SettingsDto s = get();
        List<PathInfo> out = new ArrayList<>();
        out.add(info("root", "Minecraft folder", minecraftRoot(s)));
        for (ContentType t : ContentType.values()) {
            out.add(info(t.slug(), t.slug(), dir(s, t)));
        }
        out.add(info("saves", "saves", savesDir(s)));
        return out;
    }

    private PathInfo info(String key, String label, Path p) {
        return new PathInfo(key, label, p.toString(), Files.isDirectory(p));
    }

    private Path resolve(SettingsDto s, String dir) {
        Path p = Path.of(dir.trim());
        Path resolved = p.isAbsolute() ? p : minecraftRoot(s).resolve(p);
        return resolved.toAbsolutePath().normalize();
    }

    private SettingsEntity defaults() {
        SettingsEntity e = new SettingsEntity();
        e.setMinecraftPath(trim(props.minecraftPath()));
        e.setGameVersion(orDefault(props.gameVersion(), "26.3"));
        e.setLoader(orDefault(props.loader(), "neoforge").toLowerCase(Locale.ROOT));
        e.setProvider(parseProvider(props.provider()).name());
        e.setCurseforgeApiKey(trim(props.curseforgeApiKey()));
        e.setModsDir(ContentType.MODS.defaultDir());
        e.setResourcepacksDir(ContentType.RESOURCEPACKS.defaultDir());
        e.setShaderpacksDir(ContentType.SHADERPACKS.defaultDir());
        e.setDatapacksDir(ContentType.DATAPACKS.defaultDir());
        e.setSavesDir("saves");
        return e;
    }

    private SettingsDto toDto(SettingsEntity e) {
        return new SettingsDto(
                blank(e.getMinecraftPath()) ? detectMinecraftPath().toString() : e.getMinecraftPath(),
                e.getGameVersion(), e.getLoader(), e.getProvider(), e.getCurseforgeApiKey(),
                e.getModsDir(), e.getResourcepacksDir(), e.getShaderpacksDir(),
                e.getDatapacksDir(), e.getSavesDir());
    }

    /** %APPDATA%\.minecraft on Windows, otherwise the typical paths for macOS/Linux. */
    static Path detectMinecraftPath() {
        String appData = System.getenv("APPDATA");
        if (!blank(appData)) {
            return Path.of(appData, ".minecraft");
        }
        String home = System.getProperty("user.home", ".");
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.contains("mac")) {
            return Path.of(home, "Library", "Application Support", "minecraft");
        }
        return Path.of(home, ".minecraft");
    }

    private static ProviderType parseProvider(String value) {
        if (blank(value)) {
            return ProviderType.MODRINTH;
        }
        try {
            return ProviderType.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Unknown source: " + value);
        }
    }

    private static String required(String v, String label) {
        if (blank(v)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Field \"" + label + "\" is required");
        }
        return v.trim();
    }

    private static String orDefault(String v, String def) {
        return blank(v) ? def : v.trim();
    }

    private static String trim(String v) {
        return blank(v) ? null : v.trim();
    }
}
