package com.tindi.api.service;

import com.tindi.api.domain.Category;
import com.tindi.api.domain.Repositories.CategoryRepository;
import com.tindi.api.dto.CategoryDtos.CategoryDto;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import com.tindi.api.web.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CategoryService {
    private final CategoryRepository categories;

    public CategoryService(CategoryRepository categories) {
        this.categories = categories;
    }

    @Transactional(readOnly = true)
    public List<CategoryDto> list(String userId) {
        List<Category> all = categories.findVisible(userId);
        return all.stream()
                .sorted(Comparator.comparing((Category c) -> !c.isStarter())
                        .thenComparingInt(Category::getSortOrder)
                        .thenComparing(Category::getName, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(Category::getId))
                .map(CategoryService::toDto)
                .toList();
    }

    @Transactional
    public CategoryDto create(String userId, String name) {
        ensureNameFree(userId, name, null);
        Category c = new Category(UUID.randomUUID().toString(), userId, name,
                Instant.now().truncatedTo(ChronoUnit.MICROS));
        return toDto(categories.save(c));
    }

    @Transactional
    public CategoryDto rename(String userId, String id, String name) {
        Category c = ownOrFail(userId, id);
        ensureNameFree(userId, name, c.getId());
        c.setName(name);
        return toDto(c);
    }

    @Transactional
    public void delete(String userId, String id) {
        Category c = ownOrFail(userId, id);
        // The recipe_categories rows go away through ON DELETE CASCADE; recipes stay.
        categories.delete(c);
        categories.flush();
    }

    /** Starter -> 403, someone else's or missing -> 404. */
    private Category ownOrFail(String userId, String id) {
        Category c = categories.findById(id).orElseThrow(ApiException::notFound);
        if (c.isStarter()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "STARTER_READ_ONLY",
                    "Starter categories can't be changed", null);
        }
        if (!userId.equals(c.getUserId())) throw ApiException.notFound();
        return c;
    }

    private void ensureNameFree(String userId, String name, String exceptId) {
        boolean taken = categories.findVisible(userId).stream()
                .filter(c -> !c.getId().equals(exceptId))
                .anyMatch(c -> c.getName().equalsIgnoreCase(name));
        if (taken) {
            throw new ApiException(HttpStatus.CONFLICT, "CATEGORY_NAME_TAKEN", "That category name is taken",
                    Map.of("name", "You already have a category with this name"));
        }
    }

    private static CategoryDto toDto(Category c) {
        return new CategoryDto(c.getId(), c.getName(), c.isStarter());
    }
}
