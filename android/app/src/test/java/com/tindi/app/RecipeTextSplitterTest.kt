package com.tindi.app

import com.tindi.app.data.Ingredient
import com.tindi.app.data.SourceType
import com.tindi.app.domain.RecipeTextSplitter
import com.tindi.app.domain.draftFromPaste
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RecipeTextSplitterTest {
    private val full = """
        Quick dal tadka

        Ingredients:
        - 1 cup toor dal
        - 2 tbsp ghee
        - 4 garlic cloves

        Method:
        1. Pressure cook the dal.
        2. Heat the ghee and add garlic.
        3. Pour the tadka over the dal.

        Follow me on Instagram @tindi
    """.trimIndent()

    @Test fun headingsSplitIngredientsFromSteps() {
        val (ing, steps) = RecipeTextSplitter.forPaste(full)
        assertEquals(listOf("1 cup toor dal", "2 tbsp ghee", "4 garlic cloves"), ing)
        assertEquals(listOf("Pressure cook the dal.", "Heat the ghee and add garlic.", "Pour the tadka over the dal."), steps)
    }

    @Test fun noHeadingsPutsEverythingInSteps() {
        val (ing, steps) = RecipeTextSplitter.forPaste("Boil the rice.\n\n- Drain it\n2. Serve hot")
        assertTrue(ing.isEmpty())
        assertEquals(listOf("Boil the rice.", "Drain it", "Serve hot"), steps)
    }

    @Test fun onlyAnIngredientsHeadingLeavesStepsEmpty() {
        val (ing, steps) = RecipeTextSplitter.forPaste("Ingredients\n2 eggs\n1 cup milk")
        assertEquals(listOf("2 eggs", "1 cup milk"), ing)
        assertTrue(steps.isEmpty())
    }

    @Test fun headingsWithNothingUnderThemGiveNothing() {
        val (ing, steps) = RecipeTextSplitter.forPaste("Ingredients:\nMethod:\n")
        assertTrue(ing.isEmpty())
        assertTrue(steps.isEmpty()) // labels are not recipe text
    }

    @Test fun whatYouNeedAndStepsHeadingsWithoutColons() {
        val (ing, steps) = RecipeTextSplitter.forPaste("What you need\n2 cups flour\n1 egg\nSteps\nMix it.\nBake it.")
        assertEquals(listOf("2 cups flour", "1 egg"), ing)
        assertEquals(listOf("Mix it.", "Bake it."), steps)
    }

    @Test fun hindiEnglishHeadings() {
        val (ing, steps) = RecipeTextSplitter.forPaste("Samagri (Ingredients):\n1 cup chawal\n1 tsp namak\n\nVidhi (Method):\nChawal dho lein.\nUbaal lein.")
        assertEquals(2, ing.size)
        assertEquals(2, steps.size)
        val (i2, s2) = RecipeTextSplitter.forPaste("सामग्री:\n2 कप चावल\n\nविधि:\nचावल धो लें।")
        assertEquals(listOf("2 कप चावल"), i2)
        assertEquals(listOf("चावल धो लें।"), s2)
    }

    @Test fun emojiHeadingsIncludingVariationSelectors() {
        val text = "▶️ INGREDIENTS 🧂\n✔️ 2 onions\n✔️ 1 tbsp oil\n\n👩‍🍳 DIRECTIONS\n👉 Fry the onions.\n👉 Serve."
        val (ing, steps) = RecipeTextSplitter.forPaste(text)
        assertEquals(listOf("2 onions", "1 tbsp oil"), ing)
        assertEquals(listOf("Fry the onions.", "Serve."), steps)
    }

    @Test fun sectionsStopAtNotesAndNumberingIsStripped() {
        val (ing, steps) = RecipeTextSplitter.forPaste("Ingredients\n- 1 banana\nMethod\n1) Mash it.\n2) Eat it.\nNotes:\n- Ripe ones work best")
        assertEquals(listOf("1 banana"), ing)
        assertEquals(listOf("Mash it.", "Eat it."), steps)
    }

    @Test fun quantitiesAreNotMistakenForNumbering() {
        val (ing, _) = RecipeTextSplitter.forPaste("Ingredients:\n2 cups rice\n1.5 tsp salt\n1/2 cup milk\n½ tsp pepper")
        assertEquals(listOf("2 cups rice", "1.5 tsp salt", "1/2 cup milk", "½ tsp pepper"), ing)
    }

    @Test fun lengthsAndCountAreKeptInsideTheServerLimits() {
        val long = "x".repeat(2000)
        val (_, steps) = RecipeTextSplitter.forPaste((1..150).joinToString("\n") { "Step line $it $long" })
        assertEquals(100, steps.size)
        assertTrue(steps.all { it.length <= 1000 })
        assertTrue(RecipeTextSplitter.asIngredients("Ingredients:\n" + "y".repeat(500)).all { it.length <= 120 })
    }

    @Test fun parseIngredientSplitsQuantityFromItem() {
        fun p(s: String) = RecipeTextSplitter.parseIngredient(s)
        assertEquals(Ingredient("250 g", "paneer, cubed"), p("250 g paneer, cubed"))
        assertEquals(Ingredient("2 tbsp", "butter"), p("2 tbsp butter"))
        assertEquals(Ingredient("1/2 cup", "cream"), p("1/2 cup cream"))
        assertEquals(Ingredient("3", "tomatoes, pureed"), p("3 tomatoes, pureed"))
        assertEquals(Ingredient("1-2", "green chillies"), p("1-2 green chillies"))
        assertEquals(Ingredient("", "salt to taste"), p("salt to taste"))
        assertEquals(Ingredient("", "garam masala"), p("garam masala"))
        assertEquals(Ingredient("2", "सेब"), p("2 सेब"))
    }

    @Test fun useAsHelpersPickTheRightSectionOrFallBackToEveryLine() {
        assertEquals(listOf("1 cup toor dal", "2 tbsp ghee", "4 garlic cloves"), RecipeTextSplitter.asIngredients(full))
        assertEquals(3, RecipeTextSplitter.asSteps(full).size)
        // a comment with no headings: every line is used
        assertEquals(listOf("2 eggs", "1 cup milk"), RecipeTextSplitter.asIngredients("- 2 eggs\n- 1 cup milk"))
        assertEquals(listOf("Whisk.", "Cook."), RecipeTextSplitter.asSteps("1. Whisk.\n2. Cook."))
        assertTrue(RecipeTextSplitter.asSteps("   \n  ").isEmpty())
    }

    @Test fun pastedDraftIsPastedWithNoVideoAndKeepsTheOriginalText() {
        val d = draftFromPaste("  $full  ")
        assertEquals(SourceType.Pasted, d.sourceType)
        assertNull(d.sourceUrl)
        assertNull(d.video)
        assertEquals("", d.title)
        assertEquals(Ingredient("1 cup", "toor dal"), d.ingredients[0])
        assertEquals(3, d.steps.size)
        assertEquals(full, d.sourceText)
        assertNotNull(d.sourceText)
    }
}
