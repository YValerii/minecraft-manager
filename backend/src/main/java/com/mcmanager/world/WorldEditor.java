package com.mcmanager.world;

import static com.mcmanager.common.Util.blank;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;

import com.mcmanager.common.ApiException;
import com.mcmanager.world.Nbt.Document;
import com.mcmanager.world.WorldDtos.WorldEdit;

/**
 * Applies a {@link WorldEdit} to a world's NBT files, covering both the classic layout (everything in level.dat)
 * and the newer one (difficulty_settings, data/minecraft/game_rules.dat, players/data).
 *
 * <p>Every edit is made in memory first, so an invalid one leaves the disk untouched. As Minecraft does, the
 * previous version of each file is kept next to it with the suffix {@code _old}.
 */
final class WorldEditor {

    static final String GAME_RULES_FILE = "data/minecraft/game_rules.dat";

    private static final Logger log = LoggerFactory.getLogger(WorldEditor.class);
    private static final int MAX_NAME_LENGTH = 256;
    private static final List<String> PLAYER_DIRS = List.of("playerdata", "players/data");

    private WorldEditor() {
    }

    static void apply(Path dir, WorldEdit edit) throws IOException {
        Map<Path, Document> changed = new LinkedHashMap<>();
        Path levelFile = dir.resolve("level.dat");
        Document level = Nbt.readDocument(levelFile);
        Map<String, Object> data = dataOf(level);

        if (edit.name() != null) {
            data.put("LevelName", validName(edit.name()));
            changed.put(levelFile, level);
        }
        if (edit.gameMode() != null) {
            setGameMode(dir, data, edit.gameMode(), changed);
            changed.put(levelFile, level);
        }
        if (edit.difficulty() != null) {
            setDifficulty(data, edit.difficulty());
            changed.put(levelFile, level);
        }
        if (edit.hardcore() != null) {
            setHardcore(data, edit.hardcore());
            changed.put(levelFile, level);
        }
        if (edit.allowCommands() != null) {
            data.put("allowCommands", sameType(data.get("allowCommands"), (byte) 0, edit.allowCommands() ? 1 : 0));
            changed.put(levelFile, level);
        }
        if (edit.gameRules() != null && !edit.gameRules().isEmpty()) {
            setGameRules(dir, level, levelFile, edit.gameRules(), changed);
        }
        save(changed);
    }

    /** The rules inside a game_rules.dat file; null when it has none. */
    static Map<String, Object> gameRulesIn(Document gameRulesFile) {
        return compound(gameRulesFile.root(), "data");
    }

    // ------------------------------------------------------------------ fields

    private static String validName(String name) {
        String trimmed = name.trim();
        if (blank(trimmed)) {
            throw invalid("The world name can't be empty");
        }
        if (trimmed.length() > MAX_NAME_LENGTH) {
            throw invalid("The world name is too long");
        }
        return trimmed;
    }

    private static void setGameMode(Path dir, Map<String, Object> data, String mode, Map<Path, Document> changed)
            throws IOException {
        int index = Arrays.asList(WorldService.GAME_MODES).indexOf(mode);
        if (index < 0) {
            throw invalid("Unknown game mode: " + mode);
        }
        data.put("GameType", data.get("GameType") instanceof String ? mode : sameType(data.get("GameType"), 0, index));

        // The world's mode only applies to new players: existing ones keep their own, so it has to change there too.
        Map<String, Object> embedded = compound(data, "Player");
        if (embedded != null) {
            embedded.put("playerGameType", sameType(embedded.get("playerGameType"), 0, index));
            return;
        }
        for (Path file : playerFiles(dir)) {
            try {
                Document player = Nbt.readDocument(file);
                player.root().put("playerGameType", sameType(player.root().get("playerGameType"), 0, index));
                changed.put(file, player);
            } catch (IOException e) {
                log.warn("Player file {} left as it is: {}", file, e.getMessage());
            }
        }
    }

    private static List<Path> playerFiles(Path dir) throws IOException {
        List<Path> files = new ArrayList<>();
        for (String rel : PLAYER_DIRS) {
            Path players = dir.resolve(rel);
            if (Files.isDirectory(players)) {
                try (DirectoryStream<Path> stream = Files.newDirectoryStream(players, "*.dat")) {
                    stream.forEach(files::add);
                }
            }
        }
        return files;
    }

    private static void setDifficulty(Map<String, Object> data, String difficulty) {
        int index = Arrays.asList(WorldService.DIFFICULTIES).indexOf(difficulty);
        if (index < 0) {
            throw invalid("Unknown difficulty: " + difficulty);
        }
        Map<String, Object> settings = compound(data, "difficulty_settings");
        if (settings != null) {
            settings.put("difficulty", difficulty);
        }
        if (settings == null || data.containsKey("Difficulty")) {
            data.put("Difficulty", sameType(data.get("Difficulty"), (byte) 0, index));
        }
    }

