package com.mcmanager.config;

import java.io.IOException;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * For the desktop app: Electron holds this process's stdin open, so end-of-input means Electron is
 * gone, even if it crashed or was killed. Exit then, rather than running on invisibly and keeping
 * the install folder locked.
 */
@Component
@ConditionalOnProperty("app.exit-on-stdin-close")
public class StdinWatchdog implements ApplicationRunner {

    @Override
    public void run(ApplicationArguments args) {
        Thread watcher = new Thread(() -> {
            try {
                while (System.in.read() != -1) {
                    // Nothing is ever sent; only end-of-input matters.
                }
            } catch (IOException ignored) {
                // Treated the same as end-of-input.
            }
            System.exit(0);
        }, "stdin-watchdog");
        watcher.setDaemon(true);
        watcher.start();
    }
}
