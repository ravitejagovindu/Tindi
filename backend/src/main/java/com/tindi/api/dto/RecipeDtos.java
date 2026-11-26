package com.tindi.api.dto;

import com.tindi.api.domain.SourceType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

public final class RecipeDtos {
    private RecipeDtos() {}

    public record IngredientDto(
            @Size(max = 40, message = "Use 40 characters or fewer") String quantity,
            @NotBlank(message = "Enter an ingredient") @Size(max = 120, message = "Use 120 characters or fewer")
            String item) {
        public IngredientDto {
            quantity = quantity == null ? "" : quantity.trim();
            item = item == null ? null : item.trim();
        }
    }

    public record RecipeRequest(
            @NotBlank(message = "Enter a name") @Size(max = 120, message = "Use 120 characters or fewer") String name,
            @NotNull(message = "Ingredients are required") @Size(max = 100, message = "Use 100 ingredients or fewer")
            List<@NotNull(message = "Enter an ingredient") @Valid IngredientDto> ingredients,
            @NotNull(message = "Steps are required") @Size(max = 100, message = "Use 100 steps or fewer")
            List<@NotBlank(message = "Enter a step") @Size(max = 1000, message = "Use 1000 characters or fewer")
            String> steps,
            List<String> categoryIds,
            @NotNull(message = "Choose a source type") SourceType sourceType,
            @Size(max = 2048, message = "Use 2048 characters or fewer") String sourceUrl,
            @Size(max = 2048, message = "Use 2048 characters or fewer")
            @Pattern(regexp = "(?i)https://.+", message = "Use an https link") String thumbnailUrl) {
        public RecipeRequest {
            name = name == null ? null : name.trim();
            steps = steps == null ? null : steps.stream().map(s -> s == null ? null : s.trim()).toList();
            categoryIds = categoryIds == null ? List.of() : categoryIds;
            sourceUrl = sourceUrl == null || sourceUrl.isBlank() ? null : sourceUrl.trim();
            thumbnailUrl = thumbnailUrl == null || thumbnailUrl.isBlank() ? null : thumbnailUrl.trim();
        }
    }

    public record RecipeDto(String id, String name, List<IngredientDto> ingredients, List<String> steps,
                            List<String> categoryIds, SourceType sourceType, String sourceUrl, String thumbnailUrl,
                            Instant createdAt, Instant updatedAt) {}
}
