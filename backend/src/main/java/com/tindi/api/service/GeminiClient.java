package com.tindi.api.service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Thin client for the Gemini REST API (models.generateContent) with structured JSON output. One attempt, one
 * overall deadline. The API key goes in the x-goog-api-key header, so it is never part of a URL. Neither the
 * key nor any request or response text is ever logged.
 */
@Component
public class GeminiClient {
    private static final Logger log = LoggerFactory.getLogger(GeminiClient.class);
    private static final Pattern MODEL_NAME = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]*");
    private static final Pattern ERROR_STATUS = Pattern.compile("[A-Z_]{1,40}");
    static final String DEFAULT_MODEL = "gemini-3.5-flash-lite";

    /** Why there is no usable answer. The reason is a fixed code that is safe to log. */
    public static class Failure extends RuntimeException {
        private final String reason;

        public Failure(String reason) {
            super(reason, null, false, false);
            this.reason = reason;
        }

        public String reason() { return reason; }
    }

    private final String apiKey;
    private final String model;
    private final String baseUrl;
    private final Duration timeout;
    private final JsonMapper json;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    public GeminiClient(@Value("${tindi.gemini-api-key:}") String apiKey,
                        @Value("${tindi.gemini-model:}") String model,
                        @Value("${tindi.gemini-api-base:https://generativelanguage.googleapis.com/v1beta}") String baseUrl,
                        @Value("${tindi.gemini-timeout:20s}") Duration timeout,
                        JsonMapper json) {
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.model = model == null || model.isBlank() ? DEFAULT_MODEL : model.trim();
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.timeout = timeout;
        this.json = json;
    }

    /** A blank value or the placeholder from local.properties.example counts as "not set". */
    public boolean configured() {
        return !apiKey.isEmpty() && !apiKey.toUpperCase().startsWith("PASTE");
    }

    /**
     * Sends one generateContent request (temperature 0) and returns the text of the answer, which should be
     * JSON matching {@code schema}. The caller still has to validate it.
     *
     * @throws Failure for every way this can go wrong
     */
    public String generate(String systemInstruction, String userText, Map<String, Object> schema) {
        if (!configured()) throw new Failure("NOT_CONFIGURED");
        if (!MODEL_NAME.matcher(model).matches()) throw new Failure("BAD_MODEL_NAME");

        Map<String, Object> body = Map.of(
                "systemInstruction", Map.of("parts", List.of(Map.of("text", systemInstruction))),
                "contents", List.of(Map.of("role", "user", "parts", List.of(Map.of("text", userText)))),
                "generationConfig", Map.of(
                        "temperature", 0,
                        "responseFormat", Map.of("text", Map.of("mimeType", "APPLICATION_JSON", "schema", schema))));

        HttpResponse<String> res = send(json.writeValueAsString(body));
        int status = res.statusCode();
        if (status / 100 != 2) {
            String error = errorStatus(res.body());
            log.warn("Gemini API answered {} ({})", status, error);
            if (status == 429 || error.equals("RESOURCE_EXHAUSTED")) throw new Failure("QUOTA");
            throw new Failure("HTTP_" + status);
        }
        return answerText(res.body());
    }

    private HttpResponse<String> send(String body) {
        CompletableFuture<HttpResponse<String>> pending = null;
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(baseUrl + "/models/" + model + ":generateContent"))
                    .header("x-goog-api-key", apiKey)
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            pending = http.sendAsync(req, HttpResponse.BodyHandlers.ofString());
            // One deadline for the whole exchange. HttpRequest.timeout would stop counting at the response headers.
            return pending.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            pending.cancel(true);
            throw new Failure("TIMEOUT");
        } catch (InterruptedException e) {
            pending.cancel(true);
            Thread.currentThread().interrupt();
            throw new Failure("INTERRUPTED");
        } catch (ExecutionException e) {
            // Class names only: a message could carry request details.
            log.warn("Gemini request failed: {}", e.getCause().getClass().getSimpleName());
            throw new Failure(e.getCause() instanceof IOException ? "NETWORK" : "REQUEST_FAILED");
        } catch (RuntimeException e) {
            // For example a key with characters that are not allowed in a header. The message would contain it.
            log.warn("Gemini request could not be sent: {}", e.getClass().getSimpleName());
            throw new Failure("REQUEST_FAILED");
        }
    }

    private String answerText(String body) {
        JsonNode root;
        try {
            root = json.readTree(body);
        } catch (RuntimeException e) {
            throw new Failure("INVALID_RESPONSE");
        }
        if (!root.path("promptFeedback").path("blockReason").asString("").isEmpty()) throw new Failure("BLOCKED");
        JsonNode candidate = root.path("candidates").path(0);
        String finish = candidate.path("finishReason").asString("");
        if (!finish.isEmpty() && !finish.equals("STOP")) {
            if (finish.equals("MAX_TOKENS")) throw new Failure("TRUNCATED");
            throw new Failure(ERROR_STATUS.matcher(finish).matches() ? "FINISH_" + finish : "BLOCKED");
        }
        StringBuilder text = new StringBuilder();
        for (JsonNode part : candidate.path("content").path("parts")) {
            if (!part.path("thought").asBoolean(false)) text.append(part.path("text").asString(""));
        }
        if (text.toString().isBlank()) throw new Failure("INVALID_RESPONSE");
        return text.toString();
    }

    private String errorStatus(String body) {
        try {
            String status = json.readTree(body).path("error").path("status").asString("");
            return ERROR_STATUS.matcher(status).matches() ? status : "";
        } catch (RuntimeException e) {
            return "";
        }
    }
}
