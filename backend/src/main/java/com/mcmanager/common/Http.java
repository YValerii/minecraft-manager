package com.mcmanager.common;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mcmanager.config.HttpConfig;

/** Thin wrapper over java.net.http for JSON requests to Modrinth / CurseForge. */
@Component
public class Http {

    private final HttpClient client;
    private final ObjectMapper mapper;

    public Http(HttpClient client, ObjectMapper mapper) {
        this.client = client;
        this.mapper = mapper;
    }

    public JsonNode get(String url, Map<String, String> headers) {
        HttpRequest.Builder b = base(url, headers).GET();
        return sendJson(b.build());
    }

    public JsonNode post(String url, Object body, Map<String, String> headers) {
        try {
            HttpRequest.Builder b = base(url, headers)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)));
            return sendJson(b.build());
        } catch (IOException e) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "Failed to build request: " + e.getMessage());
        }
    }

    public void download(String url, Path target) {
        HttpRequest req = base(url, Map.of()).timeout(Duration.ofMinutes(10)).GET().build();
        try {
            HttpResponse<Path> resp = client.send(req, HttpResponse.BodyHandlers.ofFile(target));
            if (resp.statusCode() / 100 != 2) {
                throw new ApiException(HttpStatus.BAD_GATEWAY, "Download failed, HTTP " + resp.statusCode());
            }
        } catch (IOException e) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "Download error: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ApiException(HttpStatus.BAD_GATEWAY, "Download interrupted");
        }
    }

    private HttpRequest.Builder base(String url, Map<String, String> headers) {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(60))
                .header("User-Agent", HttpConfig.USER_AGENT)
                .header("Accept", "application/json");
        headers.forEach(b::header);
        return b;
    }

    private JsonNode sendJson(HttpRequest req) {
        try {
            HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() / 100 != 2) {
                String body = resp.body() == null ? "" : resp.body();
                if (body.length() > 200) {
                    body = body.substring(0, 200);
                }
                throw new ApiException(HttpStatus.BAD_GATEWAY,
                        "Error from " + req.uri().getHost() + ": HTTP " + resp.statusCode() + " " + body);
            }
            return mapper.readTree(resp.body());
        } catch (IOException e) {
            throw new ApiException(HttpStatus.BAD_GATEWAY,
                    "Could not reach " + req.uri().getHost() + ": " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ApiException(HttpStatus.BAD_GATEWAY, "Request interrupted");
        }
    }
}
