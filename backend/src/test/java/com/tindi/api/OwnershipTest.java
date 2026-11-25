package com.tindi.api;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** User A must never be able to see, change or confirm the existence of user B's data. */
class OwnershipTest extends ApiTestBase {

    @Test
    void otherUsersRecipesAreInvisible() {
        String a = newUser("Alice")[0];
        String b = newUser("Bob")[0];
        String id = call("POST", "/recipes", b, recipeBody("Bob's secret", List.of())).text("id");

        assertEquals(404, call("GET", "/recipes/" + id, a, null).status());
        assertEquals(404, call("PUT", "/recipes/" + id, a, recipeBody("Hacked", List.of())).status());
        assertEquals(404, call("DELETE", "/recipes/" + id, a, null).status());
        assertEquals(0, call("GET", "/recipes", a, null).body().size());

        // still intact for the owner
        Resp mine = call("GET", "/recipes/" + id, b, null);
        assertEquals(200, mine.status());
        assertEquals("Bob's secret", mine.text("name"));
    }

    @Test
    void otherUsersCategoriesAreInvisible() {
        String a = newUser("Alice")[0];
        String b = newUser("Bob")[0];
        String id = call("POST", "/categories", b, Map.of("name", "Bobs")).text("id");

        assertEquals(404, call("PATCH", "/categories/" + id, a, Map.of("name", "Hacked")).status());
        assertEquals(404, call("DELETE", "/categories/" + id, a, null).status());
        assertEquals(6, call("GET", "/categories", a, null).body().size());
        assertEquals(7, call("GET", "/categories", b, null).body().size());
        assertEquals(404, call("PATCH", "/categories/nope", a, Map.of("name", "Zed")).status());
    }
}
