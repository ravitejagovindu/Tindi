package com.tindi.api.service;

import com.tindi.api.web.ApiException;
import java.net.URI;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;

/** Pulls the video id out of the YouTube link shapes Tindi accepts. Anything else is a validation error. */
public final class YouTubeUrl {
    public static final String MESSAGE =
            "Only YouTube links can be fetched. Paste Instagram and other links as text instead.";

    private static final Pattern ID = Pattern.compile("[A-Za-z0-9_-]{11}");
    private static final Set<String> YOUTUBE_HOSTS = Set.of("youtube.com", "www.youtube.com", "m.youtube.com");
    private static final Set<String> SHORT_HOSTS = Set.of("youtu.be", "www.youtu.be");

    private YouTubeUrl() {}

    public static String canonical(String videoId) {
        return "https://www.youtube.com/watch?v=" + videoId;
    }

    /** Returns the 11 character video id, or throws VALIDATION_FAILED. */
    public static String videoId(String raw) {
        String id = parse(raw == null ? "" : raw.trim());
        if (id == null) throw invalid();
        return id;
    }

    private static ApiException invalid() {
        return new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", MESSAGE, java.util.Map.of("url", MESSAGE));
    }

    private static String parse(String raw) {
        if (raw.isEmpty() || raw.chars().anyMatch(Character::isWhitespace)) return null;
        String withScheme = raw.matches("(?i)^https?://.*") ? raw : "https://" + raw;
        URI uri;
        try {
            uri = URI.create(withScheme);
        } catch (IllegalArgumentException e) {
            return null;
        }
        String host = uri.getHost() == null ? null : uri.getHost().toLowerCase(Locale.ROOT);
        if (host == null) return null;
        String path = uri.getPath() == null ? "" : uri.getPath();
        String[] seg = java.util.Arrays.stream(path.split("/")).filter(s -> !s.isEmpty()).toArray(String[]::new);

        String candidate = null;
        if (SHORT_HOSTS.contains(host)) {
            if (seg.length >= 1) candidate = seg[0];
        } else if (YOUTUBE_HOSTS.contains(host)) {
            if (seg.length == 1 && seg[0].equals("watch")) candidate = queryParam(uri.getRawQuery(), "v");
            else if (seg.length >= 2 && seg[0].equals("shorts")) candidate = seg[1];
        }
        return candidate != null && ID.matcher(candidate).matches() ? candidate : null;
    }

    private static String queryParam(String query, String name) {
        if (query == null) return null;
        for (String pair : query.split("&")) {
            int eq = pair.indexOf('=');
            if (eq > 0 && pair.substring(0, eq).equals(name)) return pair.substring(eq + 1);
        }
        return null;
    }
}
