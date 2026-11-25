package com.tindi.api;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/** Stands in for googleapis.com/youtube/v3 in tests. Never talks to the real YouTube. The video id picks the scenario. */
public final class StubYouTube {
    public static final String OK = "dQw4w9WgXcQ";
    public static final String NOT_FOUND = "NOTFOUND001";
    public static final String QUOTA = "QUOTA000001";
    public static final String COMMENTS_OFF = "NOCOMMENTS1";
    public static final String COMMENTS_BROKEN = "COMMENTS5XX";
    public static final String UPSTREAM_DOWN = "UPSTREAM001";
    public static final String RECIPE_IN_COMMENT = "RECIPECMT01";

    public record Hit(String path, String query, String apiKeyHeader) {}

    private static StubYouTube instance;

    private final HttpServer server;
    public final List<Hit> hits = new CopyOnWriteArrayList<>();

    public static synchronized StubYouTube get() {
        if (instance == null) instance = new StubYouTube();
        return instance;
    }

    private StubYouTube() {
        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        server.createContext("/youtube/v3/videos", this::videos);
        server.createContext("/youtube/v3/commentThreads", this::comments);
        server.start();
    }

    public String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/youtube/v3";
    }

    public void reset() {
        hits.clear();
    }

    public long countFor(String path) {
        return hits.stream().filter(h -> h.path().endsWith(path)).count();
    }

    private static String param(String query, String name) {
        for (String p : query.split("&")) {
            if (p.startsWith(name + "=")) return p.substring(name.length() + 1);
        }
        return "";
    }

    private void send(HttpExchange ex, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Content-Type", "application/json; charset=UTF-8");
        ex.sendResponseHeaders(status, bytes.length);
        ex.getResponseBody().write(bytes);
        ex.close();
    }

    private static String error(int code, String reason) {
        return "{\"error\":{\"code\":" + code + ",\"message\":\"x\",\"errors\":[{\"reason\":\"" + reason + "\"}]}}";
    }

    private void videos(HttpExchange ex) throws IOException {
        String q = ex.getRequestURI().getRawQuery();
        hits.add(new Hit(ex.getRequestURI().getPath(), q, ex.getRequestHeaders().getFirst("X-Goog-Api-Key")));
        String id = param(q, "id");
        switch (id) {
            case NOT_FOUND -> send(ex, 200, "{\"items\":[]}");
            case QUOTA -> send(ex, 403, error(403, "quotaExceeded"));
            case UPSTREAM_DOWN -> send(ex, 500, error(500, "backendError"));
            case RECIPE_IN_COMMENT -> send(ex, 200, video(id, "Jeera rice", "Quick jeera rice. Full recipe in the comments.", true));
            default -> send(ex, 200, video(id, "Paneer Butter Masala", """
                    Creamy paneer butter masala.

                    Ingredients:
                    - 250 g paneer
                    - 2 tbsp butter
                    - 1 cup tomato puree

                    Method:
                    1. Melt the butter.
                    2. Add the puree and simmer.
                    3. Add the paneer.
                    """, false));
        }
    }

    private static String video(String id, String title, String description, boolean onlyDefaultThumb) {
        String thumbs = onlyDefaultThumb
                ? "\"default\":{\"url\":\"https://i.ytimg.com/vi/" + id + "/default.jpg\"}"
                : "\"default\":{\"url\":\"https://i.ytimg.com/vi/" + id + "/default.jpg\"},"
                + "\"high\":{\"url\":\"https://i.ytimg.com/vi/" + id + "/hqdefault.jpg\"},"
                + "\"maxres\":{\"url\":\"https://i.ytimg.com/vi/" + id + "/maxresdefault.jpg\"}";
        return "{\"items\":[{\"id\":\"" + id + "\",\"snippet\":{\"title\":" + quote(title)
                + ",\"channelId\":\"UCchannel1\",\"channelTitle\":\"Tindi Test Kitchen\",\"description\":" + quote(description)
                + ",\"thumbnails\":{" + thumbs + "}}}]}";
    }

    private static String quote(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\"";
    }

    private void comments(HttpExchange ex) throws IOException {
        String q = ex.getRequestURI().getRawQuery();
        hits.add(new Hit(ex.getRequestURI().getPath(), q, ex.getRequestHeaders().getFirst("X-Goog-Api-Key")));
        String id = param(q, "videoId");
        switch (id) {
            case COMMENTS_OFF -> send(ex, 403, error(403, "commentsDisabled"));
            case COMMENTS_BROKEN -> send(ex, 500, error(500, "backendError"));
            case RECIPE_IN_COMMENT -> send(ex, 200, threads(Map.of(
                    "UCchannel1", "Ingredients:\\n2 cups rice\\n1 tsp cumin\\n1 tbsp ghee\\n\\nMethod:\\n1. Wash the rice.\\n2. Temper the cumin.\\n3. Cook the rice.",
                    "UCfan", "Looks great!")));
            default -> send(ex, 200, threads(Map.of(
                    "UCchannel1", "Pinned: use kasuri methi for the best flavour",
                    "UCfan", "Made this last night, loved it!")));
        }
    }

    /** Builds a commentThreads response. Map order is not relied on: creator comment always first. */
    private static String threads(Map<String, String> byAuthorChannel) {
        StringBuilder sb = new StringBuilder("{\"items\":[");
        boolean first = true;
        for (String channel : new String[]{"UCchannel1", "UCfan"}) {
            String text = byAuthorChannel.get(channel);
            if (text == null) continue;
            if (!first) sb.append(',');
            first = false;
            sb.append("{\"snippet\":{\"topLevelComment\":{\"snippet\":{\"authorDisplayName\":\"")
                    .append(channel.equals("UCchannel1") ? "Tindi Test Kitchen" : "A Fan")
                    .append("\",\"authorChannelId\":{\"value\":\"").append(channel)
                    .append("\"},\"textOriginal\":\"").append(text).append("\",\"likeCount\":")
                    .append(channel.equals("UCchannel1") ? 120 : 7).append("}}}}");
        }
        return sb.append("]}").toString();
    }
}
