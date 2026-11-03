package com.mcmanager.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.config.annotation.ResourceChainRegistration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.resource.ResourceResolver;

class SpaConfigTest {

    @TempDir
    Path dir;

    private ResourceResolver resolver;

    @BeforeEach
    void captureResolver() {
        ResourceHandlerRegistry registry = mock(ResourceHandlerRegistry.class);
        ResourceHandlerRegistration registration = mock(ResourceHandlerRegistration.class);
        ResourceChainRegistration chain = mock(ResourceChainRegistration.class);
        when(registry.addResourceHandler(anyString())).thenReturn(registration);
        when(registration.addResourceLocations(anyString())).thenReturn(registration);
        when(registration.resourceChain(anyBoolean())).thenReturn(chain);
        when(chain.addResolver(any())).thenReturn(chain);

        new SpaConfig().addResourceHandlers(registry);

        verify(registry).addResourceHandler("/**");
        verify(registration).addResourceLocations("classpath:/static/");
        ArgumentCaptor<ResourceResolver> captor = ArgumentCaptor.forClass(ResourceResolver.class);
        verify(chain).addResolver(captor.capture());
        resolver = captor.getValue();
    }

    private Resource resolve(String path) {
        Resource location = new FileSystemResource(dir.toString() + "/");
        return ReflectionTestUtils.invokeMethod(resolver, "getResource", path, location);
    }

    @Test
    void anExistingFileIsServedAsIs() throws Exception {
        Files.writeString(dir.resolve("main.js"), "js");
        Files.writeString(dir.resolve("index.html"), "html");

        assertThat(resolve("main.js").getFilename()).isEqualTo("main.js");
    }

    @Test
    void anAngularRouteGetsIndexHtml() throws Exception {
        Files.writeString(dir.resolve("index.html"), "html");

        assertThat(resolve("settings").getFilename()).isEqualTo("index.html");
    }

    @Test
    void unknownApiPathsAreNotAnsweredWithTheUi() throws Exception {
        Files.writeString(dir.resolve("index.html"), "html");

        assertThat(resolve("api/nope")).isNull();
    }

    @Test
    void withoutABundledUiNothingIsServed() {
        assertThat(resolve("settings")).isNull();
    }
}
