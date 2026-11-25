package com.tindi.app

import com.tindi.app.data.Categories
import com.tindi.app.data.Ingredient
import com.tindi.app.data.Recipe
import com.tindi.app.data.SourceType
import com.tindi.app.domain.AuthValidator
import com.tindi.app.domain.filterRecipes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class LibraryLogicTest {
    private fun recipe(id: String, name: String, items: List<String>, cats: List<String>) = Recipe(
        id = id, name = name, ingredients = items.map { Ingredient("1", it) }, steps = listOf("Cook."),
        categoryIds = cats, sourceType = SourceType.Pasted,
    )

    private val paneer = recipe("1", "Paneer Butter Masala", listOf("paneer", "butter"), listOf("dinner"))
    private val oats = recipe("2", "Masala Oats", listOf("oats", "onion"), listOf("breakfast", "healthy"))
    private val chaat = recipe("3", "Sprouts Chaat", listOf("sprouts", "onion"), emptyList())
    private val all = listOf(paneer, oats, chaat)

    @Test fun allCategoriesNoQueryReturnsEverything() {
        assertEquals(all, filterRecipes(all, null, ""))
    }

    @Test fun categoryNarrowsList() {
        assertEquals(listOf(paneer), filterRecipes(all, "dinner", ""))
    }

    @Test fun recipeInSeveralCategoriesShowsInEach() {
        assertEquals(listOf(oats), filterRecipes(all, "breakfast", ""))
        assertEquals(listOf(oats), filterRecipes(all, "healthy", ""))
    }

    @Test fun uncategorisedChipShowsRecipesWithNoCategory() {
        assertEquals(listOf(chaat), filterRecipes(all, Categories.UNCATEGORISED_ID, ""))
    }

    @Test fun searchMatchesNameIgnoringCase() {
        assertEquals(listOf(paneer, oats), filterRecipes(all, null, "MASALA"))
    }

    @Test fun searchMatchesIngredient() {
        assertEquals(listOf(oats, chaat), filterRecipes(all, null, "onion"))
    }

    @Test fun searchLooksInsideTheSelectedCategoryOnly() {
        // "onion" is in oats and chaat, but only oats is in Breakfast.
        assertEquals(listOf(oats), filterRecipes(all, "breakfast", "onion"))
        assertEquals(emptyList<Recipe>(), filterRecipes(all, "dinner", "onion"))
    }

    @Test fun blankQueryIsIgnored() {
        assertEquals(listOf(paneer), filterRecipes(all, "dinner", "   "))
    }

    @Test fun authValidation() {
        assertNotNull(AuthValidator.validateLogIn("", "x"))
        assertNotNull(AuthValidator.validateLogIn("not-an-email", "x"))
        assertNull(AuthValidator.validateLogIn("a@b.co", "x"))
        assertNotNull(AuthValidator.validateSignUp("", "a@b.co", "123456"))
        assertNotNull(AuthValidator.validateSignUp("Ravi", "a@b.co", "12345"))
        assertNull(AuthValidator.validateSignUp("Ravi", "a@b.co", "123456"))
    }
}
