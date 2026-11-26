package com.tindi.api.service;

import com.tindi.api.web.ApiException;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Thin client for the official YouTube Data API v3 (videos.list and commentThreads.list). One attempt per
 * call, short timeouts. The API key goes in the X-Goog-Api-Key header, so it is never part of a URL, and it is
 * never logged.
 */
@Component
public class YouTubeClient {
    private static final Logger log = LoggerFactory.getLogger(YouTubeClient.class);

    public record Video(String id, String title, String channelId, String channelName, String thumbnailUrl,
                        String description) {}

    public record Comment(String author, String authorChannelId, String text, int likeCount) {}

    private final String apiKey;
    private final String baseUrl;
    private final JsonMapper json;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    public YouTubeClient(@Value("${tindi.youtube-api-key:}") String apiKey,
                         @Value("${tindi.youtube-api-base:https://www.googleapis.com/youtube/v3}") String baseUrl,
                         JsonMapper json) {
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.json = json;
    }

    /** A blank value or the placeholder from local.properties.example counts as "not set". */
    public boolean configured() {
        return !apiKey.isEmpty() && !apiKey.toUpperCase().startsWith("PASTE");
    }

    public Video video(String videoId) {
        JsonNode body = get("/videos", "part=snippet&id=" + enc(videoId));
        JsonNode items = body.path("items");
        if (!items.isArray() || items.size() == 0) {
            throw new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "That video was not found or is private", null);
        }
        JsonNode item = items.get(0);
        JsonNode s = item.path("snippet");
        return new Video(videoId, s.path("title").asString(""), s.path("channelId").asString(""),
                s.path("channelTitle").asString(""), bestThumbnail(s.path("thumbnails")),
                s.path("description").asString(""));
    }

    /** Top comments by relevance. Any failure (comments disabled, quota, network) just means no comments. */
    public List<Comment> topComments(String videoId) {
        List<Comment> out = new ArrayList<>();
        try {
            JsonNode body = get("/commentThreads",
                    "part=snippet&order=relevance&maxResults=5&textFormat=plainText&videoId=" + enc(videoId));
            for (JsonNode item : body.path("items")) {
                JsonNode c = item.path("snippet").path("topLevelComment").path("snippet");
                String text = c.path("textOriginal").asString(c.path("textDisplay").asString(""));
                if (text.isBlank()) continue;
                out.add(new Comment(c.path("authorDisplayName").asString(""),
                        c.path("authorChannelId").path("value").asString(""), text, c.path("likeCount").asInt(0)));
            }
        } catch (ApiException e) {
            log.info("Comments skipped for a video: {}", e.code());
        }
        return out;
    }

    private static String bestThumbnail(JsonNode thumbs) {
        for (String size : new String[]{"maxres", "standard", "high", "medium", "default"}) {
            String url = thumbs.path(size).path("url").asString("");
            if (!url.isEmpty()) return url;
        }
        return null;
    }

    private static String enc(String v) {
        return URLEncoder.encode(v, StandardCharsets.UTF_8);
    }

    private JsonNode get(String path, String query) {
        HttpResponse<String> res;
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(baseUrl + path + "?" + query))
                    .timeout(Duration.ofSeconds(5))
                    .header("X-Goog-Api-Key", apiKey)
                    .header("Accept", "application/json")
                    .GET().build();
            res = http.send(req, HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw unavailable();
        } catch (IOException | RuntimeException e) {
            // Class name only: the message could carry the request URL.
            log.warn("YouTube request failed: {}", e.getClass().getSimpleName());
            throw unavailable();
        }
        int status = res.statusCode();
        if (status / 100 == 2) {
            try {
                return json.readTree(res.body());
            } catch (RuntimeException e) {
                log.warn("YouTube returned an unreadable body");
                throw unavailable();
            }
        }
        String reason = errorReason(res.body());
        log.warn("YouTube API answered {} ({})", status, reason);
        if (status == 404) {
            throw new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "That video was not found or is private", null);
        }
        if (reason.equals("quotaExceeded") || reason.equals("dailyLimitExceeded") || reason.equals("rateLimitExceeded")
                || reason.equals("userRateLimitExceeded")) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "SOURCE_QUOTA_EXCEEDED",
                    "YouTube fetch limit reached for today. Paste the recipe text instead, or try again tomorrow.", null);
        }
        if (reason.equals("commentsDisabled") || reason.equals("videoNotFound") || reason.equals("forbidden")) {
            // Only meaningful for comments, which the caller ignores. For videos.list a 403 "forbidden" is a real failure.
            throw new ApiException(HttpStatus.FORBIDDEN, "SOURCE_UNAVAILABLE", "YouTube did not allow this request", null);
        }
        throw unavailable();
    }

    private String errorReason(String body) {
        try {
            JsonNode errors = json.readTree(body).path("error").path("errors");
            if (errors.isArray() && errors.size() > 0) return errors.get(0).path("reason").asString("");
            return json.readTree(body).path("error").path("status").asString("");
        } catch (RuntimeException e) {
            return "";
        }
    }

    private static ApiException unavailable() {
        return new ApiException(HttpStatus.BAD_GATEWAY, "SOURCE_UNAVAILABLE",
                "Could not reach YouTube right now. Try again, or paste the recipe text.", null);
    }
}
