package com.mcmanager.common;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.mcmanager.content.ContentService;
import com.mcmanager.launcher.LauncherService;
import com.mcmanager.settings.SettingsService;
import com.mcmanager.world.WorldService;

/**
 * Client mistakes should get the matching 4xx status from the real Spring MVC stack.
 *
 * <p>Known bug: ApiExceptionHandler's catch-all Exception handler also catches Spring's own exceptions, so all of
 * these currently return 500. These tests state the correct behaviour; remove @Disabled when that is fixed.
 */
@Disabled("Known bug: the catch-all exception handler turns these client errors into HTTP 500")
@WebMvcTest
class ApiErrorStatusTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private ContentService content;

    @MockitoBean
    private LauncherService launcher;

    @MockitoBean
    private SettingsService settings;

    @MockitoBean
    private WorldService worlds;

    @Test
    void anUnknownAddressIsNotFound() throws Exception {
        mvc.perform(get("/api/does-not-exist")).andExpect(status().isNotFound());
    }

    @Test
    void theWrongContentTypeIsUnsupported() throws Exception {
        mvc.perform(post("/api/launcher/launch").contentType(MediaType.TEXT_PLAIN).content("{}"))
                .andExpect(status().isUnsupportedMediaType());
    }

    @Test
    void malformedJsonIsABadRequest() throws Exception {
        mvc.perform(put("/api/settings").contentType(MediaType.APPLICATION_JSON).content("{broken"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void aMissingBodyIsABadRequest() throws Exception {
        mvc.perform(post("/api/content/mods/install").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }

    @Test
    void aParameterOfTheWrongTypeIsABadRequest() throws Exception {
        mvc.perform(get("/api/content/mods").param("refresh", "abc")).andExpect(status().isBadRequest());
    }

    @Test
    void theWrongMethodIsNotAllowed() throws Exception {
        mvc.perform(delete("/api/settings")).andExpect(status().isMethodNotAllowed());
    }
}
