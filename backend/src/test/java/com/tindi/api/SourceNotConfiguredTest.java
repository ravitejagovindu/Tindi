package com.tindi.api;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** No key (or only the placeholder): the app still starts and the endpoint explains itself. */
class SourceNotConfiguredTest extends ApiTestBase {
    private static final StubYouTube STUB = StubYouTube.get();

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("tindi.youtube-api-key", () -> "PASTE_YOUR_YOUTUBE_API_KEY_HERE");
        r.add("tindi.youtube-api-base", STUB::baseUrl);
    }

    @Test
    void missingKeyGivesSourceNotConfigured() {
        STUB.reset();
        String token = newUser("NoKey")[0];
        Resp r = call("POST", "/source/fetch", token, Map.of("url", "https://youtu.be/" + StubYouTube.OK));
        assertEquals(503, r.status());
        assertEquals("SOURCE_NOT_CONFIGURED", r.body().get("error").get("code").asString());
        assertEquals(0, STUB.hits.size());
    }

    @Test
    void badLinkIsStillAValidationErrorAndRestOfAppWorks() {
        String token = newUser("NoKey2")[0];
        Resp bad = call("POST", "/source/fetch", token, Map.of("url", "https://example.com/x"));
        assertEquals("VALIDATION_FAILED", bad.body().get("error").get("code").asString());
        assertEquals(200, call("GET", "/me", token, null).status());
    }
}
