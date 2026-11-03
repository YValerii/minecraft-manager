package com.mcmanager.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.awt.Desktop;
import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;
import org.springframework.mock.env.MockEnvironment;

/** Desktop is always mocked, so these tests never open a real browser. */
class BrowserLauncherTest {

    private final MockEnvironment env = new MockEnvironment().withProperty("local.server.port", "1234");
    private final URI url = URI.create("http://localhost:1234/");
    private ClassLoader originalLoader;
    private String originalHeadless;

    @TempDir
    Path dir;

    @BeforeEach
    void remember() {
        originalLoader = Thread.currentThread().getContextClassLoader();
        originalHeadless = System.getProperty("java.awt.headless");
    }

    @AfterEach
    void restore() {
        Thread.currentThread().setContextClassLoader(originalLoader);
        if (originalHeadless == null) {
            System.clearProperty("java.awt.headless");
        } else {
            System.setProperty("java.awt.headless", originalHeadless);
        }
    }

    /** Makes static/index.html visible on the classpath, as it is in the bundled jar. */
    private void bundleUi() throws IOException {
        Files.createDirectories(dir.resolve("static"));
        Files.writeString(dir.resolve("static/index.html"), "<html>");
        Thread.currentThread().setContextClassLoader(
                new URLClassLoader(new URL[] { dir.toUri().toURL() }, originalLoader));
    }

    @Test
    void withoutABundledUiNoBrowserIsOpened() {
        try (MockedStatic<Desktop> desktop = mockStatic(Desktop.class)) {
            new BrowserLauncher(env, true).open();
            desktop.verifyNoInteractions();
        }
    }

    @Test
    void whenDisabledNoBrowserIsOpened() throws Exception {
        bundleUi();
        try (MockedStatic<Desktop> desktop = mockStatic(Desktop.class)) {
            new BrowserLauncher(env, false).open();
            desktop.verifyNoInteractions();
        }
    }

    @Test
    void opensTheAppUrlInTheDefaultBrowser() throws Exception {
        bundleUi();
        Desktop mock = mock(Desktop.class);
        when(mock.isSupported(Desktop.Action.BROWSE)).thenReturn(true);
        try (MockedStatic<Desktop> desktop = mockStatic(Desktop.class)) {
            desktop.when(Desktop::isDesktopSupported).thenReturn(true);
            desktop.when(Desktop::getDesktop).thenReturn(mock);

            new BrowserLauncher(env, true).open();
        }
        verify(mock).browse(url);
    }

    @Test
    void doesNothingWhenThereIsNoDesktop() throws Exception {
        bundleUi();
        try (MockedStatic<Desktop> desktop = mockStatic(Desktop.class)) {
            desktop.when(Desktop::isDesktopSupported).thenReturn(false);

            new BrowserLauncher(env, true).open();

            desktop.verify(Desktop::getDesktop, never());
        }
    }

    @Test
    void doesNothingWhenBrowsingIsNotSupported() throws Exception {
        bundleUi();
        Desktop mock = mock(Desktop.class);
        when(mock.isSupported(Desktop.Action.BROWSE)).thenReturn(false);
        try (MockedStatic<Desktop> desktop = mockStatic(Desktop.class)) {
            desktop.when(Desktop::isDesktopSupported).thenReturn(true);
            desktop.when(Desktop::getDesktop).thenReturn(mock);

            new BrowserLauncher(env, true).open();
        }
        verify(mock, never()).browse(any());
    }

    @Test
    void aFailureToOpenTheBrowserDoesNotStopTheApp() throws Exception {
        bundleUi();
        Desktop mock = mock(Desktop.class);
        when(mock.isSupported(Desktop.Action.BROWSE)).thenReturn(true);
        doThrow(new IOException("no browser")).when(mock).browse(any());
        try (MockedStatic<Desktop> desktop = mockStatic(Desktop.class)) {
            desktop.when(Desktop::isDesktopSupported).thenReturn(true);
            desktop.when(Desktop::getDesktop).thenReturn(mock);

            assertThatCode(() -> new BrowserLauncher(env, true).open()).doesNotThrowAnyException();
        }
    }
}
