package com.mcmanager.content;

import java.time.Duration;
import java.util.List;

import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.mcmanager.common.ApiException;
import com.mcmanager.content.ContentDtos.ContentList;
import com.mcmanager.content.ContentDtos.IdRequest;
import com.mcmanager.content.ContentDtos.Icon;
import com.mcmanager.content.ContentDtos.InstallResult;
import com.mcmanager.content.ContentDtos.UpdatesResult;

/** type = mods | resourcepacks | shaderpacks | datapacks */
@RestController
@RequestMapping("/api/content/{type}")
public class ContentController {

    private final ContentService service;

    public ContentController(ContentService service) {
        this.service = service;
    }

    @GetMapping
    public ContentList list(@PathVariable String type, @RequestParam(defaultValue = "false") boolean refresh) {
        return service.list(ContentType.fromSlug(type), refresh);
    }

    @GetMapping("/updates")
    public UpdatesResult updates(@PathVariable String type, @RequestParam(defaultValue = "false") boolean refresh) {
        return service.updates(ContentType.fromSlug(type), refresh);
    }

    @GetMapping("/icon")
    public ResponseEntity<byte[]> icon(@PathVariable String type, @RequestParam String id) {
        Icon icon = service.icon(ContentType.fromSlug(type), id);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(Duration.ofHours(1)))
                .contentType(MediaType.parseMediaType(icon.contentType()))
                .body(icon.bytes());
    }

    @PostMapping("/install")
    public InstallResult install(@PathVariable String type, @RequestBody IdRequest body) {
        if (body == null || body.id() == null) {
            throw new ApiException(org.springframework.http.HttpStatus.BAD_REQUEST, "File not specified");
        }
        return service.install(ContentType.fromSlug(type), body.id());
    }

    @PostMapping("/install-all")
    public List<InstallResult> installAll(@PathVariable String type) {
        return service.installAll(ContentType.fromSlug(type));
    }
}
