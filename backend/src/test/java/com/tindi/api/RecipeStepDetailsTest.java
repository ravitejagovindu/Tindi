package com.tindi.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

class RecipeStepDetailsTest extends ApiTestBase {
    private static final String DETAILS = """
            [{"name":"Blend","ingredientIndexes":[0,1],"illustration":"BLEND"},
             {"name":"Simmer","ingredientIndexes":[],"illustration":"SIMMER"}]""";

    private String token;
    private String userId;

    @BeforeEach
    void user() {
        String[] u = newUser("Cook");
        token = u[0];
        userId = u[1];
    }

    private static Map<String, Object> body() {
        Map<String, Object> body = new HashMap<>();
        body.put("name", "Gravy");
        body.put("ingredients", List.of(Map.of("quantity", "2", "item", "Tomato"),
                Map.of("quantity", "1", "item", "Onion")));
        body.put("steps", List.of("Blend tomato and onion", "Simmer for 10 minutes"));
        body.put("sourceType", "PASTED");
        return body;
    }

    private Map<String, Object> bodyWithDetails() {
        Map<String, Object> body = body();
        body.put("stepDetails", json.readTree(DETAILS));
        return body;
    }

    private static Map<String, Object> detail(Object name, Object indexes, Object illustration) {
        Map<String, Object> out = new HashMap<>();
        out.put("name", name);
        out.put("ingredientIndexes", indexes);
        out.put("illustration", illustration);
        return out;
    }

    private String create(Map<String, Object> body) {
        Resp r = call("POST", "/recipes", token, body);
        assertEquals(201, r.status(), String.valueOf(r.body()));
        return r.text("id");
    }

    private JsonNode put(String id, Map<String, Object> body) {
        Resp r = call("PUT", "/recipes/" + id, token, body);
        assertEquals(200, r.status(), String.valueOf(r.body()));
        // The reply and a fresh read agree.
        assertEquals(r.body().get("stepDetails"), stored(id));
        return r.body().get("stepDetails");
    }

    private JsonNode stored(String id) {
        return call("GET", "/recipes/" + id, token, null).body().get("stepDetails");
    }

    @Test
    void createWithAndWithoutDetails() {
        Resp without = call("POST", "/recipes", token, body());
        assertEquals(201, without.status());
        assertTrue(without.body().has("stepDetails") && without.body().get("stepDetails").isNull());
        assertTrue(stored(without.text("id")).isNull());

        Resp with = call("POST", "/recipes", token, bodyWithDetails());
        assertEquals(201, with.status(), String.valueOf(with.body()));
        assertEquals(json.readTree(DETAILS), with.body().get("stepDetails"));
        assertEquals(json.readTree(DETAILS), stored(with.text("id")));

        JsonNode list = call("GET", "/recipes", token, null).body();
        assertEquals(json.readTree(DETAILS), list.get(0).get("stepDetails"));
        assertTrue(list.get(1).get("stepDetails").isNull());
    }

    @Test
    void missingNameAndIndexesBecomeEmptyAndNamesAreTrimmed() {
        Map<String, Object> body = body();
        body.put("stepDetails", List.of(Map.of("illustration", "GENERIC"),
                detail("  Caramelisation ", List.of(1), "SAUTE")));
        assertEquals(json.readTree("""
                [{"name":"","ingredientIndexes":[],"illustration":"GENERIC"},
                 {"name":"Caramelisation","ingredientIndexes":[1],"illustration":"SAUTE"}]"""),
                stored(create(body)));
    }

    @Test
    void aRecipeWithoutStepsTakesAnEmptyList() {
        Map<String, Object> body = body();
        body.put("steps", List.of());
        body.put("stepDetails", List.of());
        assertEquals(json.readTree("[]"), stored(create(body)));
    }

    @Test
    void putReplacesDetailsWhenSent() {
        String id = create(body());
        assertEquals(json.readTree(DETAILS), put(id, bodyWithDetails()));

        Map<String, Object> changed = bodyWithDetails();
        changed.put("steps", List.of("Serve hot"));
        changed.put("stepDetails", List.of(detail("Serve", List.of(0), "SERVE")));
        assertEquals("SERVE", put(id, changed).get(0).get("illustration").asString());
    }

    @Test
    void putWithoutDetailsKeepsThemWhileTheyStillFit() {
        String id = create(bodyWithDetails());

        Map<String, Object> body = body();
        body.put("name", "Renamed");
        body.put("steps", List.of(" Blend tomato and onion ", "Simmer for 10 minutes")); // only spacing
        body.put("ingredients", List.of(Map.of("quantity", "3 large", "item", "Tomato"), Map.of("item", "Onion")));
        assertEquals(json.readTree(DETAILS), put(id, body));

        body.put("stepDetails", null); // the same as leaving it out
        assertEquals(json.readTree(DETAILS), put(id, body));
    }

    @Test
    void putChangingStepsWithoutDetailsClearsThem() {
        for (List<String> steps : List.of(
                List.of("Blend tomato and onion", "Simmer for 15 minutes"),
                List.of("Simmer for 10 minutes", "Blend tomato and onion"),
                List.of("Blend tomato and onion"),
                List.of("Blend tomato and onion", "Simmer for 10 minutes", "Serve"))) {
            String id = create(bodyWithDetails());
            Map<String, Object> body = body();
            body.put("steps", steps);
            assertTrue(put(id, body).isNull(), steps.toString());
        }
    }

