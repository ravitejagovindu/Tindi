package com.tindi.api;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.JsonNode;

/** Only the placeholder Gemini key: the app starts and the rules splitter answers. */
class GeminiNotConfiguredTest extends ApiTestBase {
    private static final StubGemini GEMINI = StubGemini.get();

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("tindi.gemini-api-key", () -> "PASTE_YOUR_GEMINI_API_KEY_HERE");
        r.add("tindi.gemini-api-base", GEMINI::baseUrl);
    }

    @Test
    void missingKeyUsesTheRulesSplitterAndNeverCallsGemini() {
        GEMINI.reset();
        String token = newUser("NoGemini")[0];
        Resp r = call("POST", "/source/structure", token,
                Map.of("text", "Ingredients:\n- 2 eggs\n- salt\nMethod:\n- Beat the eggs.\n- Fry them."));
        assertEquals(200, r.status());
        JsonNode s = r.body().get("suggested");
        assertEquals("RULES", s.get("method").asString());
        assertEquals("LOW", s.get("confidence").asString());
        assertEquals("PASTED", s.get("foundIn").asString());
        assertEquals(2, s.get("ingredients").size());
        assertEquals(2, s.get("steps").size());

        JsonNode none = call("POST", "/source/structure", token, Map.of("text", "Just chatting.")).body().get("suggested");
        assertEquals("NONE", none.get("confidence").asString());
        assertEquals("NONE", none.get("foundIn").asString());
        assertEquals(0, GEMINI.hits.size());
    }
}
