package com.tindi.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CategoryTest extends ApiTestBase {

    private String code(Resp r) { return r.body().get("error").get("code").asString(); }

    @Test
    void listsStartersFirstThenOwnByName() {
        String t = newUser("Ravi")[0];
        call("POST", "/categories", t, Map.of("name", "zucchini"));
        call("POST", "/categories", t, Map.of("name", "Apples"));
        var list = call("GET", "/categories", t, null).body();
        assertEquals(8, list.size());
        String[] expected = {"Breakfast", "Lunch", "Snacks", "Dinner", "Healthy", "Protein", "Apples", "zucchini"};
        for (int i = 0; i < expected.length; i++) {
            assertEquals(expected[i], list.get(i).get("name").asString());
            assertEquals(i < 6, list.get(i).get("starter").asBoolean());
        }
        assertEquals(2, call("GET", "/me", t, null).body().get("categoryCount").asInt());
    }

    @Test
    void createRenameDelete() {
        String t = newUser("Ravi")[0];
        Resp c = call("POST", "/categories", t, Map.of("name", "  Sweets "));
        assertEquals(201, c.status());
        assertEquals("Sweets", c.text("name"));
        assertFalse(c.body().get("starter").asBoolean());
        String id = c.text("id");
        assertEquals("Desserts", call("PATCH", "/categories/" + id, t, Map.of("name", "Desserts")).text("name"));
        // renaming to a different case of itself is fine
        assertEquals(200, call("PATCH", "/categories/" + id, t, Map.of("name", "DESSERTS")).status());
        assertEquals(204, call("DELETE", "/categories/" + id, t, null).status());
        assertEquals(404, call("DELETE", "/categories/" + id, t, null).status());
    }

    @Test
    void nameRules() {
        String t = newUser("Ravi")[0];
        assertEquals(400, call("POST", "/categories", t, Map.of("name", " ")).status());
        assertEquals(400, call("POST", "/categories", t, Map.of("name", "x".repeat(31))).status());
        assertEquals(201, call("POST", "/categories", t, Map.of("name", "x".repeat(30))).status());

        call("POST", "/categories", t, Map.of("name", "Sweets"));
        Resp dup = call("POST", "/categories", t, Map.of("name", "sweets"));
        assertEquals(409, dup.status());
        assertEquals("CATEGORY_NAME_TAKEN", code(dup));
        assertTrue(dup.body().get("error").get("fields").has("name"));

        Resp starter = call("POST", "/categories", t, Map.of("name", "dinner"));
        assertEquals(409, starter.status());
        assertEquals("CATEGORY_NAME_TAKEN", code(starter));

        // another user may reuse the name
        String other = newUser("Other")[0];
        assertEquals(201, call("POST", "/categories", other, Map.of("name", "Sweets")).status());
    }

    @Test
    void starterCategoriesAreReadOnly() {
        String t = newUser("Ravi")[0];
        String dinner = starterId(t, "Dinner");
        Resp rename = call("PATCH", "/categories/" + dinner, t, Map.of("name", "Supper"));
        assertEquals(403, rename.status());
        assertEquals("STARTER_READ_ONLY", code(rename));
        Resp del = call("DELETE", "/categories/" + dinner, t, null);
        assertEquals(403, del.status());
        assertEquals("STARTER_READ_ONLY", code(del));
    }

    @Test
    void deletingACategoryKeepsItsRecipes() {
        String t = newUser("Ravi")[0];
        String mine = call("POST", "/categories", t, Map.of("name", "Sweets")).text("id");
        String dinner = starterId(t, "Dinner");
        String rid = call("POST", "/recipes", t, recipeBody("Halwa", List.of(mine, dinner))).text("id");

        assertEquals(204, call("DELETE", "/categories/" + mine, t, null).status());

        Resp r = call("GET", "/recipes/" + rid, t, null);
        assertEquals(200, r.status());
        assertEquals(1, r.body().get("categoryIds").size());
        assertEquals(dinner, r.body().get("categoryIds").get(0).asString());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM recipe_categories", Integer.class));

        // deleting the last link leaves an "uncategorised" recipe
        String other = call("POST", "/categories", t, Map.of("name", "Quick")).text("id");
        call("PUT", "/recipes/" + rid, t, recipeBody("Halwa", List.of(other)));
        call("DELETE", "/categories/" + other, t, null);
        assertEquals(0, call("GET", "/recipes/" + rid, t, null).body().get("categoryIds").size());
    }
}
