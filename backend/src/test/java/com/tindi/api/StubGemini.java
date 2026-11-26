package com.tindi.api;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import tools.jackson.databind.json.JsonMapper;

/** Stands in for the Gemini API in tests. Never talks to Google. Each test says what the next reply is. */
public final class StubGemini {
    public record Hit(String path, String query, String apiKeyHeader, String body) {}

    private record Reply(int status, String body, long delayMillis) {}

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static StubGemini instance;

    private final HttpServer server;
    private volatile Reply reply;
    public final List<Hit> hits = new CopyOnWriteArrayList<>();

    public static synchronized StubGemini get() {
        if (instance == null) instance = new StubGemini();
        return instance;
    }

    private StubGemini() {
        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        server.createContext("/v1beta/models/", this::handle);
        // A slow reply must not hold up the next test.
        server.setExecutor(Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "stub-gemini");
            t.setDaemon(true);
            return t;
        }));
        server.start();
        reset();
    }

    public String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/v1beta";
    }

    public void reset() {
        hits.clear();
        recipe(List.of(), List.of(), "NONE", "");
    }

    /** A successful reply holding this recipe. */
    public void recipe(List<String> ingredients, List<String> steps, String source, String note) {
        answerText(JSON.writeValueAsString(
                Map.of("ingredients", ingredients, "steps", steps, "source", source, "note", note)));
    }

    /** A successful reply whose steps are objects with labels. Each step map is sent exactly as given. */
    public void labelled(List<String> ingredients, List<? extends Map<String, ?>> steps) {
        answerText(JSON.writeValueAsString(
                Map.of("ingredients", ingredients, "steps", steps, "source", "PASTED", "note", "")));
    }

    /** A successful reply whose answer text is exactly this. */
    public void answerText(String text) {
        raw(200, JSON.writeValueAsString(Map.of("candidates", List.of(Map.of(
                "content", Map.of("role", "model", "parts", List.of(Map.of("text", text))),
                "finishReason", "STOP")))), 0);
    }

    public void raw(int status, String body, long delayMillis) {
        reply = new Reply(status, body, delayMillis);
    }

    private void handle(HttpExchange ex) throws IOException {
        Reply r = reply;
        hits.add(new Hit(ex.getRequestURI().getPath(), ex.getRequestURI().getRawQuery(),
                ex.getRequestHeaders().getFirst("x-goog-api-key"),
                new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)));
        try {
            Thread.sleep(r.delayMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        byte[] bytes = r.body().getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Content-Type", "application/json; charset=UTF-8");
        ex.sendResponseHeaders(r.status(), bytes.length);
        ex.getResponseBody().write(bytes);
        ex.close();
    }
}
