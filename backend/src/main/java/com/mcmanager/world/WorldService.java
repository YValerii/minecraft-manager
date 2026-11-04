package com.mcmanager.world;

import static com.mcmanager.common.Util.blank;
import static com.mcmanager.common.Util.text;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.BooleanNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mcmanager.common.ApiException;
import com.mcmanager.settings.SettingsDto;
import com.mcmanager.settings.SettingsService;
import com.mcmanager.world.WorldDtos.PlayerInfo;
import com.mcmanager.world.WorldDtos.Spawn;
import com.mcmanager.world.WorldDtos.WorldDetails;
import com.mcmanager.world.WorldDtos.WorldEdit;
import com.mcmanager.world.WorldDtos.WorldSummary;

@Service
public class WorldService {

    private static final Logger log = LoggerFactory.getLogger(WorldService.class);
    static final String[] GAME_MODES = { "survival", "creative", "adventure", "spectator" };
    static final String[] DIFFICULTIES = { "peaceful", "easy", "normal", "hard" };

    private final SettingsService settings;

    public WorldService(SettingsService settings) {
        this.settings = settings;
    }

    // ------------------------------------------------------------------ list

    public List<WorldSummary> list() {
        Path saves = settings.savesDir(settings.get());
        List<WorldSummary> out = new ArrayList<>();
        if (!Files.isDirectory(saves)) {
            return out;
        }
        try (DirectoryStream<Path> dirs = Files.newDirectoryStream(saves, Files::isDirectory)) {
            for (Path dir : dirs) {
                if (Files.isRegularFile(dir.resolve("level.dat")) && !dir.getFileName().toString().startsWith(".")) {
                    out.add(summarize(dir));
                }
            }
        } catch (IOException e) {
            log.warn("Failed to read {}: {}", saves, e.getMessage());
        }
        out.sort(Comparator.comparing((WorldSummary w) -> w.lastPlayed() == null ? 0L : w.lastPlayed()).reversed());
        return out;
    }

    private WorldSummary summarize(Path dir) {
        String folder = dir.getFileName().toString();
        boolean icon = Files.isRegularFile(dir.resolve("icon.png"));
        JsonNode data = levelData(dir);
        if (data == null) {
            return new WorldSummary(folder, folder, null, null, null, false, icon);
        }
        return new WorldSummary(
                folder,
                firstText(text(data, "LevelName"), folder),
                text(data.path("Version"), "Name"),
                data.path("LastPlayed").isNumber() ? data.path("LastPlayed").asLong() : null,
                gameMode(data),
                hardcore(data),
                icon);
    }

    // ------------------------------------------------------------------ details

