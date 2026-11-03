package com.mcmanager.launcher;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/launcher")
public class LauncherController {

    public record LauncherStatus(boolean available) {
    }

    private final LauncherService service;

    public LauncherController(LauncherService service) {
        this.service = service;
    }

    @GetMapping
    public LauncherStatus status() {
        return new LauncherStatus(service.isAvailable());
    }

    /**
     * Requiring JSON makes browsers send a CORS preflight, which is refused, so another website open
     * on this computer can't start the launcher.
     */
    @PostMapping(value = "/launch", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void launch() {
        service.launch();
    }
}
