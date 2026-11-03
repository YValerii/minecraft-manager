package com.mcmanager.launcher;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.mcmanager.common.ApiException;

@WebMvcTest(LauncherController.class)
class LauncherControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private LauncherService service;

    @Test
    void statusReportsAvailability() throws Exception {
        when(service.isAvailable()).thenReturn(true);

        mvc.perform(get("/api/launcher"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(true));
    }

    @Test
    void launchStartsTheLauncher() throws Exception {
        mvc.perform(post("/api/launcher/launch").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isNoContent());
        verify(service).launch();
    }

    @Test
    void launchWithoutJsonDoesNotStartTheLauncher() throws Exception {
        mvc.perform(post("/api/launcher/launch").contentType(MediaType.TEXT_PLAIN).content("{}"));
        verify(service, never()).launch();
    }

    @Test
    void launchFailureReachesTheClient() throws Exception {
        doThrow(new ApiException(HttpStatus.NOT_FOUND, "not found")).when(service).launch();

        mvc.perform(post("/api/launcher/launch").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("not found"));
    }
}
