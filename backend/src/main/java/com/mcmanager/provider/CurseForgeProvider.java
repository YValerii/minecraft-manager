package com.mcmanager.provider;

import static com.mcmanager.common.Util.blank;
import static com.mcmanager.common.Util.partition;
import static com.mcmanager.common.Util.text;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.mcmanager.common.ApiException;
import com.mcmanager.common.Http;
import com.mcmanager.content.ContentType;
import com.mcmanager.content.LocalFile;
import com.mcmanager.settings.SettingsDto;

/** CurseForge Core API: https://docs.curseforge.com — requires your own API key. */
@Component
public class CurseForgeProvider implements ContentProvider {

    private static final String API = "https://api.curseforge.com/v1";
    private static final int MINECRAFT_GAME_ID = 432;

    private final Http http;

    public CurseForgeProvider(Http http) {
        this.http = http;
    }

    @Override
    public ProviderType type() {
        return ProviderType.CURSEFORGE;
    }

    @Override
    public Map<String, ProjectInfo> identify(ContentType type, List<LocalFile> files, SettingsDto s) {
        Map<String, String> headers = headers(s);

        Map<Long, List<LocalFile>> byFingerprint = new LinkedHashMap<>();
        for (LocalFile f : files) {
            if (f.directory()) {
                continue;
            }
            Long fp = f.fingerprint().get();
            if (fp != null) {
                byFingerprint.computeIfAbsent(fp, k -> new ArrayList<>()).add(f);
            }
        }
        if (byFingerprint.isEmpty()) {
            return Map.of();
        }

        Map<Long, JsonNode> fileByFingerprint = new HashMap<>();
        Set<Long> modIds = new LinkedHashSet<>();
        for (List<Long> part : partition(new ArrayList<>(byFingerprint.keySet()), 200)) {
            JsonNode res = http.post(API + "/fingerprints/" + MINECRAFT_GAME_ID,
                    Map.of("fingerprints", part), headers);
            for (JsonNode match : res.path("data").path("exactMatches")) {
                JsonNode file = match.path("file");
                fileByFingerprint.put(file.path("fileFingerprint").asLong(), file);
                modIds.add(file.path("modId").asLong());
            }
        }

        Map<Long, JsonNode> mods = fetchMods(modIds, headers);
        Map<String, ProjectInfo> out = new HashMap<>();
        byFingerprint.forEach((fp, localFiles) -> {
            JsonNode file = fileByFingerprint.get(fp);
            if (file == null) {
                return;
            }
            JsonNode mod = mods.get(file.path("modId").asLong());
            if (mod == null) {
                return;
            }
            JsonNode logo = mod.path("logo");
            ProjectInfo info = new ProjectInfo(
                    ProviderType.CURSEFORGE,
                    String.valueOf(mod.path("id").asLong()),
                    text(mod, "name"),
                    text(mod, "summary"),
                    firstNonNull(text(logo, "thumbnailUrl"), text(logo, "url")),
                    text(mod.path("links"), "websiteUrl"),
                    String.valueOf(file.path("id").asLong()),
                    firstNonNull(text(file, "displayName"), text(file, "fileName")));
            for (LocalFile f : localFiles) {
                out.put(f.id(), info);
            }
        });
        return out;
    }

