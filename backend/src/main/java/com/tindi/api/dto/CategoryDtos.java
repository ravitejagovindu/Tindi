package com.tindi.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class CategoryDtos {
    private CategoryDtos() {}

    public record CategoryRequest(
            @NotBlank(message = "Enter a name") @Size(max = 30, message = "Use 30 characters or fewer") String name) {
        public CategoryRequest {
            name = name == null ? null : name.trim();
        }
    }

    public record CategoryDto(String id, String name, boolean starter) {}
}
