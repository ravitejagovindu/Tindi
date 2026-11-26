package com.tindi.app.data

import kotlinx.serialization.Serializable

@Serializable
enum class SourceType { YouTube, Pasted }

@Serializable
data class Ingredient(val quantity: String, val item: String) {
    val display: String get() = if (quantity.isBlank()) item else "$quantity $item"
}

@Serializable
data class Recipe(
    val id: String,
    val name: String,
    val ingredients: List<Ingredient>,
    val steps: List<String>,
    /** Empty list = "Uncategorised". A recipe can sit in several categories. */
    val categoryIds: List<String>,
    val sourceType: SourceType,
    val sourceUrl: String? = null,
)

@Serializable
data class Category(val id: String, val name: String, val builtIn: Boolean = false)

/** What the UI knows about the signed in user. Never carries the password. */
data class Session(val name: String, val email: String)

/** From GET /me. */
data class Profile(val name: String, val email: String, val recipeCount: Int, val categoryCount: Int)

/** What the editor sends to create or replace a recipe. */
data class RecipeInput(
    val name: String,
    val ingredients: List<Ingredient>,
    val steps: List<String>,
    val categoryIds: List<String>,
    val sourceType: SourceType,
    val sourceUrl: String?,
)

object Categories {
    /** Virtual category: a recipe with no categoryIds belongs here. Never stored. */
    const val UNCATEGORISED_ID = "uncategorised"
    const val UNCATEGORISED_NAME = "Uncategorised"

    /** Same ids as the server, so the list works before the first load. */
    val starter: List<Category> = listOf(
        Category("00000000-0000-4000-8000-000000000001", "Breakfast", true),
        Category("00000000-0000-4000-8000-000000000002", "Lunch", true),
        Category("00000000-0000-4000-8000-000000000003", "Snacks", true),
        Category("00000000-0000-4000-8000-000000000004", "Dinner", true),
        Category("00000000-0000-4000-8000-000000000005", "Healthy", true),
        Category("00000000-0000-4000-8000-000000000006", "Protein", true),
    )
}

/** What Add recipe hands to Review. Nothing in it is saved until the user taps Save. */
data class RecipeDraft(
    val sourceType: SourceType,
    val sourceUrl: String?,
    val title: String = "",
    val ingredients: List<Ingredient> = emptyList(),
    val steps: List<String> = emptyList(),
    /** Pasted recipes keep the original text so the user can copy from it in Review. */
    val sourceText: String? = null,
    /** YouTube recipes keep what the server found, for the "From the video" panel. */
    val video: VideoInfo? = null,
)

/** The video details Review shows and the helper panel works from. */
data class VideoInfo(
    val title: String,
    val channel: String,
    val thumbnailUrl: String?,
    val description: String,
    val comments: List<SourceComment>,
    val confidence: Confidence,
    val foundIn: FoundIn,
)

data class SourceComment(val author: String, val text: String, val likeCount: Int, val isPinnedLikely: Boolean)

enum class Confidence { High, Low, None }

enum class FoundIn { Description, Comment, None }
