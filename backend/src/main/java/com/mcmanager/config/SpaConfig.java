package com.mcmanager.config;

import java.io.IOException;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

/**
 * Serves the Angular UI when it is bundled into the jar (the "bundle-ui" Maven profile). Any path
 * that isn't a file is an Angular route such as /settings, so it gets index.html. Without a bundled
 * UI (Docker, development) these paths stay 404.
 */
@Configuration
public class SpaConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/")
                .resourceChain(true)
                .addResolver(new PathResourceResolver() {
                    @Override
                    protected Resource getResource(String path, Resource location) throws IOException {
                        Resource file = location.createRelative(path);
                        if (file.isReadable()) {
                            return file;
                        }
                        if (path.startsWith("api/")) {
                            return null;
                        }
                        Resource index = location.createRelative("index.html");
                        return index.isReadable() ? index : null;
                    }
                });
    }
}
