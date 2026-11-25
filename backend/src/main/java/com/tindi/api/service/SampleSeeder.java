package com.tindi.api.service;

import com.tindi.api.domain.Category;
import com.tindi.api.domain.Recipe;
import com.tindi.api.domain.Repositories.CategoryRepository;
import com.tindi.api.domain.Repositories.RecipeRepository;
import com.tindi.api.domain.SourceType;
import com.tindi.api.dto.RecipeDtos.IngredientDto;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/** Gives a new account the same four sample recipes the Android app ships with. */
@Component
public class SampleSeeder {
    record Sample(String name, List<IngredientDto> ingredients, List<String> steps, List<String> categories,
                  String sourceUrl) {}

    private final RecipeRepository recipes;
    private final CategoryRepository categories;
    private final JsonMapper json;
    private final List<Sample> samples;

    public SampleSeeder(RecipeRepository recipes, CategoryRepository categories, JsonMapper json) throws IOException {
        this.recipes = recipes;
        this.categories = categories;
        this.json = json;
        try (InputStream in = new ClassPathResource("sample-recipes.json").getInputStream()) {
            this.samples = json.readValue(in, new TypeReference<List<Sample>>() {});
        }
    }

    public void seed(String userId) {
        Map<String, String> starterIds = new HashMap<>();
        for (Category c : categories.findByUserIdIsNullOrderBySortOrder()) starterIds.put(c.getName(), c.getId());
        // Newest first in the app, so give each sample a slightly different time.
        Instant base = Instant.now().truncatedTo(ChronoUnit.MICROS);
        for (int i = 0; i < samples.size(); i++) {
            Sample s = samples.get(i);
            Recipe r = new Recipe(UUID.randomUUID().toString(), userId, base.plusMillis(i));
            r.setName(s.name());
            r.setIngredients(json.writeValueAsString(s.ingredients()));
            r.setSteps(json.writeValueAsString(s.steps()));
            r.setSourceType(SourceType.YOUTUBE);
            r.setSourceUrl(s.sourceUrl());
            s.categories().forEach(n -> {
                String id = starterIds.get(n);
                if (id != null) r.getCategoryIds().add(id);
            });
            recipes.save(r);
        }
    }
}
