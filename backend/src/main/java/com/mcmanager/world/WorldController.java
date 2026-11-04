package com.mcmanager.world;

import java.time.Duration;
import java.util.List;

import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.JsonNode;
import com.mcmanager.common.ApiException;
import com.mcmanager.world.WorldDtos.WorldDetails;
import com.mcmanager.world.WorldDtos.WorldEdit;
import com.mcmanager.world.WorldDtos.WorldSummary;

@RestController
@RequestMapping("/api/worlds")
public class WorldController {

    private final WorldService service;

    public WorldController(WorldService service) {
        this.service = service;
    }

    @GetMapping
    public List<WorldSummary> list() {
        return service.list();
    }

    @GetMapping("/{folder}")
    public WorldDetails details(@PathVariable String folder) {
        return service.details(folder);
    }

    /**
     * Requiring JSON makes browsers send a CORS preflight, which is refused, so another website open
     * on this computer can't change a world.
     */
    @PatchMapping(value = "/{folder}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public WorldDetails edit(@PathVariable String folder, @RequestBody WorldEdit edit) {
        return service.edit(folder, edit);
    }

    @GetMapping("/{folder}/raw")
    public JsonNode raw(@PathVariable String folder) {
        return service.raw(folder);
    }

    @GetMapping("/{folder}/icon")
    public ResponseEntity<Resource> icon(@PathVariable String folder) {
        try {
            var path = service.icon(folder);
            return ResponseEntity.ok()
                    .cacheControl(CacheControl.maxAge(Duration.ofMinutes(10)))
                    .contentType(MediaType.IMAGE_PNG)
                    .body(new FileSystemResource(path));
        } catch (ApiException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }

    /** Removes the icon; Minecraft takes a new one the next time the world is played. */
    @DeleteMapping("/{folder}/icon")
    public WorldDetails resetIcon(@PathVariable String folder) {
        return service.resetIcon(folder);
    }
}
