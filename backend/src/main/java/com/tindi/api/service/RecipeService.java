package com.tindi.api.service;

import com.tindi.api.domain.Category;
import com.tindi.api.domain.Illustration;
import com.tindi.api.domain.Recipe;
import com.tindi.api.domain.Repositories.CategoryRepository;
import com.tindi.api.domain.Repositories.RecipeRepository;
import com.tindi.api.dto.RecipeDtos.IngredientDto;
import com.tindi.api.dto.RecipeDtos.RecipeDto;
import com.tindi.api.dto.RecipeDtos.RecipeRequest;
import com.tindi.api.dto.RecipeDtos.StepDetailDto;
import com.tindi.api.web.ApiException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
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
        checkStepDetails(req);
        Recipe r = new Recipe(UUID.randomUUID().toString(), userId, Instant.now().truncatedTo(ChronoUnit.MICROS));
        apply(r, req, cats);
        return toDto(recipes.save(r));
    }

    @Transactional
    public RecipeDto replace(String userId, String id, RecipeRequest req) {
        Recipe r = find(userId, id);
        Set<String> cats = checkCategories(userId, req.categoryIds());
        checkStepDetails(req);
        // Compared before apply() overwrites the old lists. Details that would no longer line up are removed.
        if (req.stepDetails() == null && !stillAligned(r, req)) r.setStepDetails(null);
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
        // Not sent means "leave as is". replace() has already cleared details that are out of date.
        if (req.stepDetails() != null) r.setStepDetails(json.writeValueAsString(req.stepDetails()));
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

    /** One entry for each step, each with a known illustration and indexes inside the ingredient list. */
    private static void checkStepDetails(RecipeRequest req) {
        List<StepDetailDto> details = req.stepDetails();
        if (details == null) return;
        Map<String, String> errors = new LinkedHashMap<>();
        if (details.size() != req.steps().size()) errors.put("stepDetails", "Send one entry for each step");
        for (int i = 0; i < details.size(); i++) {
            StepDetailDto d = details.get(i);
            String field = "stepDetails[" + i + "].";
            if (d.illustration() == null || !Illustration.NAMES.contains(d.illustration())) {
                errors.put(field + "illustration", "Use one of " + String.join(", ", Illustration.NAMES));
            }
            for (int j = 0; j < d.ingredientIndexes().size(); j++) {
                int index = d.ingredientIndexes().get(j);
                if (index < 0 || index >= req.ingredients().size()) {
                    errors.put(field + "ingredientIndexes[" + j + "]", "There is no ingredient at this position");
                }
            }
        }
        if (!errors.isEmpty()) throw ApiException.validation(errors);
    }

    /**
     * Stored step details still fit if the steps are the same and each position holds the same ingredient.
     * A changed quantity does not move anything.
     */
    private boolean stillAligned(Recipe stored, RecipeRequest req) {
        return readSteps(stored).equals(req.steps()) && items(readIngredients(stored)).equals(items(req.ingredients()));
    }

    private static List<String> items(List<IngredientDto> ingredients) {
        return ingredients.stream().map(IngredientDto::item).toList();
    }

    private List<IngredientDto> readIngredients(Recipe r) {
        return json.readValue(r.getIngredients(), new TypeReference<List<IngredientDto>>() {});
    }

    private List<String> readSteps(Recipe r) {
        return json.readValue(r.getSteps(), new TypeReference<List<String>>() {});
    }

    private RecipeDto toDto(Recipe r) {
        List<StepDetailDto> details = r.getStepDetails() == null ? null
                : json.readValue(r.getStepDetails(), new TypeReference<List<StepDetailDto>>() {});
        return new RecipeDto(r.getId(), r.getName(), readIngredients(r), readSteps(r), details,
                r.getCategoryIds().stream().sorted().toList(), r.getSourceType(), r.getSourceUrl(),
                r.getThumbnailUrl(), r.getCreatedAt(), r.getUpdatedAt());
    }
}
