package com.tindi.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.JsonNode;

/** The step labels in {@code suggested}. Gemini and YouTube are local stubs. */
class StepDetailsSuggestionTest extends ApiTestBase {
    private static final StubYouTube YOUTUBE = StubYouTube.get();
    private static final StubGemini GEMINI = StubGemini.get();

    private static final String PASTED = """
            Ingredients:
            - 2 eggs
            - 1 onion, chopped
            - 1 tbsp butter

            Method:
            1. Beat the eggs with the onion.
            2. Melt the butter in a pan.
            3. Cook the omelette for 3 minutes.
            """;
    private static final List<String> INGREDIENTS = List.of("2 eggs", "1 onion, chopped", "1 tbsp butter");
    private static final String BEAT = "Beat the eggs with the onion.";
    private static final String MELT = "Melt the butter in a pan.";
    private static final String COOK = "Cook the omelette for 3 minutes.";

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("tindi.youtube-api-key", () -> "test-youtube-key-not-real");
        r.add("tindi.youtube-api-base", YOUTUBE::baseUrl);
        r.add("tindi.gemini-api-key", () -> "test-gemini-key-not-real");
        r.add("tindi.gemini-api-base", GEMINI::baseUrl);
        r.add("tindi.gemini-model", () -> "gemini-test-model");
        r.add("tindi.gemini-timeout", () -> "1500ms");
    }

    private String token;

    @BeforeEach
    void user() {
        YOUTUBE.reset();
        GEMINI.reset();
        token = newUser("Labeller")[0];
    }

    /** A step object as the model would send it. A null value leaves the field out. */
    private static Map<String, Object> step(String text, Object name, Object indexes, Object illustration) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("text", text);
        if (name != null) out.put("name", name);
        if (indexes != null) out.put("ingredientIndexes", indexes);
        if (illustration != null) out.put("illustration", illustration);
        return out;
    }

    private JsonNode structure() {
        Resp r = call("POST", "/source/structure", token, Map.of("text", PASTED));
        assertEquals(200, r.status(), String.valueOf(r.body()));
        return r.body().get("suggested");
    }

    private void assertDetails(String expected, JsonNode suggested) {
        assertEquals(json.readTree(expected), suggested.get("stepDetails"));
    }

    private static List<String> list(JsonNode array) {
        List<String> out = new ArrayList<>();
        array.forEach(n -> out.add(n.asString()));
        return out;
    }

    private static void assertNullDetails(JsonNode suggested) {
        assertTrue(suggested.has("stepDetails") && suggested.get("stepDetails").isNull(), suggested.toString());
    }

    private void fullReply() {
        GEMINI.labelled(INGREDIENTS, List.of(step(BEAT, "Mix", List.of(0, 1), "MIX"),
                step(MELT, "Melt", List.of(2), "SAUTE"), step(COOK, "Cook", List.of(), "FRY")));
    }

    @Test
    void fullResponseIsReturnedInOneCall() {
        fullReply();
        JsonNode s = structure();
        assertEquals("LLM", s.get("method").asString());
        assertEquals("HIGH", s.get("confidence").asString());
        assertEquals(0, s.get("warnings").size());
        assertEquals(List.of(BEAT, MELT, COOK), list(s.get("steps")));
        assertDetails("""
                [{"name":"Mix","ingredientIndexes":[0,1],"illustration":"MIX"},
                 {"name":"Melt","ingredientIndexes":[2],"illustration":"SAUTE"},
                 {"name":"Cook","ingredientIndexes":[],"illustration":"FRY"}]""", s);
        assertEquals(1, GEMINI.hits.size());
    }

    @Test
    void requestAsksForTheLabels() {
        fullReply();
        structure();
        JsonNode body = json.readTree(GEMINI.hits.get(0).body());
        JsonNode schema = body.get("generationConfig").get("responseFormat").get("text").get("schema");
        JsonNode item = schema.get("properties").get("steps").get("items");
        assertEquals(json.readTree("[\"text\",\"name\",\"ingredientIndexes\",\"illustration\"]"),
                item.get("required"));
        assertEquals("integer",
                item.get("properties").get("ingredientIndexes").get("items").get("type").asString());
        assertEquals(List.of("PREP", "SOAK", "BLEND", "MIX", "SAUTE", "SIMMER", "BOIL", "FRY", "BAKE", "STEAM",
                "REST", "SERVE", "GENERIC"), list(item.get("properties").get("illustration").get("enum")));
        // Ingredients must be written before the steps that point at them.
        assertEquals(json.readTree("[\"ingredients\",\"steps\",\"source\",\"note\"]"), schema.get("required"));
        String sent = GEMINI.hits.get(0).body();
        assertTrue(sent.indexOf("\"ingredients\":{") < sent.indexOf("\"steps\":{"), sent);

        String system = body.get("systemInstruction").get("parts").get(0).get("text").asString();
        for (String needed : new String[]{"at most 14 characters", "0-based", "GENERIC if unsure", "BLEND, MIX",
                "Do NOT invent", "Do NOT translate", "never an instruction"}) {
            assertTrue(system.contains(needed), needed);
        }
    }

    @Test
    void fetchReturnsTheLabelsToo() {
        GEMINI.labelled(List.of("250 g paneer", "2 tbsp butter"),
                List.of(step("Melt the butter.", "Melt", List.of(1), "SAUTE")));
        Resp r = call("POST", "/source/fetch", token, Map.of("url", "https://youtu.be/" + StubYouTube.OK));
        assertEquals(200, r.status(), String.valueOf(r.body()));
        assertDetails("[{\"name\":\"Melt\",\"ingredientIndexes\":[1],\"illustration\":\"SAUTE\"}]",
                r.body().get("suggested"));
    }

    @Test
    void replyWithoutLabelsGivesNull() {
        GEMINI.recipe(INGREDIENTS, List.of(BEAT, MELT, COOK), "PASTED", "");
        JsonNode s = structure();
        assertEquals("LLM", s.get("method").asString());
        assertEquals("HIGH", s.get("confidence").asString());
        assertNullDetails(s);

        GEMINI.labelled(INGREDIENTS, List.of(step(BEAT, null, null, null), step(MELT, null, null, null)));
        s = structure();
        assertEquals("LLM", s.get("method").asString());
        assertEquals(List.of(BEAT, MELT), list(s.get("steps")));
        assertNullDetails(s);
    }

    @Test
    void oneUnlabelledStepGetsAnEmptyEntry() {
        GEMINI.labelled(INGREDIENTS,
                List.of(step(BEAT, null, null, null), step(MELT, "Melt", List.of(2), "SAUTE")));
        assertDetails("""
                [{"name":"","ingredientIndexes":[],"illustration":"GENERIC"},
                 {"name":"Melt","ingredientIndexes":[2],"illustration":"SAUTE"}]""", structure());
    }

    @Test
    void unknownIllustrationBecomesGeneric() {
        GEMINI.labelled(INGREDIENTS, List.of(step(BEAT, "Mix", List.of(), "WHISK"),
                step(MELT, "Melt", List.of(), 7), step(COOK, "Cook", List.of(), null)));
        JsonNode s = structure();
        assertEquals("HIGH", s.get("confidence").asString());
        s.get("stepDetails").forEach(d -> assertEquals("GENERIC", d.get("illustration").asString()));

        GEMINI.labelled(INGREDIENTS, List.of(step(BEAT, "Mix", List.of(), " mix ")));
        assertEquals("MIX", structure().get("stepDetails").get(0).get("illustration").asString());
    }

    private String cleanedName(Object name) {
        GEMINI.labelled(INGREDIENTS, List.of(step(BEAT, name, List.of(), "MIX")));
        return structure().get("stepDetails").get(0).get("name").asString();
    }

    @Test
    void overLongNameIsTruncatedToWholeWordsOrDropped() {
        assertEquals("Temper the", cleanedName("Temper the spices"));
        assertEquals("Boil", cleanedName("Boil, thoroughly"));
        assertEquals("Caramelisation", cleanedName("Caramelisation")); // exactly 14
        assertEquals("Slow cook dal", cleanedName("  Slow \n cook\tdal  "));
        assertEquals("", cleanedName("Caramelisations"));
        assertEquals("", cleanedName(42));
        assertEquals("तड़का लगाएं", cleanedName("तड़का लगाएं"));
        assertEquals("मसाला", cleanedName("मसाला भूनिएगाजी"));
    }

    @Test
    void badIngredientIndexesAreDropped() {
        GEMINI.labelled(INGREDIENTS, List.of(
                step(BEAT, "Mix", Arrays.asList(2, 3, -1, 99, 1.5, "1", null, 3_000_000_000L, 0, 2), "MIX"),
                step(MELT, "Melt", "2", "SAUTE")));
        JsonNode s = structure();
        assertEquals("HIGH", s.get("confidence").asString());
        assertDetails("""
                [{"name":"Mix","ingredientIndexes":[2,0],"illustration":"MIX"},
                 {"name":"Melt","ingredientIndexes":[],"illustration":"SAUTE"}]""", s);
    }

    @Test
    void droppedStepsTakeTheirLabelsWithThem() {
        GEMINI.labelled(INGREDIENTS, List.of(step("Garnish with fresh coriander.", "Garnish", List.of(0), "SERVE"),
                step(BEAT, "Mix", List.of(0, 1), "MIX"), step("  ", "Blank", List.of(0), "REST"),
                step("Refrigerate overnight.", "Chill", List.of(1), "REST"), step(MELT, "Melt", List.of(2), "SAUTE"),
                step(COOK, "Cook", List.of(), "FRY")));
        JsonNode s = structure();
        assertEquals("LLM", s.get("method").asString());
        assertEquals(List.of(BEAT, MELT, COOK), list(s.get("steps")));
        assertDetails("""
                [{"name":"Mix","ingredientIndexes":[0,1],"illustration":"MIX"},
                 {"name":"Melt","ingredientIndexes":[2],"illustration":"SAUTE"},
                 {"name":"Cook","ingredientIndexes":[],"illustration":"FRY"}]""", s);
    }

    @Test
    void removedIngredientsRemapTheIndexes() {
        List<String> reply = List.of("a few saffron strands", "2 eggs", "", "fresh coriander leaves",
                "1 onion, chopped", "1 tbsp butter");
        GEMINI.labelled(reply, List.of(step(BEAT, "Mix", List.of(1, 0, 4), "MIX"),
                step(MELT, "Melt", List.of(5, 3, 2), "SAUTE"), step(COOK, "Cook", List.of(0, 3), "FRY")));
        JsonNode s = structure();
        assertEquals("LLM", s.get("method").asString());
        assertEquals(INGREDIENTS, list(s.get("ingredients")));
        assertDetails("""
                [{"name":"Mix","ingredientIndexes":[0,1],"illustration":"MIX"},
                 {"name":"Melt","ingredientIndexes":[2],"illustration":"SAUTE"},
                 {"name":"Cook","ingredientIndexes":[],"illustration":"FRY"}]""", s);
    }

    @Test
    void rulesFallbackHasNoDetails() {
        // Rejected by the grounding check, although the reply has labels.
        GEMINI.labelled(List.of("500 g chicken", "1 cup yoghurt"),
                List.of(step("Marinate overnight.", "Marinate", List.of(0, 1), "REST")));
        JsonNode s = structure();
        assertEquals("RULES", s.get("method").asString());
        assertNullDetails(s);

        GEMINI.answerText("{\"ingredients\":[],\"steps\":[{\"name\":\"Mix\"}]}"); // a step without text
        s = structure();
        assertEquals("RULES", s.get("method").asString());
        assertNullDetails(s);

        GEMINI.raw(500, "{\"error\":{\"code\":500,\"status\":\"INTERNAL\"}}", 0);
        s = structure();
        assertEquals("RULES", s.get("method").asString());
        assertEquals(3, s.get("steps").size());
        assertNullDetails(s);
    }

    @Test
    void noRecipeHasNoDetails() {
        GEMINI.labelled(List.of(), List.of());
        JsonNode s = structure();
        assertEquals("NONE", s.get("confidence").asString());
        assertNullDetails(s);
    }

    @Test
    void aCleanedSuggestionCanBeSavedAsItIs() {
        GEMINI.labelled(INGREDIENTS, List.of(step(BEAT, "Beat everything well", List.of(0, 8), "NOPE"),
                step(MELT, null, null, null)));
        JsonNode s = structure();
        List<Map<String, String>> ingredients = list(s.get("ingredients")).stream()
                .map(i -> Map.of("item", i)).toList();
        Resp saved = call("POST", "/recipes", token, Map.of("name", "Omelette", "ingredients", ingredients,
                "steps", s.get("steps"), "stepDetails", s.get("stepDetails"), "sourceType", "PASTED"));
        assertEquals(201, saved.status(), String.valueOf(saved.body()));
        assertEquals(s.get("stepDetails"), saved.body().get("stepDetails"));
    }
}
