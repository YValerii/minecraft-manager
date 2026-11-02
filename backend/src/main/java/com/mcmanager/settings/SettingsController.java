package com.mcmanager.settings;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/settings")
public class SettingsController {

    private final SettingsService service;

    public SettingsController(SettingsService service) {
        this.service = service;
    }

    @GetMapping
    public SettingsDto get() {
        return service.get();
    }

    @PutMapping
    public SettingsDto update(@RequestBody SettingsDto dto) {
        return service.update(dto);
    }

    /** Actual paths after resolving relative folders, plus whether they exist. */
    @GetMapping("/paths")
    public List<SettingsService.PathInfo> paths() {
        return service.paths();
    }
}
