package com.tindi.api;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Integration tests: real server on a random port, real MySQL schema tindi_test. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public abstract class ApiTestBase {
    public record Resp(int status, JsonNode body) {
        public String text(String field) { return body.get(field).asString(); }
    }

    @Autowired protected Environment env;
    @Autowired protected JdbcTemplate jdbc;
    protected final JsonMapper json = JsonMapper.builder().build();
    private final HttpClient http = HttpClient.newHttpClient();

    @BeforeEach
    void cleanDatabase() {
        String url = env.getProperty("spring.datasource.url");
        assertTrue(url != null && url.contains("/tindi_test"), "Refusing to run tests against " + url);
        jdbc.update("DELETE FROM users"); // cascades to tokens, categories, recipes, links
    }

    protected Resp call(String method, String path, String token, Object body) {
        try {
            String port = env.getProperty("local.server.port");
            HttpRequest.Builder b = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1" + path))
                    .header("Content-Type", "application/json");
            if (token != null) b.header("Authorization", "Bearer " + token);
            b.method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                    : HttpRequest.BodyPublishers.ofString(body instanceof String s ? s : json.writeValueAsString(body)));
            HttpResponse<String> r = http.send(b.build(), HttpResponse.BodyHandlers.ofString());
            return new Resp(r.statusCode(), r.body().isBlank() ? null : json.readTree(r.body()));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /** Signs up a fresh user and returns {token, id}. */
    protected String[] newUser(String name) {
        String email = name.toLowerCase() + "-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        Resp r = call("POST", "/auth/signup", null, Map.of("name", name, "email", email, "password", "secret123"));
        assertTrue(r.status() == 201, "signup failed: " + r.body());
        return new String[]{r.text("token"), r.body().get("user").get("id").asString(), email};
    }

    protected Map<String, Object> recipeBody(String name, java.util.List<String> categoryIds) {
        return Map.of("name", name,
                "ingredients", java.util.List.of(Map.of("quantity", "250 g", "item", "Paneer")),
                "steps", java.util.List.of("Blend tomato and onion"),
                "categoryIds", categoryIds,
                "sourceType", "PASTED");
    }

    protected String starterId(String token, String name) {
        for (JsonNode c : call("GET", "/categories", token, null).body()) {
            if (c.get("name").asString().equals(name)) return c.get("id").asString();
        }
        throw new IllegalStateException(name);
    }
}
