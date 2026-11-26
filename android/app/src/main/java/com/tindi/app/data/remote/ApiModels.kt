package com.tindi.app.data.remote

import com.tindi.app.data.Category
import com.tindi.app.data.Ingredient
import com.tindi.app.data.Recipe
import com.tindi.app.data.RecipeInput
import com.tindi.app.data.SourceType
import kotlinx.serialization.Serializable

@Serializable data class SignUpRequest(val name: String, val email: String, val password: String)
@Serializable data class LoginRequest(val email: String, val password: String)
@Serializable data class UserDto(val id: String, val name: String, val email: String)
@Serializable data class AuthResponse(val token: String, val user: UserDto)
@Serializable
data class MeDto(
    val id: String,
    val name: String,
    val email: String,
    val recipeCount: Int = 0,
    val categoryCount: Int = 0,
)

@Serializable data class NameRequest(val name: String)
@Serializable data class CategoryDto(val id: String, val name: String, val starter: Boolean = false)

@Serializable data class IngredientDto(val quantity: String = "", val item: String)

@Serializable
data class RecipeDto(
    val id: String,
    val name: String,
    val ingredients: List<IngredientDto> = emptyList(),
    val steps: List<String> = emptyList(),
    val categoryIds: List<String> = emptyList(),
    val sourceType: String = "PASTED",
    val sourceUrl: String? = null,
    val thumbnailUrl: String? = null,
)

@Serializable
data class RecipeRequest(
    val name: String,
    val ingredients: List<IngredientDto>,
    val steps: List<String>,
    val categoryIds: List<String>,
    val sourceType: String,
    val sourceUrl: String?,
    val thumbnailUrl: String? = null,
)

fun CategoryDto.toDomain() = Category(id, name, builtIn = starter)

fun RecipeDto.toDomain() = Recipe(
    id = id,
    name = name,
    ingredients = ingredients.map { Ingredient(it.quantity, it.item) },
    steps = steps,
    categoryIds = categoryIds,
    sourceType = if (sourceType.equals("YOUTUBE", ignoreCase = true)) SourceType.YouTube else SourceType.Pasted,
    sourceUrl = sourceUrl,
    thumbnailUrl = thumbnailUrl,
)

fun RecipeInput.toRequest() = RecipeRequest(
    name = name.trim(),
    ingredients = ingredients.map { IngredientDto(it.quantity.trim(), it.item.trim()) },
    steps = steps,
    categoryIds = categoryIds,
    sourceType = if (sourceType == SourceType.YouTube) "YOUTUBE" else "PASTED",
    sourceUrl = sourceUrl?.takeIf { it.isNotBlank() },
    thumbnailUrl = thumbnailUrl?.takeIf { it.isNotBlank() },
)

@Serializable data class FetchRequest(val url: String)

@Serializable
data class SourceCommentDto(
    val author: String = "",
    val text: String = "",
    val likeCount: Int = 0,
    val isPinnedLikely: Boolean = false,
)

@Serializable
data class SuggestedDto(
    val ingredients: List<String> = emptyList(),
    val steps: List<String> = emptyList(),
    val confidence: String = "NONE",
    val foundIn: String = "NONE",
)

@Serializable
data class FetchResponseDto(
    val title: String = "",
    val channelName: String = "",
    val thumbnailUrl: String? = null,
    val sourceUrl: String,
    val sourceType: String = "YOUTUBE",
    val description: String = "",
    val comments: List<SourceCommentDto> = emptyList(),
    val suggested: SuggestedDto = SuggestedDto(),
)
