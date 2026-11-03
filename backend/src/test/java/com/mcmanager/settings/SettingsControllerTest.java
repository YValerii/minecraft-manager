package com.mcmanager.settings;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.mcmanager.common.ApiException;

@WebMvcTest(SettingsController.class)
class SettingsControllerTest {

    private static final SettingsDto SETTINGS = new SettingsDto("/mc", "1.21", "fabric", "MODRINTH", null,
            "mods", "resourcepacks", "shaderpacks", "datapacks", "saves");

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private SettingsService service;

    @Test
    void getReturnsTheCurrentSettings() throws Exception {
        when(service.get()).thenReturn(SETTINGS);

        mvc.perform(get("/api/settings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.minecraftPath").value("/mc"))
                .andExpect(jsonPath("$.gameVersion").value("1.21"));
    }

    @Test
    void putSavesAndReturnsTheResult() throws Exception {
        when(service.update(any())).thenReturn(SETTINGS);

        mvc.perform(put("/api/settings").contentType(MediaType.APPLICATION_JSON).content("{\"gameVersion\":\"1.21\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.loader").value("fabric"));
    }

    @Test
    void validationErrorsReachTheClient() throws Exception {
        when(service.update(any())).thenThrow(new ApiException(HttpStatus.BAD_REQUEST, "Field is required"));

        mvc.perform(put("/api/settings").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Field is required"));
    }

    @Test
    void pathsListsResolvedFolders() throws Exception {
        when(service.paths()).thenReturn(List.of(new SettingsService.PathInfo("root", "Minecraft folder", "/mc", true)));

        mvc.perform(get("/api/settings/paths"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].key").value("root"))
                .andExpect(jsonPath("$[0].exists").value(true));
    }
}
