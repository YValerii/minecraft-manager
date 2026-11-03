package com.mcmanager.launcher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import com.mcmanager.common.ApiException;
import com.mcmanager.config.AppProperties;

class LauncherServiceTest {

    private static final String STORE_PACKAGE = "Microsoft.4297127D64EC6_8wekyb3d8bbwe";

    private final Map<String, String> env = new HashMap<>();
    private String osName;
    private String userHome;

    @TempDir
    Path temp;

    @BeforeEach
    void remember() {
        osName = System.getProperty("os.name");
        userHome = System.getProperty("user.home");
        System.setProperty("user.home", temp.toString());
    }

    @AfterEach
    void restore() {
        System.setProperty("os.name", osName);
        System.setProperty("user.home", userHome);
    }

    private LauncherService service(String configured) {
        AppProperties props = new AppProperties(null, null, null, null, null, 60, null, configured);
        return new LauncherService(props, env::get);
    }

    @SuppressWarnings("unchecked")
    private List<String> command(String configured) {
        return (List<String>) ReflectionTestUtils.invokeMethod(service(configured), "findCommand");
    }

    private Path executable(Path file) throws Exception {
        Files.createDirectories(file.getParent());
        Files.writeString(file, "");
        file.toFile().setExecutable(true);
        return file;
    }

    /** Runs and exits by itself, so launching it is safe on every operating system. */
    private static String javaExecutable() {
        boolean windows = System.getProperty("os.name").toLowerCase().contains("win");
        return Path.of(System.getProperty("java.home"), "bin", windows ? "java.exe" : "java").toString();
    }

    // ---- configured path

    @Test
    void aConfiguredPathIsUsedAsIs() throws Exception {
        Path launcher = executable(temp.resolve("launcher"));
        System.setProperty("os.name", "Linux");

        assertThat(command(launcher.toString())).containsExactly(launcher.toString());
        assertThat(service(launcher.toString()).isAvailable()).isTrue();
    }

    @Test
    void aConfiguredPathThatDoesNotExistMeansUnavailable() {
        String missing = temp.resolve("missing").toString();
        assertThat(service(missing).isAvailable()).isFalse();
    }

    @Test
    void aConfiguredAppBundleIsOpenedOnMac() throws Exception {
        Path app = Files.createDirectories(temp.resolve("Minecraft.app"));
        System.setProperty("os.name", "Mac OS X");

        assertThat(command(app.toString())).containsExactly("open", app.toString());
    }

    @Test
    void aConfiguredNonAppPathOnMacIsRunDirectly() throws Exception {
        Path launcher = executable(temp.resolve("launcher"));
        System.setProperty("os.name", "Mac OS X");

        assertThat(command(launcher.toString())).containsExactly(launcher.toString());
    }

    @Test
    void aConfiguredAppBundleIsRunDirectlyElsewhere() throws Exception {
        Path app = Files.createDirectories(temp.resolve("Minecraft.app"));
        System.setProperty("os.name", "Linux");

        assertThat(command(app.toString())).containsExactly(app.toString());
    }

    // ---- Windows

    @Test
    void windowsPrefersTheStoreInstall() throws Exception {
        System.setProperty("os.name", "Windows 11");
        env.put("LOCALAPPDATA", temp.toString());
        Files.createDirectories(temp.resolve("Packages").resolve(STORE_PACKAGE));

        assertThat(command(null)).containsExactly("explorer.exe", "shell:AppsFolder\\" + STORE_PACKAGE + "!Minecraft");
    }

    @Test
    void windowsFindsTheClassicInstallInProgramFiles() throws Exception {
        System.setProperty("os.name", "Windows 11");
        env.put("LOCALAPPDATA", temp.toString());
        env.put("ProgramFiles", temp.toString());
        Path exe = executable(temp.resolve("Minecraft Launcher/MinecraftLauncher.exe"));

        assertThat(command(null)).containsExactly(exe.toString());
    }

