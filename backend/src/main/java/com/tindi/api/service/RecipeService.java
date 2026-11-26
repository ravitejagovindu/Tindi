package com.tindi.api.service;

import com.tindi.api.domain.Category;
import com.tindi.api.domain.Recipe;
import com.tindi.api.domain.Repositories.CategoryRepository;
import com.tindi.api.domain.Repositories.RecipeRepository;
import com.tindi.api.dto.RecipeDtos.IngredientDto;
import com.tindi.api.dto.RecipeDtos.RecipeDto;
import com.tindi.api.dto.RecipeDtos.RecipeRequest;
import com.tindi.api.web.ApiException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

@Service
public class RecipeService {
    private final RecipeRepository recipes;
    private final CategoryRepository categories;
    private final JsonMapper json;

    public RecipeService(RecipeRepository recipes, CategoryRepository categories, JsonMapper json) {
        this.recipes = recipes;
        this.categories = categories;
        this.json = json;
    }

    @Transactional(readOnly = true)
    public List<RecipeDto> list(String userId) {
        return recipes.findByUserIdOrderByCreatedAtDescIdAsc(userId).stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public RecipeDto get(String userId, String id) {
        return toDto(find(userId, id));
    }

    @Transactional
    public RecipeDto create(String userId, RecipeRequest req) {
        Set<String> cats = checkCategories(userId, req.categoryIds());
        Recipe r = new Recipe(UUID.randomUUID().toString(), userId, Instant.now().truncatedTo(ChronoUnit.MICROS));
        apply(r, req, cats);
        return toDto(recipes.save(r));
    }

    @Transactional
    public RecipeDto replace(String userId, String id, RecipeRequest req) {
        Recipe r = find(userId, id);
        Set<String> cats = checkCategories(userId, req.categoryIds());
        apply(r, req, cats);
        r.setUpdatedAt(Instant.now().truncatedTo(ChronoUnit.MICROS));
        return toDto(recipes.saveAndFlush(r));
    }

    @Transactional
    public void delete(String userId, String id) {
        recipes.delete(find(userId, id));
    }

    private Recipe find(String userId, String id) {
        return recipes.findByIdAndUserId(id, userId).orElseThrow(ApiException::notFound);
    }

    private void apply(Recipe r, RecipeRequest req, Set<String> cats) {
        r.setName(req.name());
        r.setIngredients(json.writeValueAsString(req.ingredients()));
        r.setSteps(json.writeValueAsString(req.steps()));
        r.setSourceType(req.sourceType());
        r.setSourceUrl(req.sourceUrl());
        r.setThumbnailUrl(req.thumbnailUrl());
        r.getCategoryIds().clear();
        r.getCategoryIds().addAll(cats);
    }

    /** Only starter categories and the caller's own are allowed. */
    private Set<String> checkCategories(String userId, List<String> ids) {
        Set<String> wanted = new LinkedHashSet<>(ids);
        if (wanted.isEmpty()) return wanted;
        List<Category> found = categories.findAllById(wanted);
        long allowed = found.stream().filter(c -> c.getUserId() == null || userId.equals(c.getUserId())).count();
        if (allowed != wanted.size()) {
            throw ApiException.validation("categoryIds", "Choose only your own or starter categories");
        }
        return wanted;
    }

    private RecipeDto toDto(Recipe r) {
        List<IngredientDto> ing = json.readValue(r.getIngredients(), new TypeReference<List<IngredientDto>>() {});
        List<String> steps = json.readValue(r.getSteps(), new TypeReference<List<String>>() {});
        return new RecipeDto(r.getId(), r.getName(), ing, steps, r.getCategoryIds().stream().sorted().toList(),
                r.getSourceType(), r.getSourceUrl(), r.getThumbnailUrl(), r.getCreatedAt(), r.getUpdatedAt());
    }
}
