package com.tindi.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.JsonNode;

/** Key configured, YouTube replaced by a local stub. */
class SourceFetchTest extends ApiTestBase {
    static final String FAKE_KEY = "test-key-not-real";
    private static final StubYouTube STUB = StubYouTube.get();

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("tindi.youtube-api-key", () -> FAKE_KEY);
        r.add("tindi.youtube-api-base", STUB::baseUrl);
    }

    private String token;

    @BeforeEach
    void user() {
        STUB.reset();
        token = newUser("Fetcher")[0];
    }

    private Resp fetch(String url) {
        return call("POST", "/source/fetch", token, Map.of("url", url));
    }

    private static String code(Resp r) {
        return r.body().get("error").get("code").asString();
    }

    @Test
    void fullResponseForAWatchUrl() {
        Resp r = fetch("https://www.youtube.com/watch?v=" + StubYouTube.OK);
        assertEquals(200, r.status());
        JsonNode b = r.body();
        assertEquals("Paneer Butter Masala", b.get("title").asString());
        assertEquals("Tindi Test Kitchen", b.get("channelName").asString());
        assertTrue(b.get("thumbnailUrl").asString().endsWith("maxresdefault.jpg"));
        assertEquals("https://www.youtube.com/watch?v=" + StubYouTube.OK, b.get("sourceUrl").asString());
        assertEquals("YOUTUBE", b.get("sourceType").asString());
        assertTrue(b.get("description").asString().contains("Ingredients:"));

        JsonNode comments = b.get("comments");
        assertEquals(2, comments.size());
        assertEquals("Tindi Test Kitchen", comments.get(0).get("author").asString());
        assertEquals(120, comments.get(0).get("likeCount").asInt());
        assertTrue(comments.get(0).get("isPinnedLikely").asBoolean());
        assertFalse(comments.get(1).get("isPinnedLikely").asBoolean());

        JsonNode s = b.get("suggested");
        assertEquals(3, s.get("ingredients").size());
        assertEquals("250 g paneer", s.get("ingredients").get(0).asString());
        assertEquals(3, s.get("steps").size());
        assertEquals("LOW", s.get("confidence").asString()); // HIGH is reserved for a clean LLM result
        assertEquals("DESCRIPTION", s.get("foundIn").asString());
        assertEquals("RULES", s.get("method").asString());
        assertEquals(0, s.get("warnings").size());
    }

    @Test
    void everyAcceptedUrlShapeWorks() {
        for (String url : new String[]{
                "https://youtu.be/" + StubYouTube.OK + "?si=x",
                "https://www.youtube.com/shorts/" + StubYouTube.OK,
                "https://m.youtube.com/watch?v=" + StubYouTube.OK,
                "https://youtube.com/watch?v=" + StubYouTube.OK + "&t=9s"}) {
            Resp r = fetch(url);
            assertEquals(200, r.status(), url);
            assertEquals("https://www.youtube.com/watch?v=" + StubYouTube.OK, r.text("sourceUrl"));
        }
    }

    @Test
    void bestAvailableThumbnailIsUsed() {
        Resp r = fetch("https://youtu.be/" + StubYouTube.RECIPE_IN_COMMENT);
        assertTrue(r.text("thumbnailUrl").endsWith("default.jpg"));
    }

    @Test
    void instagramAndOtherLinksAreRejectedWithAPasteHint() {
        Resp r = fetch("https://www.instagram.com/reel/Cabc123/");
        assertEquals(400, r.status());
        assertEquals("VALIDATION_FAILED", code(r));
        String hint = r.body().get("error").get("fields").get("url").asString();
        assertTrue(hint.contains("Instagram") && hint.contains("text"), hint);
        assertEquals(0, STUB.hits.size(), "YouTube must not be called for a bad link");
    }

    @Test
    void blankAndMissingUrlAreValidationErrors() {
        assertEquals(400, fetch("   ").status());
        assertEquals("VALIDATION_FAILED", code(call("POST", "/source/fetch", token, Map.of())));
    }

    @Test
    void videoNotFoundOrPrivate() {
        Resp r = fetch("https://youtu.be/" + StubYouTube.NOT_FOUND);
        assertEquals(404, r.status());
        assertEquals("NOT_FOUND", code(r));
    }

    @Test
    void quotaExceededIsReportedAndNotRetried() {
        Resp r = fetch("https://youtu.be/" + StubYouTube.QUOTA);
        assertEquals(429, r.status());
        assertEquals("SOURCE_QUOTA_EXCEEDED", code(r));
        assertEquals(1, STUB.countFor("/videos"), "no retries");
        assertEquals(0, STUB.countFor("/commentThreads"));
    }

    @Test
    void upstreamFailureIsSourceUnavailable() {
        Resp r = fetch("https://youtu.be/" + StubYouTube.UPSTREAM_DOWN);
        assertEquals(502, r.status());
        assertEquals("SOURCE_UNAVAILABLE", code(r));
        assertEquals(1, STUB.countFor("/videos"), "no retries");
    }

    @Test
    void commentsDisabledStillReturnsTheVideo() {
        Resp r = fetch("https://youtu.be/" + StubYouTube.COMMENTS_OFF);
        assertEquals(200, r.status());
        assertEquals(0, r.body().get("comments").size());
        assertEquals("Paneer Butter Masala", r.text("title"));
        assertEquals("RULES", r.body().get("suggested").get("method").asString());
        assertEquals(3, r.body().get("suggested").get("steps").size());
    }

    @Test
    void commentsFailingUpstreamStillReturnsTheVideo() {
        Resp r = fetch("https://youtu.be/" + StubYouTube.COMMENTS_BROKEN);
        assertEquals(200, r.status());
        assertEquals(0, r.body().get("comments").size());
    }

    @Test
    void recipeFoundInACommentIsSuggestedFromThere() {
        Resp r = fetch("https://youtu.be/" + StubYouTube.RECIPE_IN_COMMENT);
        assertEquals(200, r.status());
        JsonNode s = r.body().get("suggested");
        assertEquals("COMMENT", s.get("foundIn").asString());
        assertEquals(3, s.get("ingredients").size());
        assertEquals(3, s.get("steps").size());
        assertEquals("LOW", s.get("confidence").asString());
        assertEquals("RULES", s.get("method").asString());
        assertEquals(2, r.body().get("comments").size()); // raw comments still returned
    }

    @Test
    void apiKeyTravelsInAHeaderNeverInTheUrl() {
        fetch("https://youtu.be/" + StubYouTube.OK);
        assertFalse(STUB.hits.isEmpty());
        for (StubYouTube.Hit h : STUB.hits) {
            assertEquals(FAKE_KEY, h.apiKeyHeader());
            assertFalse(h.query().contains(FAKE_KEY));
            assertFalse(h.query().contains("key="));
        }
        var comment = STUB.hits.stream().filter(h -> h.path().endsWith("/commentThreads")).findFirst().orElseThrow();
        assertTrue(comment.query().contains("order=relevance"));
        assertTrue(comment.query().contains("maxResults=5"));
    }

    @Test
    void authIsRequired() {
        Resp none = call("POST", "/source/fetch", null, Map.of("url", "https://youtu.be/" + StubYouTube.OK));
        assertEquals(401, none.status());
        assertEquals("UNAUTHORIZED", code(none));
        Resp bad = call("POST", "/source/fetch", "not-a-token", Map.of("url", "https://youtu.be/" + StubYouTube.OK));
        assertEquals(401, bad.status());
        assertEquals(0, STUB.hits.size());
        assertNull(none.body().get("error").get("fields"));
    }
}
