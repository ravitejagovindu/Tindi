package com.tindi.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;

class AuthTest extends ApiTestBase {

    @Test
    void healthIsOpen() {
        Resp r = call("GET", "/health", null, null);
        assertEquals(200, r.status());
        assertEquals("UP", r.text("status"));
    }

    @Test
    void signupThenLoginThenMe() {
        Resp s = call("POST", "/auth/signup", null,
                Map.of("name", "  Ravi  ", "email", "Ravi@Example.COM", "password", "secret123"));
        assertEquals(201, s.status());
        assertEquals("Ravi", s.body().get("user").get("name").asString());
        assertEquals("ravi@example.com", s.body().get("user").get("email").asString());
        assertFalse(s.text("token").isBlank());

        Resp l = call("POST", "/auth/login", null, Map.of("email", "RAVI@example.com", "password", "secret123"));
        assertEquals(200, l.status());
        Resp me = call("GET", "/me", l.text("token"), null);
        assertEquals(200, me.status());
        assertEquals(0, me.body().get("recipeCount").asInt());
        assertEquals(0, me.body().get("categoryCount").asInt());
    }

    @Test
    void passwordAndTokenAreNotStoredInPlain() {
        Resp s = call("POST", "/auth/signup", null,
                Map.of("name", "Ravi", "email", "a@example.com", "password", "secret123"));
        String hash = jdbc.queryForObject("SELECT password_hash FROM users", String.class);
        assertTrue(hash.startsWith("$2"));
        String tokenHash = jdbc.queryForObject("SELECT token_hash FROM auth_tokens", String.class);
        assertEquals(64, tokenHash.length());
        assertFalse(tokenHash.contains(s.text("token")));
    }

    @Test
    void wrongPasswordAndUnknownEmailLookTheSame() {
        newUser("Ravi");
        String email = jdbc.queryForObject("SELECT email FROM users", String.class);
        Resp wrongPw = call("POST", "/auth/login", null, Map.of("email", email, "password", "nope-nope"));
        Resp noUser = call("POST", "/auth/login", null, Map.of("email", "ghost@example.com", "password", "nope-nope"));
        assertEquals(401, wrongPw.status());
        assertEquals("INVALID_CREDENTIALS", wrongPw.body().get("error").get("code").asString());
        assertEquals(wrongPw.body(), noUser.body());
    }

    @Test
    void duplicateEmailIsCaseInsensitive() {
        call("POST", "/auth/signup", null, Map.of("name", "A", "email", "dup@example.com", "password", "secret123"));
        Resp r = call("POST", "/auth/signup", null,
                Map.of("name", "B", "email", "DUP@example.com", "password", "secret123"));
        assertEquals(409, r.status());
        assertEquals("EMAIL_TAKEN", r.body().get("error").get("code").asString());
        assertTrue(r.body().get("error").get("fields").has("email"));
    }

    @Test
    void validationErrorShape() {
        Resp r = call("POST", "/auth/signup", null, Map.of("name", " ", "email", "nope", "password", "123"));
        assertEquals(400, r.status());
        var err = r.body().get("error");
        assertEquals("VALIDATION_FAILED", err.get("code").asString());
        assertFalse(err.get("message").asString().isBlank());
        assertTrue(err.get("fields").has("name"));
        assertTrue(err.get("fields").has("email"));
        assertTrue(err.get("fields").has("password"));
    }

    @Test
    void malformedBodyIsValidationFailed() {
        Resp r = call("POST", "/auth/login", null, "{not json");
        assertEquals(400, r.status());
        assertEquals("VALIDATION_FAILED", r.body().get("error").get("code").asString());
    }

    @Test
    void protectedEndpointsNeedAToken() {
        for (String path : new String[]{"/me", "/categories", "/recipes"}) {
            Resp r = call("GET", path, null, null);
            assertEquals(401, r.status(), path);
            assertEquals("UNAUTHORIZED", r.body().get("error").get("code").asString());
        }
        assertEquals(401, call("GET", "/me", "garbage", null).status());
    }

    @Test
    void logoutRevokesImmediatelyButOtherDevicesKeepWorking() {
        newUser("Ravi");
        String email = jdbc.queryForObject("SELECT email FROM users", String.class);
        String t1 = call("POST", "/auth/login", null, Map.of("email", email, "password", "secret123")).text("token");
        String t2 = call("POST", "/auth/login", null, Map.of("email", email, "password", "secret123")).text("token");
        assertEquals(200, call("GET", "/me", t1, null).status());
        assertEquals(204, call("POST", "/auth/logout", t1, null).status());
        assertEquals(401, call("GET", "/me", t1, null).status());
        assertEquals(200, call("GET", "/me", t2, null).status());
    }

    @Test
    void tokensNeverExpire() {
        String token = newUser("Ravi")[0];
        jdbc.update("UPDATE auth_tokens SET created_at = '2015-01-01 00:00:00', last_used_at = '2015-01-01 00:00:00'");
        assertEquals(200, call("GET", "/me", token, null).status());
        String lastUsed = jdbc.queryForObject("SELECT YEAR(last_used_at) FROM auth_tokens", String.class);
        assertTrue(Integer.parseInt(lastUsed) >= 2025, "last_used_at should be refreshed");
    }
}
