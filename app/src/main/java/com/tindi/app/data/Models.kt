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

object Categories {
    /** Virtual category: a recipe with no categoryIds belongs here. Never stored. */
    const val UNCATEGORISED_ID = "uncategorised"
    const val UNCATEGORISED_NAME = "Uncategorised"

    val starter: List<Category> = listOf(
        Category("starter-breakfast", "Breakfast", true),
        Category("starter-lunch", "Lunch", true),
        Category("starter-snacks", "Snacks", true),
        Category("starter-dinner", "Dinner", true),
        Category("starter-healthy", "Healthy", true),
        Category("starter-protein", "Protein", true),
    )
}

/** What Add recipe hands to Review. */
data class RecipeDraft(
    val sourceText: String,
    val sourceType: SourceType,
    val sourceUrl: String?,
    val suggestedName: String = "",
)
