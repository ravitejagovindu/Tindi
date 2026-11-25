package com.tindi.api;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(properties = "tindi.seed-sample-recipes=true")
class SeedTest extends ApiTestBase {

    @Test
    void newAccountGetsFourSampleRecipes() {
        String t = newUser("Ravi")[0];
        var list = call("GET", "/recipes", t, null).body();
        assertEquals(4, list.size());
        int linked = 0;
        for (var r : list) linked += r.get("categoryIds").size();
        assertEquals(1 + 2 + 3 + 2, linked);
        assertEquals(4, call("GET", "/me", t, null).body().get("recipeCount").asInt());
        // a second account is seeded independently
        assertEquals(4, call("GET", "/recipes", newUser("Other")[0], null).body().size());
    }
}
