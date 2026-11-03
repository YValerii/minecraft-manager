package com.mcmanager.launcher;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.mcmanager.common.ApiException;
import com.mcmanager.common.Util;
import com.mcmanager.config.AppProperties;

/** Finds the official Minecraft Launcher on this computer and starts it. */
@Service
public class LauncherService {

    /** Microsoft Store / Xbox app install. It has no directly runnable exe, so it's started by app ID. */
    private static final String WINDOWS_STORE_PACKAGE = "Microsoft.4297127D64EC6_8wekyb3d8bbwe";
    private static final String LINUX_COMMAND = "minecraft-launcher";
    private static final String FLATPAK_ID = "com.mojang.Minecraft";

    private final String configuredPath;

    public LauncherService(AppProperties props) {
        this.configuredPath = props.launcherPath();
    }

    public boolean isAvailable() {
        return findCommand() != null;
    }

    public void launch() {
        List<String> command = findCommand();
        if (command == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Minecraft Launcher was not found on this computer");
        }
        try {
            // Not waited for: the launcher keeps running on its own, also after this app is closed.
            new ProcessBuilder(command)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start();
        } catch (IOException e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Could not start Minecraft Launcher: " + e.getMessage());
        }
    }

    /** The command that starts the launcher, or null if it isn't installed. */
    private List<String> findCommand() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        boolean mac = os.contains("mac");
        if (!Util.blank(configuredPath)) {
            if (!Files.exists(Path.of(configuredPath))) {
                return null;
            }
            return mac && configuredPath.endsWith(".app") ? List.of("open", configuredPath) : List.of(configuredPath);
        }
        if (os.contains("win")) {
            return findOnWindows();
        }
        return mac ? findOnMac() : findOnLinux();
    }

    private List<String> findOnWindows() {
        String localAppData = System.getenv("LOCALAPPDATA");
        if (localAppData != null && Files.isDirectory(Path.of(localAppData, "Packages", WINDOWS_STORE_PACKAGE))) {
            return List.of("explorer.exe", "shell:AppsFolder\\" + WINDOWS_STORE_PACKAGE + "!Minecraft");
        }
        for (String variable : List.of("ProgramFiles(x86)", "ProgramFiles")) {
            String programFiles = System.getenv(variable);
            if (programFiles != null) {
                Path exe = Path.of(programFiles, "Minecraft Launcher", "MinecraftLauncher.exe");
                if (Files.isRegularFile(exe)) {
                    return List.of(exe.toString());
                }
            }
        }
        return null;
    }

    private List<String> findOnMac() {
        String home = System.getProperty("user.home", "");
        for (Path app : List.of(Path.of("/Applications/Minecraft.app"), Path.of(home, "Applications", "Minecraft.app"))) {
            if (Files.isDirectory(app)) {
                return List.of("open", app.toString());
            }
        }
        return null;
    }

    private List<String> findOnLinux() {
        String path = System.getenv("PATH");
        if (path != null) {
            for (String dir : path.split(File.pathSeparator)) {
                if (!dir.isEmpty() && Files.isExecutable(Path.of(dir, LINUX_COMMAND))) {
                    return List.of(Path.of(dir, LINUX_COMMAND).toString());
                }
            }
        }
        Path opt = Path.of("/opt/minecraft-launcher/minecraft-launcher");
        if (Files.isExecutable(opt)) {
            return List.of(opt.toString());
        }
        String home = System.getProperty("user.home", "");
        if (Files.isDirectory(Path.of(home, ".local/share/flatpak/app", FLATPAK_ID))
                || Files.isDirectory(Path.of("/var/lib/flatpak/app", FLATPAK_ID))) {
            return List.of("flatpak", "run", FLATPAK_ID);
        }
        return null;
    }
}
