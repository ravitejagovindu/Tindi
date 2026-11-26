package com.tindi.api;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.tindi.api.service.GeminiClient;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Each model family gets the lowest thinking setting it accepts. Talks to the local stub only. */
class GeminiThinkingTest {
    private static final StubGemini GEMINI = StubGemini.get();
    private final JsonMapper json = JsonMapper.builder().build();

    private JsonNode thinkingSentFor(String model) {
        GEMINI.reset();
        new GeminiClient("test-gemini-key-not-real", model, GEMINI.baseUrl(), Duration.ofSeconds(5), json)
                .generate("system", "user", Map.of("type", "object"));
        return json.readTree(GEMINI.hits.get(0).body()).get("generationConfig").get("thinkingConfig");
    }

    @Test
    void gemini3FlashModelsThinkMinimally() {
        for (String model : new String[]{"", "gemini-3.5-flash-lite", "gemini-3.1-flash-lite", "gemini-3-flash-preview",
                "gemini-3.5-flash"}) {
            assertEquals(json.readTree("{\"thinkingLevel\":\"MINIMAL\"}"), thinkingSentFor(model), model);
        }
    }

    @Test
    void gemini25FlashModelsDoNotThink() {
        for (String model : new String[]{"gemini-2.5-flash", "gemini-2.5-flash-lite"}) {
            assertEquals(json.readTree("{\"thinkingBudget\":0}"), thinkingSentFor(model), model);
        }
    }

    @Test
    void otherModelsKeepTheirDefault() {
        for (String model : new String[]{"gemini-3-pro-preview", "gemini-2.5-pro", "gemini-test-model"}) {
            assertEquals(null, thinkingSentFor(model), model);
        }
    }
}
