package com.mcmanager.provider;

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
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.mcmanager.common.Http;
import com.mcmanager.content.ContentType;
import com.mcmanager.content.LocalFile;
import com.mcmanager.settings.SettingsDto;

@Component
public class ModrinthProvider implements ContentProvider {

    private static final String API = "https://api.modrinth.com/v2";
    private static final int BATCH = 100;

    private final Http http;

    public ModrinthProvider(Http http) {
        this.http = http;
    }

    @Override
    public ProviderType type() {
        return ProviderType.MODRINTH;
    }

    @Override
    public Map<String, ProjectInfo> identify(ContentType type, List<LocalFile> files, SettingsDto s) {
        Map<String, List<LocalFile>> bySha = groupBySha(files);
        if (bySha.isEmpty()) {
            return Map.of();
        }

        // 1) hash -> version (contains project_id)
        Map<String, JsonNode> versions = new HashMap<>();
        for (List<String> part : partition(new ArrayList<>(bySha.keySet()), BATCH)) {
            JsonNode res = http.post(API + "/version_files",
                    Map.of("hashes", part, "algorithm", "sha1"), Map.of());
            res.properties().forEach(e -> versions.put(e.getKey(), e.getValue()));
        }

        // 2) project_id -> project (name, icon, description)
        Set<String> projectIds = versions.values().stream()
                .map(v -> text(v, "project_id"))
                .filter(id -> id != null)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<String, JsonNode> projects = fetchProjects(projectIds);

        Map<String, ProjectInfo> out = new HashMap<>();
        versions.forEach((sha, version) -> {
            JsonNode project = projects.get(text(version, "project_id"));
            if (project == null) {
                return;
            }
            ProjectInfo info = new ProjectInfo(
                    ProviderType.MODRINTH,
                    text(project, "id"),
                    text(project, "title"),
                    text(project, "description"),
                    text(project, "icon_url"),
                    pageUrl(project),
                    text(version, "id"),
                    firstNonNull(text(version, "version_number"), text(version, "name")));
            for (LocalFile f : bySha.getOrDefault(sha, List.of())) {
                out.put(f.id(), info);
            }
        });
        return out;
    }

    @Override
    public Map<String, UpdateCheck> latest(ContentType type, List<LocalFile> files,
            Map<String, ProjectInfo> known, SettingsDto s) {
        List<LocalFile> identified = files.stream().filter(f -> known.containsKey(f.id())).toList();
        Map<String, List<LocalFile>> bySha = groupBySha(identified);
        Map<String, UpdateCheck> out = new HashMap<>();
        if (bySha.isEmpty()) {
            return out;
        }

        Map<String, JsonNode> latestBySha = new HashMap<>();
        for (List<String> part : partition(new ArrayList<>(bySha.keySet()), BATCH)) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("hashes", part);
            body.put("algorithm", "sha1");
            body.put("loaders", loaders(type, s));
            body.put("game_versions", List.of(s.gameVersion()));
            JsonNode res = http.post(API + "/version_files/update", body, Map.of());
            res.properties().forEach(e -> latestBySha.put(e.getKey(), e.getValue()));
        }

        for (LocalFile f : identified) {
            JsonNode latest = latestBySha.get(f.sha1());
            if (latest == null) {
                out.put(f.id(), new UpdateCheck(UpdateStatus.NO_COMPATIBLE, null));
                continue;
            }
            UpdateInfo info = toUpdateInfo(latest);
            boolean same = info.versionId() != null && info.versionId().equals(known.get(f.id()).versionId());
            out.put(f.id(), new UpdateCheck(same ? UpdateStatus.UP_TO_DATE : UpdateStatus.UPDATE_AVAILABLE, info));
        }
        return out;
    }

    // ------------------------------------------------------------------ helpers

    private Map<String, JsonNode> fetchProjects(Set<String> ids) {
        Map<String, JsonNode> out = new HashMap<>();
        for (List<String> part : partition(new ArrayList<>(ids), BATCH)) {
            String array = part.stream().map(id -> "\"" + id + "\"").collect(Collectors.joining(",", "[", "]"));
            JsonNode res = http.get(API + "/projects?ids=" + URLEncoder.encode(array, StandardCharsets.UTF_8), Map.of());
            res.forEach(p -> out.put(text(p, "id"), p));
        }
        return out;
    }

    private static Map<String, List<LocalFile>> groupBySha(List<LocalFile> files) {
        Map<String, List<LocalFile>> map = new LinkedHashMap<>();
        for (LocalFile f : files) {
            if (!f.directory() && f.sha1() != null) {
                map.computeIfAbsent(f.sha1(), k -> new ArrayList<>()).add(f);
            }
        }
        return map;
    }

    /** On Modrinth, the "loader" for packs and shaders is a separate set of values, not a mod loader. */
    private static List<String> loaders(ContentType type, SettingsDto s) {
        return switch (type) {
            case MODS -> List.of(s.loader());
            case RESOURCEPACKS -> List.of("minecraft");
            case SHADERPACKS -> List.of("iris", "optifine", "canvas", "vanilla");
            case DATAPACKS -> List.of("datapack");
        };
    }

    private static String pageUrl(JsonNode project) {
        String slug = firstNonNull(text(project, "slug"), text(project, "id"));
        String kind = firstNonNull(text(project, "project_type"), "mod");
        return "https://modrinth.com/" + kind + "/" + slug;
    }

    private static UpdateInfo toUpdateInfo(JsonNode version) {
        JsonNode file = null;
        for (JsonNode f : version.path("files")) {
            if (f.path("primary").asBoolean(false)) {
                file = f;
                break;
            }
        }
        if (file == null) {
            file = version.path("files").path(0);
        }
        String changelog = text(version, "changelog");
        if (changelog != null && changelog.length() > 4000) {
            changelog = changelog.substring(0, 4000) + "…";
        }
        return new UpdateInfo(
                text(version, "id"),
                firstNonNull(text(version, "version_number"), text(version, "name")),
                text(file, "filename"),
                text(file, "url"),
                "sha512",
                text(file.path("hashes"), "sha512"),
                changelog);
    }

    private static String firstNonNull(String a, String b) {
        return a != null ? a : b;
    }
}