    @Test
    void putMovingIngredientsWithoutDetailsClearsThem() {
        Map<String, String> tomato = Map.of("item", "Tomato");
        Map<String, String> onion = Map.of("item", "Onion");
        for (List<Map<String, String>> ingredients : List.of(
                List.of(onion, tomato),
                List.of(tomato),
                List.of(tomato, onion, Map.of("item", "Salt")),
                List.of(tomato, Map.of("item", "Garlic")))) {
            String id = create(bodyWithDetails());
            Map<String, Object> body = body();
            body.put("ingredients", ingredients);
            assertTrue(put(id, body).isNull(), ingredients.toString());
        }
    }

    private void assertRejected(Resp r, String... fields) {
        assertEquals(400, r.status(), String.valueOf(r.body()));
        assertEquals("VALIDATION_FAILED", r.body().get("error").get("code").asString());
        for (String field : fields) {
            assertTrue(r.body().get("error").get("fields").has(field), field + " in " + r.body());
        }
    }

    /** Both ways of saving must reject it. */
    private void assertInvalid(Object details, String... fields) {
        Map<String, Object> body = body();
        body.put("stepDetails", details);
        String id = create(bodyWithDetails());
        assertRejected(call("POST", "/recipes", token, body), fields);
        body.put("name", "Must not be saved");
        assertRejected(call("PUT", "/recipes/" + id, token, body), fields);
        Resp unchanged = call("GET", "/recipes/" + id, token, null);
        assertEquals("Gravy", unchanged.text("name"));
        assertEquals(json.readTree(DETAILS), unchanged.body().get("stepDetails"));
        call("DELETE", "/recipes/" + id, token, null);
        assertEquals(0, call("GET", "/recipes", token, null).body().size());
    }

    @Test
    void lengthMustMatchSteps() {
        Map<String, Object> one = detail("Blend", List.of(), "BLEND");
        assertInvalid(List.of(), "stepDetails");
        assertInvalid(List.of(one), "stepDetails");
        assertInvalid(List.of(one, one, one), "stepDetails");
    }

    @Test
    void indexesMustBeInsideTheIngredients() {
        assertInvalid(List.of(detail("Blend", List.of(0, 2), "BLEND"), detail("Simmer", List.of(-1), "SIMMER")),
                "stepDetails[0].ingredientIndexes[1]", "stepDetails[1].ingredientIndexes[0]");
        assertInvalid(List.of(detail("Blend", Arrays.asList(0, null), "BLEND"),
                detail("Simmer", List.of(), "SIMMER")), "stepDetails[0].ingredientIndexes[1]");
        assertInvalid(List.of(detail("Blend", List.of("first"), "BLEND"), detail("Simmer", List.of(), "SIMMER")),
                "stepDetails[0].ingredientIndexes[0]");
    }

    @Test
    void illustrationMustBeInTheSet() {
        assertInvalid(List.of(detail("Blend", List.of(), "WHISK"), detail("Simmer", List.of(), "simmer")),
                "stepDetails[0].illustration", "stepDetails[1].illustration");
        assertInvalid(List.of(detail("Blend", List.of(), null), detail("Simmer", List.of(), "")),
                "stepDetails[0].illustration", "stepDetails[1].illustration");
    }

    @Test
    void namesAndEntriesAreChecked() {
        Map<String, Object> ok = detail("Blend", List.of(), "BLEND");
        assertInvalid(List.of(ok, detail("Caramelisations", List.of(), "SIMMER")), "stepDetails[1].name");
        assertInvalid(Arrays.asList(ok, null), "stepDetails[1]");
    }

    @Test
    void otherUsersStillGetNotFound() {
        String id = create(bodyWithDetails());
        String intruder = newUser("Intruder")[0];

        Map<String, Object> invalid = body();
        invalid.put("stepDetails", List.of(detail("Blend", List.of(50), "WHISK")));
        assertEquals(404, call("GET", "/recipes/" + id, intruder, null).status());
        assertEquals(404, call("PUT", "/recipes/" + id, intruder, body()).status());
        assertEquals(404, call("PUT", "/recipes/" + id, intruder, bodyWithDetails()).status());
        assertEquals(404, call("PUT", "/recipes/" + id, intruder, invalid).status());
        assertEquals(404, call("DELETE", "/recipes/" + id, intruder, null).status());
        assertEquals(json.readTree(DETAILS), stored(id));
    }

    @Test
    void recipesSavedBeforeTheColumnExistedStillLoad() {
        String id = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO recipes (id, user_id, name, ingredients, steps, source_type, created_at, updated_at) "
                + "VALUES (?, ?, 'Old', ?, ?, 'PASTED', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))", id, userId,
                "[{\"quantity\":\"\",\"item\":\"Salt\"}]", "[\"Add salt\"]");

        Resp one = call("GET", "/recipes/" + id, token, null);
        assertEquals(200, one.status());
        assertTrue(one.body().get("stepDetails").isNull());
        assertEquals("Add salt", one.body().get("steps").get(0).asString());
        assertEquals(200, call("GET", "/recipes", token, null).status());

        assertTrue(put(id, body()).isNull());
        assertEquals(json.readTree(DETAILS), put(id, bodyWithDetails()));
    }
}
