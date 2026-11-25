package com.tindi.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RecipeTest extends ApiTestBase {

    private String code(Resp r) { return r.body().get("error").get("code").asString(); }

    @Test
    void createGetListNewestFirst() {
        String t = newUser("Ravi")[0];
        String dinner = starterId(t, "Dinner");
        Resp a = call("POST", "/recipes", t, recipeBody("First", List.of(dinner)));
        assertEquals(201, a.status());
        assertEquals("250 g", a.body().get("ingredients").get(0).get("quantity").asString());
        assertEquals("Paneer", a.body().get("ingredients").get(0).get("item").asString());
        assertEquals("Blend tomato and onion", a.body().get("steps").get(0).asString());
        assertEquals("PASTED", a.text("sourceType"));
        assertEquals(dinner, a.body().get("categoryIds").get(0).asString());
        call("POST", "/recipes", t, recipeBody("Second", List.of()));

        Resp list = call("GET", "/recipes", t, null);
        assertEquals(200, list.status());
        assertEquals(2, list.body().size());
        assertEquals("Second", list.body().get(0).get("name").asString());
        assertEquals("First", list.body().get(1).get("name").asString());
        assertEquals(2, call("GET", "/me", t, null).body().get("recipeCount").asInt());
        assertEquals("First", call("GET", "/recipes/" + a.text("id"), t, null).text("name"));
    }

    @Test
    void putReplacesEverything() {
        String t = newUser("Ravi")[0];
        String dinner = starterId(t, "Dinner");
        String lunch = starterId(t, "Lunch");
        Resp a = call("POST", "/recipes", t, recipeBody("Old", List.of(dinner)));
        Map<String, Object> body = new HashMap<>();
        body.put("name", "  New name ");
        body.put("ingredients", List.of(Map.of("quantity", "", "item", "Salt"), Map.of("item", "Oil")));
        body.put("steps", List.of("One", "Two"));
        body.put("categoryIds", List.of(lunch));
        body.put("sourceType", "YOUTUBE");
        body.put("sourceUrl", "https://www.youtube.com/watch?v=abc");
        Resp u = call("PUT", "/recipes/" + a.text("id"), t, body);
        assertEquals(200, u.status());
        assertEquals(a.text("id"), u.text("id"));
        assertEquals("New name", u.text("name"));
        assertEquals(2, u.body().get("ingredients").size());
        assertEquals("", u.body().get("ingredients").get(1).get("quantity").asString());
        assertEquals(2, u.body().get("steps").size());
        assertEquals(1, u.body().get("categoryIds").size());
        assertEquals(lunch, u.body().get("categoryIds").get(0).asString());
        assertEquals("YOUTUBE", u.text("sourceType"));
        assertEquals(a.text("createdAt"), u.text("createdAt"));
        assertEquals(1, call("GET", "/recipes", t, null).body().size());
    }

    @Test
    void deleteRemovesRecipeAndLinks() {
        String t = newUser("Ravi")[0];
        String id = call("POST", "/recipes", t, recipeBody("Gone", List.of(starterId(t, "Lunch")))).text("id");
        assertEquals(204, call("DELETE", "/recipes/" + id, t, null).status());
        assertEquals(404, call("GET", "/recipes/" + id, t, null).status());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM recipe_categories", Integer.class));
    }

    @Test
    void categoryIdsMustBeStarterOrOwn() {
        String t = newUser("Ravi")[0];
        String other = newUser("Other")[0];
        String othersCat = call("POST", "/categories", other, Map.of("name", "Secret")).text("id");

        Resp foreign = call("POST", "/recipes", t, recipeBody("X", List.of(othersCat)));
        assertEquals(400, foreign.status());
        assertEquals("VALIDATION_FAILED", code(foreign));
        assertTrue(foreign.body().get("error").get("fields").has("categoryIds"));

        Resp unknown = call("POST", "/recipes", t, recipeBody("X", List.of("does-not-exist")));
        assertEquals(400, unknown.status());

        String mine = call("POST", "/categories", t, Map.of("name", "Mine")).text("id");
        Resp ok = call("POST", "/recipes", t, recipeBody("X", List.of(mine, starterId(t, "Protein"), mine)));
        assertEquals(201, ok.status());
        assertEquals(2, ok.body().get("categoryIds").size());

        // same rule on update
        assertEquals(400, call("PUT", "/recipes/" + ok.text("id"), t, recipeBody("X", List.of(othersCat))).status());
        assertEquals(0, call("GET", "/recipes", other, null).body().size());
    }

    @Test
    void validationRules() {
        String t = newUser("Ravi")[0];
        Map<String, Object> bad = new HashMap<>(recipeBody("ok", List.of()));

        bad.put("name", " ");
        Resp r = call("POST", "/recipes", t, bad);
        assertEquals(400, r.status());
        assertTrue(r.body().get("error").get("fields").has("name"));

        bad = new HashMap<>(recipeBody("x".repeat(121), List.of()));
        assertEquals(400, call("POST", "/recipes", t, bad).status());

        bad = new HashMap<>(recipeBody("ok", List.of()));
        bad.put("ingredients", List.of(Map.of("quantity", "q".repeat(41), "item", "Salt"), Map.of("item", " ")));
        r = call("POST", "/recipes", t, bad);
        assertEquals(400, r.status());
        var fields = r.body().get("error").get("fields");
        assertTrue(fields.has("ingredients[0].quantity"), fields.toString());
        assertTrue(fields.has("ingredients[1].item"), fields.toString());

        bad = new HashMap<>(recipeBody("ok", List.of()));
        bad.put("steps", List.of("fine", " ", "s".repeat(1001)));
        r = call("POST", "/recipes", t, bad);
        assertEquals(400, r.status());
        assertTrue(r.body().get("error").get("fields").has("steps[1]"));
        assertTrue(r.body().get("error").get("fields").has("steps[2]"));

        List<Map<String, String>> many = new ArrayList<>();
        for (int i = 0; i < 101; i++) many.add(Map.of("quantity", "1", "item", "i" + i));
        bad = new HashMap<>(recipeBody("ok", List.of()));
        bad.put("ingredients", many);
        assertEquals(400, call("POST", "/recipes", t, bad).status());

        bad = new HashMap<>(recipeBody("ok", List.of()));
        bad.put("sourceType", "TIKTOK");
        r = call("POST", "/recipes", t, bad);
        assertEquals(400, r.status());
        assertEquals("VALIDATION_FAILED", code(r));

        bad = new HashMap<>(recipeBody("ok", List.of()));
        bad.remove("sourceType");
        assertEquals(400, call("POST", "/recipes", t, bad).status());

        // exactly at the limits is fine
        List<Map<String, String>> hundred = new ArrayList<>();
        for (int i = 0; i < 100; i++) hundred.add(Map.of("quantity", "q".repeat(40), "item", "i".repeat(120)));
        Map<String, Object> max = new HashMap<>(recipeBody("n".repeat(120), List.of()));
        max.put("ingredients", hundred);
        max.put("steps", List.of("s".repeat(1000)));
        assertEquals(201, call("POST", "/recipes", t, max).status());
    }
}
