package com.mcmanager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.mockStatic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/** The whole application wired together, on temp folders and without any calls to the internet. */
@SpringBootTest(properties = "app.open-browser=false")
@AutoConfigureMockMvc
class McManagerApplicationTests {

    @TempDir
    static Path temp;

    @DynamicPropertySource
    static void folders(DynamicPropertyRegistry registry) {
        registry.add("app.minecraft-path", () -> temp.resolve("minecraft").toString());
        registry.add("app.data-dir", () -> temp.resolve("data").toString());
        // Whichever launcher this computer has must never be started by a test.
        registry.add("app.launcher-path", () -> temp.resolve("no-launcher").toString());
    }

    @Autowired
    private MockMvc mvc;

    @Test
    void servesTheApiEndToEnd() throws Exception {
        mvc.perform(get("/api/settings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.provider").value("MODRINTH"))
                .andExpect(jsonPath("$.minecraftPath", containsString("minecraft")));
        mvc.perform(get("/api/worlds")).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(0)));
        mvc.perform(get("/api/content/mods"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.directoryExists").value(false))
                .andExpect(jsonPath("$.items", hasSize(0)));
        mvc.perform(get("/api/settings/paths")).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(6)));
        mvc.perform(get("/api/launcher")).andExpect(status().isOk()).andExpect(jsonPath("$.available").value(false));
    }

    @Test
    void savedSettingsReachTheDataFolder() throws Exception {
        mvc.perform(put("/api/settings").contentType(MediaType.APPLICATION_JSON)
                .content("{\"gameVersion\":\"1.21\",\"loader\":\"FABRIC\",\"provider\":\"modrinth\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.loader").value("fabric"));
        assertThat(temp.resolve("data/settings.json")).isRegularFile();
    }

    @Test
    void launchingWithoutJsonIsRefused() throws Exception {
        mvc.perform(post("/api/launcher/launch").contentType(MediaType.TEXT_PLAIN).content("{}"))
                .andExpect(result -> assertThat(result.getResponse().getStatus()).isNotEqualTo(204));
    }

    @Test
    void mainStartsSpring() {
        try (MockedStatic<SpringApplication> spring = mockStatic(SpringApplication.class)) {
            String[] args = { "--x" };
            McManagerApplication.main(args);
            spring.verify(() -> SpringApplication.run(McManagerApplication.class, args));
        }
    }
}
