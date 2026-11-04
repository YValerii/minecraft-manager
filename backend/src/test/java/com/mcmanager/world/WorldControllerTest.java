package com.mcmanager.world;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mcmanager.common.ApiException;
import com.mcmanager.world.WorldDtos.WorldDetails;
import com.mcmanager.world.WorldDtos.WorldEdit;
import com.mcmanager.world.WorldDtos.WorldSummary;

@WebMvcTest(WorldController.class)
class WorldControllerTest {

    private static final WorldSummary SUMMARY = new WorldSummary("w", "World", "1.21", 5L, "survival", false, true);

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private WorldService service;

    @TempDir
    Path temp;

    @Test
    void listReturnsWorlds() throws Exception {
        when(service.list()).thenReturn(List.of(SUMMARY));

        mvc.perform(get("/api/worlds"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].folder").value("w"))
                .andExpect(jsonPath("$[0].hasIcon").value(true));
    }

    @Test
    void detailsReturnEverythingKnown() throws Exception {
        when(service.details("w")).thenReturn(new WorldDetails(SUMMARY, "/p", 10, 2, null, null, null, null, null,
                null, "1", null, null, null, null, null, null, null, List.of(), null, List.of(), List.of(), List.of(),
                null, 0, List.of("overworld"), null));

        mvc.perform(get("/api/worlds/w"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.name").value("World"))
                .andExpect(jsonPath("$.seed").value("1"))
                .andExpect(jsonPath("$.dimensions[0]").value("overworld"));
    }

    @Test
    void anUnknownWorldIsNotFoundWithAMessage() throws Exception {
        when(service.details("x")).thenThrow(new ApiException(HttpStatus.NOT_FOUND, "World not found: x"));

        mvc.perform(get("/api/worlds/x"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("World not found: x"));
    }

    @Test
    void rawReturnsTheTree() throws Exception {
        when(service.raw("w")).thenReturn(new ObjectMapper().readTree("{\"Data\":{\"a\":1}}"));

        mvc.perform(get("/api/worlds/w/raw"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.Data.a").value(1));
    }

    @Test
    void iconIsServedAsACacheablePng() throws Exception {
        Path icon = Files.writeString(temp.resolve("icon.png"), "png-bytes");
        when(service.icon("w")).thenReturn(icon);

        mvc.perform(get("/api/worlds/w/icon"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_PNG))
                .andExpect(content().string("png-bytes"))
                .andExpect(header().string("Cache-Control", containsString("max-age=600")));
    }

    @Test
    void aMissingIconIsAnEmptyNotFound() throws Exception {
        when(service.icon("w")).thenThrow(new ApiException(HttpStatus.NOT_FOUND, "No icon"));

        mvc.perform(get("/api/worlds/w/icon"))
                .andExpect(status().isNotFound())
                .andExpect(content().string(""));
    }

    @Test
    void editPassesTheChangesOnAndReturnsTheNewDetails() throws Exception {
        WorldEdit edit = new WorldEdit("New", null, null, null, true, Map.of("rule", "5"));
        when(service.edit("w", edit)).thenReturn(new WorldDetails(SUMMARY, "/p", 10, 2, null, null, null, null, null,
                null, "1", null, null, null, null, null, null, null, List.of(), null, List.of(), List.of(), List.of(),
                null, 0, List.of(), null));

        mvc.perform(patch("/api/worlds/w").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"New\",\"allowCommands\":true,\"gameRules\":{\"rule\":\"5\"}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.folder").value("w"));
    }

    @Test
    void editOnlyAcceptsJsonSoOtherSitesCannotCallIt() throws Exception {
        mvc.perform(patch("/api/worlds/w").contentType(MediaType.TEXT_PLAIN).content("{}"));
        verifyNoInteractions(service);
    }

    @Test
    void anEditOfAnOpenWorldReportsTheConflict() throws Exception {
        when(service.edit(eq("w"), any())).thenThrow(new ApiException(HttpStatus.CONFLICT, "Open"));

        mvc.perform(patch("/api/worlds/w").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Open"));
    }

    @Test
    void resetIconReturnsTheNewDetails() throws Exception {
        when(service.resetIcon("w")).thenReturn(new WorldDetails(SUMMARY, "/p", 10, 2, null, null, null, null, null,
                null, "1", null, null, null, null, null, null, null, List.of(), null, List.of(), List.of(), List.of(),
                null, 0, List.of(), null));

        mvc.perform(delete("/api/worlds/w/icon"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.path").value("/p"));
    }
}