    public WorldDetails details(String folder) {
        Path dir = resolveWorld(folder);
        JsonNode data = levelData(dir);
        if (data == null) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "Failed to read this world's level.dat");
        }
        WorldSummary summary = summarize(dir);

        AtomicLong size = new AtomicLong();
        AtomicLong count = new AtomicLong();
        try {
            Files.walkFileTree(dir, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    size.addAndGet(attrs.size());
                    count.incrementAndGet();
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) {
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            log.warn("Failed to compute size of {}: {}", dir, e.getMessage());
        }

        JsonNode version = data.path("Version");
        JsonNode player = data.has("Player") ? data.get("Player") : firstPlayerData(dir);
        long time = data.path("Time").asLong(-1);

        return new WorldDetails(
                summary,
                dir.toString(),
                size.get(),
                count.get(),
                modified(dir.resolve("level.dat")),
                intOrNull(data.path("DataVersion")),
                intOrNull(version.path("Id")),
                version.has("Snapshot") ? version.path("Snapshot").asInt() != 0 : null,
                difficulty(data),
                flag(data, "allowCommands"),
                seed(dir, data),
                spawn(data),
                time >= 0 ? time : null,
                data.path("DayTime").isNumber() ? data.path("DayTime").asLong() : null,
                time >= 0 ? Math.round(time / 2400.0) / 10.0 : null,
                flag(data, "raining"),
                flag(data, "thundering"),
                data.path("BorderSize").isNumber() ? data.path("BorderSize").asDouble() : null,
                strings(data.path("ServerBrands")),
                flag(data, "WasModded"),
                strings(data.path("DataPacks").path("Enabled")),
                strings(data.path("DataPacks").path("Disabled")),
                strings(data.path("enabled_features")),
                player == null ? null : playerInfo(player),
                countFiles(dir.resolve("playerdata")),
                dimensions(dir),
                gameRules(dir, data));
    }

    /** The full NBT tree of level.dat, for free-form viewing. */
    public JsonNode raw(String folder) {
        Path dir = resolveWorld(folder);
        try {
            return Nbt.readGzipFile(dir.resolve("level.dat"));
        } catch (IOException e) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "Failed to read level.dat: " + e.getMessage());
        }
    }

    public Path icon(String folder) {
        Path icon = resolveWorld(folder).resolve("icon.png");
        if (!Files.isRegularFile(icon)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "No icon");
        }
        return icon;
    }

    // ------------------------------------------------------------------ editing

    public WorldDetails edit(String folder, WorldEdit edit) {
        Path dir = resolveWorld(folder);
        whileWorldIsClosed(dir, () -> WorldEditor.apply(dir, edit));
        return details(folder);
    }

    public WorldDetails resetIcon(String folder) {
        Path dir = resolveWorld(folder);
        whileWorldIsClosed(dir, () -> Files.deleteIfExists(dir.resolve("icon.png")));
        return details(folder);
    }

    private interface WorldChange {
        void run() throws IOException;
    }

    /** Minecraft holds a lock on session.lock while a world is open and would overwrite the change when saving. */
    private static void whileWorldIsClosed(Path dir, WorldChange change) {
        Path sessionLock = dir.resolve("session.lock");
        try {
            if (!Files.isRegularFile(sessionLock)) {
                change.run();
                return;
            }
            try (FileChannel channel = FileChannel.open(sessionLock, StandardOpenOption.WRITE);
                    FileLock lock = channel.tryLock()) {
                if (lock == null) {
                    throw worldInUse();
                }
                change.run();
            } catch (OverlappingFileLockException e) {
                throw worldInUse();
            }
        } catch (IOException e) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "Failed to change the world: " + e.getMessage());
        }
    }

    private static ApiException worldInUse() {
        return new ApiException(HttpStatus.CONFLICT, "This world is open in Minecraft. Close it first, then try again.");
    }

    // ------------------------------------------------------------------ field reading

    private JsonNode levelData(Path dir) {
        try {
            JsonNode root = Nbt.readGzipFile(dir.resolve("level.dat"));
            return root.has("Data") ? root.get("Data") : root;
        } catch (Exception e) {
            log.warn("level.dat in {} not read: {}", dir.getFileName(), e.getMessage());
            return null;
        }
    }

    private static String gameMode(JsonNode data) {
        JsonNode gt = data.path("GameType");
        if (gt.isNumber() && gt.asInt() >= 0 && gt.asInt() < GAME_MODES.length) {
            return GAME_MODES[gt.asInt()];
        }
        return gt.isTextual() ? gt.asText() : null;
    }

    private static boolean hardcore(JsonNode data) {
        Boolean h = flag(data, "hardcore");
        if (h == null) {
            h = flag(data.path("difficulty_settings"), "hardcore");
        }
        return Boolean.TRUE.equals(h);
    }

    private static String difficulty(JsonNode data) {
        JsonNode ds = data.path("difficulty_settings");
        if (ds.has("difficulty")) {
            return ds.path("difficulty").asText();
        }
        JsonNode d = data.path("Difficulty");
        if (d.isNumber() && d.asInt() >= 0 && d.asInt() < DIFFICULTIES.length) {
            return DIFFICULTIES[d.asInt()];
        }
        return null;
    }

    /** The seed lives in level.dat (older versions) or in a separate data/…/world_gen_settings.dat file (newer). */
    private String seed(Path dir, JsonNode data) {
        JsonNode s = data.path("WorldGenSettings").path("seed");
        if (s.isMissingNode()) {
            s = data.path("RandomSeed");
        }
        if (s.isMissingNode()) {
            for (String rel : List.of("data/minecraft/world_gen_settings.dat", "data/world_gen_settings.dat")) {
                Path p = dir.resolve(rel);
                if (Files.isRegularFile(p)) {
                    try {
                        JsonNode root = Nbt.readGzipFile(p);
                        JsonNode d = root.has("data") ? root.get("data") : root;
                        s = d.path("seed");
                        if (!s.isMissingNode()) {
                            break;
                        }
                    } catch (IOException e) {
                        log.debug("{} not read: {}", p, e.getMessage());
                    }
                }
            }
        }
        return s.isMissingNode() || s.isNull() ? null : s.asText();
    }

    /** Newer versions keep the rules in their own file, with booleans stored as bytes rather than "true"/"false". */
    private static JsonNode gameRules(Path dir, JsonNode data) {
        Path file = dir.resolve(WorldEditor.GAME_RULES_FILE);
        if (Files.isRegularFile(file)) {
            try {
                Map<String, Object> rules = WorldEditor.gameRulesIn(Nbt.readDocument(file));
                if (rules != null) {
                    ObjectNode out = JsonNodeFactory.instance.objectNode();
                    rules.forEach((name, tag) -> out.set(name,
                            tag instanceof Byte b ? BooleanNode.valueOf(b != 0) : Nbt.toJson(tag)));
                    return out;
                }
            } catch (IOException e) {
                log.debug("{} not read: {}", file, e.getMessage());
            }
        }
        return data.path("GameRules");
    }

    private static Spawn spawn(JsonNode data) {
        if (data.has("SpawnX")) {
            return new Spawn(data.path("SpawnX").asInt(), data.path("SpawnY").asInt(), data.path("SpawnZ").asInt());
        }
        JsonNode pos = data.path("spawn").path("pos");
        if (pos.isArray() && pos.size() == 3) {
            return new Spawn(pos.get(0).asInt(), pos.get(1).asInt(), pos.get(2).asInt());
        }
        return null;
    }

    private JsonNode firstPlayerData(Path dir) {
        Path playerdata = dir.resolve("playerdata");
        if (!Files.isDirectory(playerdata)) {
            return null;
        }
        try (DirectoryStream<Path> files = Files.newDirectoryStream(playerdata, "*.dat")) {
            for (Path f : files) {
                return Nbt.readGzipFile(f);
            }
        } catch (IOException e) {
            log.debug("playerdata not read: {}", e.getMessage());
        }
        return null;
    }

    private static PlayerInfo playerInfo(JsonNode p) {
        JsonNode pos = p.path("Pos");
        boolean hasPos = pos.isArray() && pos.size() == 3;
        JsonNode gm = p.path("playerGameType");
        String mode = gm.isNumber() && gm.asInt() >= 0 && gm.asInt() < GAME_MODES.length ? GAME_MODES[gm.asInt()] : null;
        return new PlayerInfo(
                p.path("Dimension").isMissingNode() ? null : p.path("Dimension").asText(),
                hasPos ? pos.get(0).asDouble() : null,
                hasPos ? pos.get(1).asDouble() : null,
                hasPos ? pos.get(2).asDouble() : null,
                p.path("Health").isNumber() ? p.path("Health").asDouble() : null,
                intOrNull(p.path("foodLevel")),
                intOrNull(p.path("XpLevel")),
                mode);
    }

    private static List<String> dimensions(Path dir) {
        List<String> dims = new ArrayList<>();
        if (Files.isDirectory(dir.resolve("region"))) {
            dims.add("overworld");
        }
        if (Files.isDirectory(dir.resolve("DIM-1"))) {
            dims.add("nether");
        }
        if (Files.isDirectory(dir.resolve("DIM1"))) {
            dims.add("the_end");
        }
        Path custom = dir.resolve("dimensions");
        if (Files.isDirectory(custom)) {
            try (var walk = Files.walk(custom, 2)) {
                walk.filter(p -> p.getNameCount() - custom.getNameCount() == 2 && Files.isDirectory(p))
                        .forEach(p -> dims.add(custom.relativize(p).toString().replace('\\', ':').replace('/', ':')));
            } catch (IOException e) {
                log.debug("dimensions not read: {}", e.getMessage());
            }
        }
        return dims;
    }

    private static int countFiles(Path dir) {
        if (!Files.isDirectory(dir)) {
            return 0;
        }
        try (DirectoryStream<Path> files = Files.newDirectoryStream(dir, "*.dat")) {
            int n = 0;
            for (Path ignored : files) {
                n++;
            }
            return n;
        } catch (IOException e) {
            return 0;
        }
    }

    // ------------------------------------------------------------------ utilities

    /** The world folder must be a direct child of saves — guards against ../ in the request. */
    private Path resolveWorld(String folder) {
        Path saves = settings.savesDir(settings.get());
        Path dir = saves.resolve(folder).normalize();
        if (blank(folder) || !saves.equals(dir.getParent()) || !Files.isDirectory(dir)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "World not found: " + folder);
        }
        return dir;
    }

    private static Long modified(Path p) {
        try {
            return Files.getLastModifiedTime(p).toMillis();
        } catch (IOException e) {
            return null;
        }
    }

    private static Boolean flag(JsonNode n, String field) {
        JsonNode v = n.path(field);
        if (v.isNumber()) {
            return v.asInt() != 0;
        }
        if (v.isBoolean()) {
            return v.asBoolean();
        }
        return null;
    }

    private static Integer intOrNull(JsonNode n) {
        return n.isNumber() ? n.asInt() : null;
    }

    private static List<String> strings(JsonNode arr) {
        List<String> out = new ArrayList<>();
        if (arr.isArray()) {
            arr.forEach(v -> out.add(v.asText()));
        }
        return out;
    }

    private static String firstText(String a, String b) {
        return blank(a) ? b : a;
    }
}
