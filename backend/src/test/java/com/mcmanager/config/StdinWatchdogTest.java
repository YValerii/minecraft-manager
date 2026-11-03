package com.mcmanager.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

/**
 * End-of-input calls System.exit, which would kill the test JVM, so that part can't run here. This
 * checks the watcher waits on a background thread and doesn't keep the JVM alive.
 */
class StdinWatchdogTest {

    @Test
    void watchesStdinOnABackgroundDaemonThread() throws Exception {
        CountDownLatch reading = new CountDownLatch(1);
        InputStream original = System.in;
        System.setIn(new InputStream() {
            @Override
            public int read() {
                reading.countDown();
                try {
                    new CountDownLatch(1).await(); // never reaches end-of-input
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return 0;
            }
        });
        try {
            new StdinWatchdog().run(null);

            assertThat(reading.await(10, TimeUnit.SECONDS)).isTrue();
            Thread watcher = Thread.getAllStackTraces().keySet().stream()
                    .filter(t -> t.getName().equals("stdin-watchdog"))
                    .findFirst().orElseThrow();
            assertThat(watcher.isDaemon()).isTrue();
            assertThat(watcher.isAlive()).isTrue();
        } finally {
            System.setIn(original);
        }
    }
}
