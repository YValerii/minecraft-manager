package com.mcmanager.config;

import java.awt.Desktop;
import java.net.URI;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * Started with java -jar, the bundled UI would otherwise give no sign that it's running, so open it
 * in the default browser. Electron turns this off because it has its own window.
 */
@Component
public class BrowserLauncher {

    private static final Logger log = LoggerFactory.getLogger(BrowserLauncher.class);

    private final Environment env;
    private final boolean enabled;

    public BrowserLauncher(Environment env, @Value("${app.open-browser:true}") boolean enabled) {
        this.env = env;
        this.enabled = enabled;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void open() {
        if (!new ClassPathResource("static/index.html").isReadable()) {
            return; // API only: Docker or development.
        }
        String url = "http://localhost:" + env.getProperty("local.server.port") + "/";
        log.info("Minecraft Manager is running at {}", url);
        if (!enabled) {
            return;
        }
        try {
            // Spring Boot defaults to headless, in which case Desktop isn't available.
            System.setProperty("java.awt.headless", "false");
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(URI.create(url));
            }
        } catch (Exception | LinkageError e) {
            log.info("Could not open a browser automatically: {}", e.toString());
        }
    }
}
