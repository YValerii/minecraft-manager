package com.mcmanager.world;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;

public final class WorldDtos {

    private WorldDtos() {
    }

    /** Data for a tile in the list. */
    public record WorldSummary(
            String folder,
            String name,
            String versionName,
            Long lastPlayed,
            String gameMode,
            boolean hardcore,
            boolean hasIcon) {
    }

    public record Spawn(Integer x, Integer y, Integer z) {
    }

    public record PlayerInfo(
            String dimension,
            Double x,
            Double y,
            Double z,
            Double health,
            Integer food,
            Integer xpLevel,
            String gameMode) {
    }

    /** Everything that could be pulled from level.dat and the world folder's structure. */
    public record WorldDetails(
            WorldSummary summary,
            String path,
            long sizeBytes,
            long fileCount,
            Long levelDatModified,
            Integer dataVersion,
            Integer versionId,
            Boolean snapshot,
            String difficulty,
            Boolean allowCommands,
            String seed,
            Spawn spawn,
            Long gameTimeTicks,
            Long dayTimeTicks,
            Double daysPlayed,
            Boolean raining,
            Boolean thundering,
            Double borderSize,
            List<String> serverBrands,
            Boolean wasModded,
            List<String> enabledDatapacks,
            List<String> disabledDatapacks,
            List<String> enabledFeatures,
            PlayerInfo player,
            int playerCount,
            List<String> dimensions,
            JsonNode gameRules) {
    }

    /** The changes to make to a world; a null field (or a game rule left out) stays as it is. */
    public record WorldEdit(
            String name,
            String gameMode,
            String difficulty,
            Boolean hardcore,
            Boolean allowCommands,
            Map<String, String> gameRules) {
    }
}
