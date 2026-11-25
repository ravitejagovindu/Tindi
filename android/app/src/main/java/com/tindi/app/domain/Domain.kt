package com.tindi.app.domain

import com.tindi.app.data.AuthErrors
import com.tindi.app.data.Categories
import com.tindi.app.data.Ingredient
import com.tindi.app.data.Recipe

/**
 * Library filter. The category narrows first, then the search looks inside it
 * by recipe name or ingredient. [categoryId] null = All.
 */
fun filterRecipes(recipes: List<Recipe>, categoryId: String?, query: String): List<Recipe> {
    val inCategory = when (categoryId) {
        null -> recipes
        Categories.UNCATEGORISED_ID -> recipes.filter { it.categoryIds.isEmpty() }
        else -> recipes.filter { categoryId in it.categoryIds }
    }
    val q = query.trim()
    if (q.isEmpty()) return inCategory
    return inCategory.filter { r ->
        r.name.contains(q, ignoreCase = true) ||
            r.ingredients.any { it.item.contains(q, ignoreCase = true) }
    }
}

object AuthValidator {
    private val emailRegex = Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")

    fun isValidEmail(email: String) = emailRegex.matches(email.trim())

    fun validateLogIn(email: String, password: String): AuthErrors? {
        val e = when {
            email.isBlank() -> "Enter your email."
            !isValidEmail(email) -> "Enter a valid email."
            else -> null
        }
        val p = if (password.isEmpty()) "Enter your password." else null
        return if (e != null || p != null) AuthErrors(email = e, password = p) else null
    }

    fun validateSignUp(name: String, email: String, password: String): AuthErrors? {
        val n = if (name.isBlank()) "Enter your name." else null
        val e = when {
            email.isBlank() -> "Enter your email."
            !isValidEmail(email) -> "Enter a valid email."
            else -> null
        }
        val p = when {
            password.isEmpty() -> "Enter a password."
            password.length < 6 -> "Use at least 6 characters."
            else -> null
        }
        return if (n != null || e != null || p != null) AuthErrors(name = n, email = e, password = p) else null
    }
}

/** Ingredients a step mentions, for the Cooking view "You need" panel. */
fun ingredientsForStep(step: String, ingredients: List<Ingredient>): List<Ingredient> {
    val text = step.lowercase()
    return ingredients.filter { ing ->
        val key = ing.item.substringBefore(',').trim().lowercase()
        val words = key.split(' ').filter { it.length > 2 }
        key.isNotEmpty() && (text.contains(key) || words.any { w -> text.contains(w.removeSuffix("s")) })
    }
}
