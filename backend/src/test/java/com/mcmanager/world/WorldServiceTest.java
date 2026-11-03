package com.mcmanager.world;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpStatus;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mcmanager.common.ApiException;
import com.mcmanager.config.AppProperties;
import com.mcmanager.settings.SettingsService;
import com.mcmanager.world.WorldDtos.WorldDetails;
import com.mcmanager.world.WorldDtos.WorldSummary;

class WorldServiceTest {

    @TempDir
    Path temp;

    private Path saves;
    private WorldService service;

    @BeforeEach
    void setUp() throws Exception {
        saves = temp.resolve("mc/saves").toAbsolutePath().normalize();
        Files.createDirectories(saves);
        AppProperties props = new AppProperties(temp.resolve("mc").toString(), "1.21", "fabric", "MODRINTH", null, 60,
                temp.resolve("data").toString(), null);
        service = new WorldService(new SettingsService(new ObjectMapper(), props));
    }

    /** A world whose level.dat wraps the values in "Data", as Minecraft writes it. */
    private Path world(String folder, Map<String, ?> data) throws Exception {
        Path dir = saves.resolve(folder);
        NbtWriter.write(dir.resolve("level.dat"), Map.of("Data", data));
        return dir;
    }

    private static void assertNotFound(Runnable call) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(ApiException.class,
                e -> assertThat(e.status()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    private static void assertUnprocessable(Runnable call) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(ApiException.class,
                e -> assertThat(e.status()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY));
    }

    // ---- list

    @Test
    void listIsEmptyWithoutASavesFolder() throws Exception {
        Files.delete(saves);
        assertThat(service.list()).isEmpty();
    }

    @Test
    void listOnlyIncludesVisibleFoldersWithALevelDat() throws Exception {
        world("real", Map.of("LevelName", "Real"));
        world(".hidden", Map.of("LevelName", "Hidden"));
        Files.createDirectories(saves.resolve("no-level-dat"));
        Files.writeString(saves.resolve("file.txt"), "x");

        assertThat(service.list()).extracting(WorldSummary::folder).containsExactly("real");
    }

    @Test
    void listShowsTheMostRecentlyPlayedFirst() throws Exception {
        world("old", Map.of("LastPlayed", 100L));
        world("new", Map.of("LastPlayed", 200L));
        world("never", Map.of("LevelName", "Never"));

        assertThat(service.list()).extracting(WorldSummary::folder).containsExactly("new", "old", "never");
    }

    @Test
    void summaryReadsTheWorldsDetails() throws Exception {
        Path dir = world("w", Map.of(
                "LevelName", "My World",
                "Version", Map.of("Name", "1.21.4"),
                "LastPlayed", 123L,
                "GameType", 1,
                "hardcore", (byte) 1));
        Files.writeString(dir.resolve("icon.png"), "png");

        WorldSummary s = service.list().get(0);

        assertThat(s).isEqualTo(new WorldSummary("w", "My World", "1.21.4", 123L, "creative", true, true));
    }

    @Test
    void summaryOfAnUnreadableWorldStillShowsTheFolder() throws Exception {
        Path dir = Files.createDirectories(saves.resolve("broken"));
        Files.writeString(dir.resolve("level.dat"), "not nbt");

        assertThat(service.list().get(0)).isEqualTo(new WorldSummary("broken", "broken", null, null, null, false, false));
    }

    @Test
    void levelDatWithoutTheDataWrapperIsAlsoRead() throws Exception {
        NbtWriter.write(saves.resolve("w/level.dat"), Map.of("LevelName", "Flat"));

        assertThat(service.list().get(0).name()).isEqualTo("Flat");
    }

    @Test
    void aBlankNameFallsBackToTheFolder() throws Exception {
        world("w", Map.of("LevelName", " "));
        assertThat(service.list().get(0).name()).isEqualTo("w");
    }

    @Test
    void gameModeHandlesNumbersTextAndUnknownValues() throws Exception {
        world("a-survival", Map.of("GameType", 0, "LastPlayed", 6L));
        world("b-adventure", Map.of("GameType", 2, "LastPlayed", 5L));
        world("c-spectator", Map.of("GameType", 3, "LastPlayed", 4L));
        world("d-text", Map.of("GameType", "custom", "LastPlayed", 3L));
        world("e-too-high", Map.of("GameType", 9, "LastPlayed", 2L));
        world("f-negative", Map.of("GameType", -1, "LastPlayed", 1L));

        assertThat(service.list()).extracting(WorldSummary::gameMode)
                .containsExactly("survival", "adventure", "spectator", "custom", null, null);
    }

    @Test
    void hardcoreIsAlsoReadFromTheNewerDifficultySettings() throws Exception {
        world("new", Map.of("difficulty_settings", Map.of("hardcore", (byte) 1)));
        assertThat(service.list().get(0).hardcore()).isTrue();
    }

    @Test
    void hardcoreDefaultsToFalse() throws Exception {
        world("w", Map.of("hardcore", (byte) 0));
        assertThat(service.list().get(0).hardcore()).isFalse();
    }

    // ---- details

    @Test
    void detailsReadTheClassicFormat() throws Exception {
        Map<String, Object> data = new HashMap<>();
        data.put("LevelName", "Classic");
        data.put("DataVersion", 3953);
        data.put("Version", Map.of("Id", 3953, "Name", "1.21", "Snapshot", (byte) 0));
        data.put("Difficulty", 2);
        data.put("allowCommands", (byte) 1);
        data.put("RandomSeed", 42L);
        data.put("SpawnX", 1);
        data.put("SpawnY", 64);
        data.put("SpawnZ", -3);
        data.put("Time", 48000L);
        data.put("DayTime", 6000L);
        data.put("raining", (byte) 1);
        data.put("thundering", (byte) 0);
        data.put("BorderSize", 1000.0);
        data.put("ServerBrands", List.of("fabric", "vanilla"));
        data.put("WasModded", (byte) 1);
        data.put("DataPacks", Map.of("Enabled", List.of("vanilla"), "Disabled", List.of("bundle")));
        data.put("enabled_features", List.of("minecraft:vanilla"));
        data.put("GameRules", Map.of("keepInventory", "true"));
        data.put("Player", Map.of(
                "Dimension", "minecraft:overworld",
                "Pos", List.of(1.5, 64.0, -2.5),
                "Health", 20.0f,
                "foodLevel", 18,
                "XpLevel", 7,
                "playerGameType", 0));
        Path dir = world("w", data);
        Files.createDirectories(dir.resolve("playerdata"));
        Files.writeString(dir.resolve("playerdata/a.dat"), "x");
        Files.writeString(dir.resolve("playerdata/b.dat"), "x");
        Files.writeString(dir.resolve("playerdata/ignored.txt"), "x");
        Files.createDirectories(dir.resolve("region"));
        Files.createDirectories(dir.resolve("DIM-1"));
        Files.createDirectories(dir.resolve("DIM1"));

        WorldDetails d = service.details("w");

        assertThat(d.summary().name()).isEqualTo("Classic");
        assertThat(d.path()).isEqualTo(dir.toString());
        assertThat(d.fileCount()).isEqualTo(4);
        assertThat(d.sizeBytes()).isGreaterThan(3);
        assertThat(d.levelDatModified()).isPositive();
        assertThat(d.dataVersion()).isEqualTo(3953);
        assertThat(d.versionId()).isEqualTo(3953);
        assertThat(d.snapshot()).isFalse();
        assertThat(d.difficulty()).isEqualTo("normal");
        assertThat(d.allowCommands()).isTrue();
        assertThat(d.seed()).isEqualTo("42");
        assertThat(d.spawn()).isEqualTo(new WorldDtos.Spawn(1, 64, -3));
        assertThat(d.gameTimeTicks()).isEqualTo(48000L);
        assertThat(d.dayTimeTicks()).isEqualTo(6000L);
        assertThat(d.daysPlayed()).isEqualTo(2.0);
        assertThat(d.raining()).isTrue();
        assertThat(d.thundering()).isFalse();
        assertThat(d.borderSize()).isEqualTo(1000.0);
        assertThat(d.serverBrands()).containsExactly("fabric", "vanilla");
        assertThat(d.wasModded()).isTrue();
        assertThat(d.enabledDatapacks()).containsExactly("vanilla");
        assertThat(d.disabledDatapacks()).containsExactly("bundle");
        assertThat(d.enabledFeatures()).containsExactly("minecraft:vanilla");
        assertThat(d.player()).isEqualTo(new WorldDtos.PlayerInfo("minecraft:overworld", 1.5, 64.0, -2.5, 20.0, 18, 7, "survival"));
        assertThat(d.playerCount()).isEqualTo(2);
        assertThat(d.dimensions()).containsExactly("overworld", "nether", "the_end");
        assertThat(d.gameRules().path("keepInventory").asText()).isEqualTo("true");
    }

    @Test
    void detailsReadTheNewerFormat() throws Exception {
        Path dir = world("w", Map.of(
                "difficulty_settings", Map.of("difficulty", "hard"),
                "WorldGenSettings", Map.of("seed", 7L),
                "spawn", Map.of("pos", new int[] { 4, 5, 6 })));
        Files.createDirectories(dir.resolve("dimensions/mymod/mining"));
        Files.createDirectories(dir.resolve("dimensions/mymod/skylands"));

        WorldDetails d = service.details("w");

        assertThat(d.difficulty()).isEqualTo("hard");
        assertThat(d.seed()).isEqualTo("7");
        assertThat(d.spawn()).isEqualTo(new WorldDtos.Spawn(4, 5, 6));
        assertThat(d.dimensions()).containsExactlyInAnyOrder("mymod:mining", "mymod:skylands");
    }

    @Test
    void missingValuesAreNullNotErrors() throws Exception {
        world("w", Map.of("LevelName", "Bare"));

        WorldDetails d = service.details("w");

        assertThat(d.dataVersion()).isNull();
        assertThat(d.versionId()).isNull();
        assertThat(d.snapshot()).isNull();
        assertThat(d.difficulty()).isNull();
        assertThat(d.allowCommands()).isNull();
        assertThat(d.seed()).isNull();
        assertThat(d.spawn()).isNull();
        assertThat(d.gameTimeTicks()).isNull();
        assertThat(d.dayTimeTicks()).isNull();
        assertThat(d.daysPlayed()).isNull();
        assertThat(d.raining()).isNull();
        assertThat(d.borderSize()).isNull();
        assertThat(d.serverBrands()).isEmpty();
        assertThat(d.player()).isNull();
        assertThat(d.playerCount()).isZero();
        assertThat(d.dimensions()).isEmpty();
    }

    @Test
    void outOfRangeDifficultyIsUnknown() throws Exception {
        world("high", Map.of("Difficulty", 9));
        world("negative", Map.of("Difficulty", -1));

        assertThat(service.details("high").difficulty()).isNull();
        assertThat(service.details("negative").difficulty()).isNull();
    }

    @Test
    void aSnapshotIsReported() throws Exception {
        world("w", Map.of("Version", Map.of("Snapshot", (byte) 1)));
        assertThat(service.details("w").snapshot()).isTrue();
    }

    @Test
    void aSpawnWithoutThreeCoordinatesIsIgnored() throws Exception {
        world("w", Map.of("spawn", Map.of("pos", new int[] { 1, 2 })));
        assertThat(service.details("w").spawn()).isNull();
    }

    @Test
    void theSeedCanComeFromASeparateFile() throws Exception {
        Path dir = world("w", Map.of("LevelName", "x"));
        NbtWriter.write(dir.resolve("data/minecraft/world_gen_settings.dat"), Map.of("data", Map.of("seed", 11L)));

        assertThat(service.details("w").seed()).isEqualTo("11");
    }

    @Test
    void theSeedFileMayNotHaveADataWrapperAndMayBeInTheOlderLocation() throws Exception {
        Path dir = world("w", Map.of("LevelName", "x"));
        NbtWriter.write(dir.resolve("data/world_gen_settings.dat"), Map.of("seed", 12L));

        assertThat(service.details("w").seed()).isEqualTo("12");
    }

    @Test
    void aBrokenSeedFileFallsThroughToTheNextOne() throws Exception {
        Path dir = world("w", Map.of("LevelName", "x"));
        Files.createDirectories(dir.resolve("data/minecraft"));
        Files.writeString(dir.resolve("data/minecraft/world_gen_settings.dat"), "broken");
        NbtWriter.write(dir.resolve("data/world_gen_settings.dat"), Map.of("seed", 13L));

        assertThat(service.details("w").seed()).isEqualTo("13");
    }

    @Test
    void aSeedFileWithoutASeedTriesTheNextOne() throws Exception {
        Path dir = world("w", Map.of("LevelName", "x"));
        NbtWriter.write(dir.resolve("data/minecraft/world_gen_settings.dat"), Map.of("other", 1));
        NbtWriter.write(dir.resolve("data/world_gen_settings.dat"), Map.of("seed", 14L));

        assertThat(service.details("w").seed()).isEqualTo("14");
    }

    @Test
    void thePlayerCanComeFromThePlayerdataFolder() throws Exception {
        Path dir = world("w", Map.of("LevelName", "x"));
        NbtWriter.write(dir.resolve("playerdata/abc.dat"), Map.of("Health", 5.0f, "playerGameType", 9, "Dimension", 0));

        WorldDtos.PlayerInfo p = service.details("w").player();

        assertThat(p.health()).isEqualTo(5.0);
        assertThat(p.gameMode()).isNull();
        assertThat(p.dimension()).isEqualTo("0");
        assertThat(p.x()).isNull();
    }

    @Test
    void anIncompletePlayerStillLoads() throws Exception {
        world("w", Map.of("Player", Map.of("Pos", List.of(1.0, 2.0), "playerGameType", -1)));

        WorldDtos.PlayerInfo p = service.details("w").player();

        assertThat(p.dimension()).isNull();
        assertThat(p.x()).isNull();
        assertThat(p.health()).isNull();
        assertThat(p.food()).isNull();
        assertThat(p.gameMode()).isNull();
    }

    @Test
    void anUnreadablePlayerFileIsIgnored() throws Exception {
        Path dir = world("w", Map.of("LevelName", "x"));
        Files.createDirectories(dir.resolve("playerdata"));
        Files.writeString(dir.resolve("playerdata/abc.dat"), "broken");

        WorldDetails d = service.details("w");

        assertThat(d.player()).isNull();
        assertThat(d.playerCount()).isEqualTo(1);
    }

    @Test
    void anEmptyPlayerdataFolderMeansNoPlayer() throws Exception {
        Path dir = world("w", Map.of("LevelName", "x"));
        Files.createDirectories(dir.resolve("playerdata"));

        assertThat(service.details("w").player()).isNull();
    }

    @Test
    void detailsOfAnUnreadableWorldAreUnprocessable() throws Exception {
        Path dir = Files.createDirectories(saves.resolve("broken"));
        Files.writeString(dir.resolve("level.dat"), "not nbt");

        assertUnprocessable(() -> service.details("broken"));
    }

    // ---- raw

    @Test
    void rawReturnsTheWholeTree() throws Exception {
        world("w", Map.of("LevelName", "Raw"));
        JsonNode raw = service.raw("w");
        assertThat(raw.at("/Data/LevelName").asText()).isEqualTo("Raw");
    }

    @Test
    void rawOfAnUnreadableWorldIsUnprocessable() throws Exception {
        Path dir = Files.createDirectories(saves.resolve("broken"));
        Files.writeString(dir.resolve("level.dat"), "not nbt");

        assertUnprocessable(() -> service.raw("broken"));
    }

    // ---- icon

    @Test
    void iconReturnsTheFile() throws Exception {
        Path dir = world("w", Map.of("LevelName", "x"));
        Path icon = Files.writeString(dir.resolve("icon.png"), "png");

        assertThat(service.icon("w")).isEqualTo(icon);
    }

    @Test
    void aMissingIconIsNotFound() throws Exception {
        world("w", Map.of("LevelName", "x"));
        assertNotFound(() -> service.icon("w"));
    }

    // ---- folder validation

    @Test
    void foldersOutsideSavesAreRefused() throws Exception {
        Files.createDirectories(temp.resolve("mc/secret"));
        Files.writeString(temp.resolve("mc/secret/level.dat"), "x");
        world("w", Map.of("LevelName", "x"));
        Files.createDirectories(saves.resolve("w/nested"));

        assertNotFound(() -> service.details("../secret"));
        assertNotFound(() -> service.raw("../secret"));
        assertNotFound(() -> service.icon("../secret"));
        assertNotFound(() -> service.details("w/nested"));
        assertNotFound(() -> service.details(".."));
        assertNotFound(() -> service.details(temp.resolve("mc/secret").toString()));
    }

    @Test
    @DisabledOnOs(value = OS.WINDOWS, disabledReason = "Known bug: resolveWorld calls Path.resolve before checking "
            + "the name, so a name Windows can't parse gives InvalidPathException (HTTP 500) instead of 404")
    void aBlankFolderNameIsNotFound() {
        assertNotFound(() -> service.details(" "));
    }

    @Test
    void blankAndMissingFoldersAreNotFound() throws Exception {
        assertNotFound(() -> service.details(""));
        assertNotFound(() -> service.details("does-not-exist"));
        Files.writeString(saves.resolve("file"), "x");
        assertNotFound(() -> service.details("file"));
    }
}