    @Test
    void windowsChecksProgramFilesX86First() throws Exception {
        System.setProperty("os.name", "Windows 11");
        env.put("ProgramFiles(x86)", temp.resolve("x86").toString());
        env.put("ProgramFiles", temp.resolve("x64").toString());
        Path exe = executable(temp.resolve("x86/Minecraft Launcher/MinecraftLauncher.exe"));
        executable(temp.resolve("x64/Minecraft Launcher/MinecraftLauncher.exe"));

        assertThat(command(null)).containsExactly(exe.toString());
    }

    @Test
    void windowsWithoutAnInstallIsUnavailable() {
        System.setProperty("os.name", "Windows 11");
        env.put("ProgramFiles", temp.toString());

        assertThat(service(" ").isAvailable()).isFalse();
    }

    // ---- macOS

    @Test
    void macFindsTheAppInTheUsersApplicationsFolder() throws Exception {
        assumeFalse(Files.isDirectory(Path.of("/Applications/Minecraft.app")), "installed system-wide on this computer");
        System.setProperty("os.name", "Mac OS X");
        Path app = Files.createDirectories(temp.resolve("Applications/Minecraft.app"));

        assertThat(command(null)).containsExactly("open", app.toString());
    }

    @Test
    void macWithoutTheAppIsUnavailable() {
        assumeFalse(Files.isDirectory(Path.of("/Applications/Minecraft.app")), "installed system-wide on this computer");
        System.setProperty("os.name", "Mac OS X");

        assertThat(service(null).isAvailable()).isFalse();
    }

    // ---- Linux

    @Test
    void linuxSearchesThePath() throws Exception {
        System.setProperty("os.name", "Linux");
        Path launcher = executable(temp.resolve("bin/minecraft-launcher"));
        env.put("PATH", File.pathSeparator + temp.resolve("empty") + File.pathSeparator + temp.resolve("bin"));

        assertThat(command(null)).containsExactly(launcher.toString());
    }

    @Test
    void linuxFindsAFlatpakInstall() throws Exception {
        assumeSystemHasNoLinuxLauncher();
        System.setProperty("os.name", "Linux");
        Files.createDirectories(temp.resolve(".local/share/flatpak/app/com.mojang.Minecraft"));

        assertThat(command(null)).containsExactly("flatpak", "run", "com.mojang.Minecraft");
    }

    @Test
    void linuxWithoutAnInstallIsUnavailable() {
        assumeSystemHasNoLinuxLauncher();
        assumeFalse(Files.isDirectory(Path.of("/var/lib/flatpak/app/com.mojang.Minecraft")));
        System.setProperty("os.name", "Linux");

        assertThat(service(null).isAvailable()).isFalse();
    }

    private static void assumeSystemHasNoLinuxLauncher() {
        assumeFalse(Files.isExecutable(Path.of("/opt/minecraft-launcher/minecraft-launcher")),
                "installed system-wide on this computer");
    }

    // ---- launching

    @Test
    void launchStartsTheCommand() {
        assertThatCode(() -> service(javaExecutable()).launch()).doesNotThrowAnyException();
    }

    @Test
    void launchWithoutALauncherIsNotFound() {
        LauncherService service = service(temp.resolve("missing").toString());
        assertThatThrownBy(service::launch).isInstanceOfSatisfying(ApiException.class,
                e -> assertThat(e.status()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void aCommandThatCantStartIsAnInternalError() throws Exception {
        Path notRunnable = Files.writeString(temp.resolve("notes.txt"), "just text");
        LauncherService service = service(notRunnable.toString());

        assertThatThrownBy(service::launch).isInstanceOfSatisfying(ApiException.class, e -> {
            assertThat(e.status()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
            assertThat(e.getMessage()).contains("Could not start Minecraft Launcher");
        });
    }

    @Test
    void theSpringConstructorReadsTheRealEnvironment() {
        AppProperties props = new AppProperties(null, null, null, null, null, 60, null, temp.resolve("x").toString());
        assertThat(new LauncherService(props).isAvailable()).isFalse();
    }
}