    private static void setHardcore(Map<String, Object> data, boolean hardcore) {
        byte value = (byte) (hardcore ? 1 : 0);
        Map<String, Object> settings = compound(data, "difficulty_settings");
        if (settings != null) {
            settings.put("hardcore", value);
        }
        if (settings == null || data.containsKey("hardcore")) {
            data.put("hardcore", value);
        }
    }

    private static void setGameRules(Path dir, Document level, Path levelFile, Map<String, String> edits,
            Map<Path, Document> changed) throws IOException {
        Path rulesFile = dir.resolve(GAME_RULES_FILE);
        boolean ownFile = Files.isRegularFile(rulesFile);
        Document document = ownFile ? Nbt.readDocument(rulesFile) : level;
        Map<String, Object> rules = ownFile ? gameRulesIn(document) : compound(dataOf(level), "GameRules");
        if (rules == null) {
            throw invalid("This world has no game rules to edit");
        }
        for (Map.Entry<String, String> e : edits.entrySet()) {
            String name = e.getKey();
            if (!rules.containsKey(name)) {
                throw invalid("Unknown game rule: " + name);
            }
            rules.put(name, ruleValue(name, rules.get(name), e.getValue()));
        }
        changed.put(ownFile ? rulesFile : levelFile, document);
    }

    /** A rule keeps its tag type and kind: a boolean stays a boolean and a number stays a number. */
    private static Object ruleValue(String name, Object current, String value) {
        String text = value == null ? "" : value.trim();
        try {
            return switch (current) {
                case Byte b -> Byte.valueOf((byte) (parseBoolean(name, text) ? 1 : 0));
                case Short s -> Short.valueOf(text);
                case Integer i -> Integer.valueOf(text);
                case Long l -> Long.valueOf(text);
                case String s when isBoolean(s) -> Boolean.toString(parseBoolean(name, text));
                case String s when isInteger(s) -> Integer.toString(Integer.parseInt(text));
                case String s -> text;
                default -> throw invalid("The game rule " + name + " can't be edited");
            };
        } catch (NumberFormatException e) {
            throw invalid("The game rule " + name + " must be a whole number");
        }
    }

    private static boolean parseBoolean(String name, String text) {
        if (!isBoolean(text)) {
            throw invalid("The game rule " + name + " must be true or false");
        }
        return Boolean.parseBoolean(text);
    }

    private static boolean isBoolean(String text) {
        return "true".equals(text) || "false".equals(text);
    }

    private static boolean isInteger(String text) {
        return text.matches("-?\\d+");
    }

    // ------------------------------------------------------------------ files

    /** Writes and re-reads every file before replacing any, so a failure here can't leave the world half edited. */
    private static void save(Map<Path, Document> changed) throws IOException {
        List<Path> temporary = new ArrayList<>();
        try {
            for (Map.Entry<Path, Document> e : changed.entrySet()) {
                Path tmp = sibling(e.getKey(), ".tmp");
                temporary.add(tmp);
                Nbt.writeDocument(tmp, e.getValue());
                Nbt.readDocument(tmp);
            }
            for (Path file : changed.keySet()) {
                Files.copy(file, sibling(file, "_old"), StandardCopyOption.REPLACE_EXISTING);
                Files.move(sibling(file, ".tmp"), file, StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            }
        } finally {
            for (Path tmp : temporary) {
                Files.deleteIfExists(tmp);
            }
        }
    }

    private static Path sibling(Path file, String suffix) {
        return file.resolveSibling(file.getFileName() + suffix);
    }

    // ------------------------------------------------------------------ tags

    private static Map<String, Object> dataOf(Document level) {
        Map<String, Object> data = compound(level.root(), "Data");
        return data != null ? data : level.root();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> compound(Map<String, Object> parent, String name) {
        return parent.get(name) instanceof Map<?, ?> m ? (Map<String, Object>) m : null;
    }

    /** The value as the same tag type as the existing one, or as the fallback's type when there is no such number. */
    private static Object sameType(Object existing, Object fallback, int value) {
        Object sample = existing instanceof Byte || existing instanceof Short || existing instanceof Integer
                || existing instanceof Long ? existing : fallback;
        return switch (sample) {
            case Byte b -> Byte.valueOf((byte) value);
            case Short s -> Short.valueOf((short) value);
            case Long l -> Long.valueOf(value);
            default -> Integer.valueOf(value);
        };
    }

    private static ApiException invalid(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, message);
    }
}
