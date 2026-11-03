package com.mcmanager.content;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
import com.mcmanager.content.ContentDtos.ContentList;
import com.mcmanager.content.ContentDtos.Icon;
import com.mcmanager.content.ContentDtos.InstallResult;
import com.mcmanager.content.ContentDtos.UpdateItem;
import com.mcmanager.content.ContentDtos.UpdatesResult;
import com.mcmanager.provider.UpdateStatus;

@WebMvcTest(ContentController.class)
class ContentControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private ContentService service;

    @Test
    void listDefaultsToNoRefresh() throws Exception {
        when(service.list(ContentType.MODS, false)).thenReturn(new ContentList("/mods", true, "MODRINTH", List.of(), null));

        mvc.perform(get("/api/content/mods"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.directory").value("/mods"))
                .andExpect(jsonPath("$.directoryExists").value(true));
    }

    @Test
    void listPassesRefreshThrough() throws Exception {
        when(service.list(ContentType.SHADERPACKS, true)).thenReturn(new ContentList("/s", false, "MODRINTH", List.of(), "warn"));

        mvc.perform(get("/api/content/shaderpacks").param("refresh", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.warning").value("warn"));
    }

    @Test
    void anUnknownContentTypeIsNotFound() throws Exception {
        mvc.perform(get("/api/content/nope"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message", containsString("nope")));
        verifyNoInteractions(service);
    }

    @Test
    void updatesReturnStatuses() throws Exception {
        when(service.updates(ContentType.DATAPACKS, true)).thenReturn(new UpdatesResult(
                List.of(new UpdateItem("id", UpdateStatus.UPDATE_AVAILABLE, "2", "f.zip", "log")), null));

        mvc.perform(get("/api/content/datapacks/updates").param("refresh", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].status").value("UPDATE_AVAILABLE"))
                .andExpect(jsonPath("$.items[0].latestVersion").value("2"));
    }

    @Test
    void iconIsServedWithItsTypeAndCacheHeader() throws Exception {
        when(service.icon(ContentType.MODS, "mods/a.jar")).thenReturn(new Icon(new byte[] { 1, 2 }, "image/jpeg"));

        mvc.perform(get("/api/content/mods/icon").param("id", "mods/a.jar"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_JPEG))
                .andExpect(content().bytes(new byte[] { 1, 2 }))
                .andExpect(header().string("Cache-Control", containsString("max-age=3600")));
    }

    @Test
    void installUpdatesTheFile() throws Exception {
        when(service.install(ContentType.MODS, "mods/a.jar"))
                .thenReturn(new InstallResult("mods/a.jar", true, "Updated to 2", "b.jar"));

        mvc.perform(post("/api/content/mods/install").contentType(MediaType.APPLICATION_JSON)
                .content("{\"id\":\"mods/a.jar\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true))
                .andExpect(jsonPath("$.newFileName").value("b.jar"));
    }

    @Test
    void installWithoutAnIdIsABadRequest() throws Exception {
        mvc.perform(post("/api/content/mods/install").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("File not specified"));
        verifyNoInteractions(service);
    }

    @Test
    void aNullBodyIsRejectedAsWell() {
        ContentController controller = new ContentController(mock(ContentService.class));
        assertThatThrownBy(() -> controller.install("mods", null)).isInstanceOf(ApiException.class)
                .hasMessage("File not specified");
    }

    @Test
    void installFailuresReachTheClient() throws Exception {
        when(service.install(any(), any())).thenThrow(new ApiException(HttpStatus.CONFLICT, "No newer versions"));

        mvc.perform(post("/api/content/mods/install").contentType(MediaType.APPLICATION_JSON).content("{\"id\":\"x\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("No newer versions"));
    }

    @Test
    void installAllReturnsEveryResult() throws Exception {
        when(service.installAll(ContentType.RESOURCEPACKS))
                .thenReturn(List.of(new InstallResult("x", false, "failed", null)));

        mvc.perform(post("/api/content/resourcepacks/install-all"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].ok").value(false))
                .andExpect(jsonPath("$[0].message").value("failed"));
        verify(service).installAll(ContentType.RESOURCEPACKS);
    }

    @Test
    void unexpectedErrorsBecomeA500WithAMessage() throws Exception {
        when(service.list(any(), anyBoolean())).thenThrow(new IllegalStateException("boom"));

        mvc.perform(get("/api/content/mods"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("Internal error: boom"));
    }
}
