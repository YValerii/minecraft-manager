package com.mcmanager.settings;

import static com.mcmanager.common.Util.blank;

import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mcmanager.common.ApiException;
import com.mcmanager.common.JsonFileStore;
import com.mcmanager.config.AppProperties;
import com.mcmanager.content.ContentType;
import com.mcmanager.provider.ProviderType;

/** Settings persisted to a local JSON file (app.data-dir/settings.json) instead of a database. */
@Service
public class SettingsService {

    public record PathInfo(String key, String label, String path, boolean exists) {
    }

    private final ObjectMapper mapper;
    private final AppProperties props;
    private final Path file;

    /** As stored on disk — minecraftPath may be blank, meaning "auto-detect". */
    private volatile SettingsDto raw;

    public SettingsService(ObjectMapper mapper, AppProperties props) {
        this.mapper = mapper;
        this.props = props;
        this.file = Path.of(blankToDefault(props.dataDir(), "./data")).resolve("settings.json")
                .toAbsolutePath().normalize();
    }

    public SettingsDto get() {
        return toDto(raw());
    }

    public synchronized SettingsDto update(SettingsDto in) {
        SettingsDto next = new SettingsDto(
                trim(in.minecraftPath()),
                required(in.gameVersion(), "Minecraft version"),
                required(in.loader(), "Mod loader").toLowerCase(Locale.ROOT),
                parseProvider(in.provider()).name(),
                trim(in.curseforgeApiKey()),
                orDefault(in.modsDir(), ContentType.MODS.defaultDir()),
                orDefault(in.resourcepacksDir(), ContentType.RESOURCEPACKS.defaultDir()),
                orDefault(in.shaderpacksDir(), ContentType.SHADERPACKS.defaultDir()),
                orDefault(in.datapacksDir(), ContentType.DATAPACKS.defaultDir()),
                orDefault(in.savesDir(), "saves"));
        try {
            JsonFileStore.write(mapper, file, next);
        } catch (UncheckedIOException e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to save settings to " + file);
        }
        raw = next;
        return toDto(next);
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

    /** Loaded from disk on first use, or seeded from application.yml / environment variables. */
    private SettingsDto raw() {
        SettingsDto r = raw;
        if (r == null) {
            synchronized (this) {
                r = raw;
                if (r == null) {
                    SettingsDto loaded = JsonFileStore.read(mapper, file, SettingsDto.class);
                    r = loaded != null ? loaded : defaults();
                    raw = r;
                }
            }
        }
        return r;
    }

    private SettingsDto defaults() {
        return new SettingsDto(
                trim(props.minecraftPath()),
                orDefault(props.gameVersion(), "26.3"),
                orDefault(props.loader(), "neoforge").toLowerCase(Locale.ROOT),
                parseProvider(props.provider()).name(),
                trim(props.curseforgeApiKey()),
                ContentType.MODS.defaultDir(),
                ContentType.RESOURCEPACKS.defaultDir(),
                ContentType.SHADERPACKS.defaultDir(),
                ContentType.DATAPACKS.defaultDir(),
                "saves");
    }

    private SettingsDto toDto(SettingsDto s) {
        return blank(s.minecraftPath())
                ? new SettingsDto(detectMinecraftPath().toString(), s.gameVersion(), s.loader(), s.provider(),
                        s.curseforgeApiKey(), s.modsDir(), s.resourcepacksDir(), s.shaderpacksDir(),
                        s.datapacksDir(), s.savesDir())
                : s;
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

    private static String blankToDefault(String v, String def) {
        return blank(v) ? def : v.trim();
    }
}
