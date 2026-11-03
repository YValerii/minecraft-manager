package com.mcmanager.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpStatus;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mcmanager.config.HttpConfig;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/** Uses a real HTTP server on localhost, so requests and responses are checked for real. */
class HttpTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final Map<String, String> seen = new ConcurrentHashMap<>();
    private HttpServer server;
    private String base;
    private Http http;

    @TempDir
    Path dir;

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/json", e -> {
            seen.put("method", e.getRequestMethod());
            seen.put("agent", e.getRequestHeaders().getFirst("User-Agent"));
            seen.put("accept", e.getRequestHeaders().getFirst("Accept"));
            seen.put("key", String.valueOf(e.getRequestHeaders().getFirst("x-api-key")));
            seen.put("type", String.valueOf(e.getRequestHeaders().getFirst("Content-Type")));
            seen.put("body", new String(e.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            respond(e, 200, "{\"ok\":true}");
        });
        server.createContext("/fail", e -> respond(e, 503, "x".repeat(500)));
        server.createContext("/file", e -> respond(e, 200, "content"));
        server.createContext("/missing", e -> respond(e, 404, "gone"));
        server.start();
        base = "http://127.0.0.1:" + server.getAddress().getPort();
        http = new Http(new HttpConfig().httpClient(), mapper);
    }

    @AfterEach
    void stop() {
        server.stop(0);
        Thread.interrupted(); // don't leak the flag some tests set into the next one
    }

    private static void respond(HttpExchange e, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        e.sendResponseHeaders(status, bytes.length);
        e.getResponseBody().write(bytes);
        e.close();
    }

    @Test
    void getSendsIdentifyingHeadersAndParsesJson() {
        JsonNode res = http.get(base + "/json", Map.of("x-api-key", "secret"));

        assertThat(res.path("ok").asBoolean()).isTrue();
        assertThat(seen).containsEntry("method", "GET")
                .containsEntry("agent", HttpConfig.USER_AGENT)
                .containsEntry("accept", "application/json")
                .containsEntry("key", "secret");
    }

    @Test
    void postSendsTheBodyAsJson() {
        http.post(base + "/json", Map.of("hashes", List.of("a")), Map.of());

        assertThat(seen).containsEntry("method", "POST")
                .containsEntry("type", "application/json")
                .containsEntry("body", "{\"hashes\":[\"a\"]}");
    }

    @Test
    void errorResponseBecomesBadGatewayWithATruncatedBody() {
        assertThatThrownBy(() -> http.get(base + "/fail", Map.of()))
                .isInstanceOfSatisfying(ApiException.class, e -> {
                    assertThat(e.status()).isEqualTo(HttpStatus.BAD_GATEWAY);
                    assertThat(e.getMessage()).contains("127.0.0.1").contains("HTTP 503");
                    assertThat(e.getMessage()).endsWith(" " + "x".repeat(200));
                });
    }

    @Test
    @SuppressWarnings("unchecked")
    void errorResponseWithoutABodyIsStillReported() throws Exception {
        HttpClient client = mock(HttpClient.class);
        HttpResponse<Object> resp = mock(HttpResponse.class);
        when(resp.statusCode()).thenReturn(500);
        when(resp.body()).thenReturn(null);
        when(client.send(any(), any())).thenReturn(resp);

        assertThatThrownBy(() -> new Http(client, mapper).get(base + "/json", Map.of()))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("HTTP 500");
    }

    @Test
    void connectionFailureBecomesBadGateway() throws Exception {
        HttpClient client = mock(HttpClient.class);
        when(client.send(any(), any())).thenThrow(new IOException("refused"));

        assertThatThrownBy(() -> new Http(client, mapper).get(base + "/json", Map.of()))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Could not reach 127.0.0.1")
                .hasMessageContaining("refused");
    }

    @Test
    void interruptedRequestKeepsTheInterruptFlag() throws Exception {
        HttpClient client = mock(HttpClient.class);
        when(client.send(any(), any())).thenThrow(new InterruptedException());

        assertThatThrownBy(() -> new Http(client, mapper).get(base + "/json", Map.of()))
                .isInstanceOf(ApiException.class)
                .hasMessage("Request interrupted");
        assertThat(Thread.interrupted()).isTrue();
    }

    @Test
    void unserializableBodyIsReported() throws Exception {
        ObjectMapper broken = mock(ObjectMapper.class);
        when(broken.writeValueAsString(any())).thenThrow(new JsonProcessingException("nope") {
        });

        assertThatThrownBy(() -> new Http(mock(HttpClient.class), broken).post(base, Map.of(), Map.of()))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Failed to build request");
    }

    @Test
    void downloadWritesTheFile() {
        Path target = dir.resolve("out.jar");
        http.download(base + "/file", target);
        assertThat(target).hasContent("content");
    }

    @Test
    void downloadFailsOnAnErrorStatus() {
        Path target = dir.resolve("out.jar");
        assertThatThrownBy(() -> http.download(base + "/missing", target))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("HTTP 404");
    }

    @Test
    void downloadReportsConnectionFailures() throws Exception {
        HttpClient client = mock(HttpClient.class);
        when(client.send(any(), any())).thenThrow(new IOException("reset"));

        assertThatThrownBy(() -> new Http(client, mapper).download(base + "/file", dir.resolve("x")))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Download error: reset");
    }

    @Test
    void interruptedDownloadKeepsTheInterruptFlag() throws Exception {
        HttpClient client = mock(HttpClient.class);
        when(client.send(any(), any())).thenThrow(new InterruptedException());

        assertThatThrownBy(() -> new Http(client, mapper).download(base + "/file", dir.resolve("x")))
                .isInstanceOf(ApiException.class)
                .hasMessage("Download interrupted");
        assertThat(Thread.interrupted()).isTrue();
    }
}