    @Override
    public Map<String, UpdateCheck> latest(ContentType type, List<LocalFile> files,
            Map<String, ProjectInfo> known, SettingsDto s) {
        Map<String, String> headers = headers(s);
        Set<Long> modIds = new LinkedHashSet<>();
        for (LocalFile f : files) {
            ProjectInfo p = known.get(f.id());
            if (p != null) {
                modIds.add(Long.parseLong(p.projectId()));
            }
        }
        Map<Long, JsonNode> mods = fetchMods(modIds, headers);
        // Filtering by loader only makes sense for mods
        Integer loaderId = type == ContentType.MODS ? loaderId(s.loader()) : null;

        Map<String, UpdateCheck> out = new HashMap<>();
        for (LocalFile f : files) {
            ProjectInfo p = known.get(f.id());
            if (p == null) {
                continue;
            }
            JsonNode mod = mods.get(Long.parseLong(p.projectId()));
            if (mod == null) {
                out.put(f.id(), UpdateCheck.unknown());
                continue;
            }
            long bestId = -1;
            String bestName = null;
            for (JsonNode idx : mod.path("latestFilesIndexes")) {
                if (!s.gameVersion().equals(text(idx, "gameVersion"))) {
                    continue;
                }
                if (loaderId != null && idx.path("modLoader").asInt(-1) != loaderId) {
                    continue;
                }
                if (idx.path("releaseType").asInt(1) > 2) { // 3 = alpha
                    continue;
                }
                long id = idx.path("fileId").asLong();
                if (id > bestId) {
                    bestId = id;
                    bestName = text(idx, "filename");
                }
            }
            if (bestId < 0) {
                out.put(f.id(), new UpdateCheck(UpdateStatus.NO_COMPATIBLE, null));
                continue;
            }
            long current = Long.parseLong(p.versionId());
            UpdateInfo info = new UpdateInfo(String.valueOf(bestId), stripExtension(bestName), bestName,
                    null, null, null, null);
            out.put(f.id(), new UpdateCheck(bestId > current ? UpdateStatus.UPDATE_AVAILABLE : UpdateStatus.UP_TO_DATE, info));
        }
        return out;
    }

    @Override
    public UpdateInfo prepareDownload(ProjectInfo project, UpdateInfo update, SettingsDto s) {
        JsonNode res = http.get(API + "/mods/" + project.projectId() + "/files/" + update.versionId(), headers(s));
        JsonNode file = res.path("data");
        long fileId = Long.parseLong(update.versionId());
        String fileName = firstNonNull(text(file, "fileName"), update.fileName());

        String url = text(file, "downloadUrl");
        if (url == null) {
            // The author disabled direct downloads via the API — try the public CDN instead.
            url = "https://edge.forgecdn.net/files/" + (fileId / 1000) + "/" + (fileId % 1000) + "/"
                    + URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");
        }
        String hash = null;
        String algo = null;
        for (JsonNode h : file.path("hashes")) {
            if (h.path("algo").asInt() == 1) { // 1 = SHA-1, 2 = MD5
                hash = text(h, "value");
                algo = "sha1";
            }
        }
        return new UpdateInfo(update.versionId(), update.versionNumber(), fileName, url, algo, hash, update.changelog());
    }

    // ------------------------------------------------------------------ helpers

    private Map<Long, JsonNode> fetchMods(Set<Long> ids, Map<String, String> headers) {
        Map<Long, JsonNode> out = new HashMap<>();
        for (List<Long> part : partition(new ArrayList<>(ids), 100)) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("modIds", part);
            body.put("filterPcOnly", true);
            JsonNode res = http.post(API + "/mods", body, headers);
            for (JsonNode mod : res.path("data")) {
                out.put(mod.path("id").asLong(), mod);
            }
        }
        return out;
    }

    private static Map<String, String> headers(SettingsDto s) {
        if (blank(s.curseforgeApiKey())) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "CurseForge requires an API key — set one in the settings (console.curseforge.com)");
        }
        return Map.of("x-api-key", s.curseforgeApiKey().trim());
    }

    /** ModLoaderType from the CurseForge API. */
    private static Integer loaderId(String loader) {
        return switch (loader == null ? "" : loader.toLowerCase()) {
            case "forge" -> 1;
            case "fabric" -> 4;
            case "quilt" -> 5;
            case "neoforge" -> 6;
            default -> null;
        };
    }

    private static String stripExtension(String name) {
        if (name == null) {
            return null;
        }
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    private static String firstNonNull(String a, String b) {
        return a != null ? a : b;
    }
}
