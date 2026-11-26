package com.tindi.app

import com.tindi.app.data.Ingredient
import com.tindi.app.data.Recipe
import com.tindi.app.data.SourceType
import com.tindi.app.data.StepDetail
import com.tindi.app.domain.CookSession
import com.tindi.app.domain.Illustration
import com.tindi.app.domain.cheerFor
import com.tindi.app.domain.cookIngredient
import com.tindi.app.domain.cookSteps
import com.tindi.app.domain.finishedLine
import com.tindi.app.domain.illustrationFor
import com.tindi.app.domain.ingredientKeys
import com.tindi.app.domain.journeyScrollFor
import com.tindi.app.domain.journeyStopWidths
import com.tindi.app.domain.mainButtonLabel
import com.tindi.app.domain.titleFromText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private fun recipe(
    ingredients: List<String>,
    steps: List<String>,
    details: List<StepDetail>? = null,
) = Recipe(
    id = "r", name = "Test", ingredients = ingredients.map { Ingredient("", it) }, steps = steps,
    categoryIds = emptyList(), sourceType = SourceType.Pasted, stepDetails = details,
)

class StepNameTest {
    @Test fun firstPlainWordIsTheNameCapitalised() {
        assertEquals("Blend", titleFromText("blend the tomatoes."))
        assertEquals("Soak", titleFromText("  Soak, then drain."))
        assertEquals("Melt", titleFromText("MELT the butter"))
        assertEquals("Sauté", titleFromText("sauté the onions"))
        assertEquals("Add", titleFromText("Add cream"))
        assertEquals("Caramelising", titleFromText("caramelising takes a while")) // 12 letters
    }

    @Test fun anythingElseIsNotAName() {
        for (step in listOf(
            "In a pan, melt butter", // 2 letters
            "Refrigeration helps", // 13 letters
            "Stir-fry the greens",
            "2 minutes later, add salt",
            "1. Wash the rice",
            "(Optional) garnish",
            "प्याज़ काटें",
            "",
            "   ",
        )) assertNull(step, titleFromText(step))
    }

    @Test fun stepsWithoutANameAreNumbered() {
        val steps = cookSteps(recipe(emptyList(), listOf("Boil water.", "In a pan, fry.", "2 eggs next.")))
        assertEquals(listOf("Boil", "Step 2", "Step 3"), steps.map { it.title })
    }

    @Test fun serverNamesWinAndABlankOneFallsBackToTheText() {
        val steps = cookSteps(
            recipe(
                emptyList(), listOf("Blend it.", "Pour it out.", "On low heat, wait."),
                listOf(StepDetail("Puree"), StepDetail(""), StepDetail("  ")),
            ),
        )
        assertEquals(listOf("Puree", "Pour", "Step 3"), steps.map { it.title })
    }
}

class IngredientMatchingTest {
    private fun matched(ingredient: String, step: String): Boolean =
        cookSteps(recipe(listOf(ingredient), listOf("Wait.", step)))[1].ingredients.isNotEmpty()

    @Test fun mainWordsMatchWithoutRegardToCaseOrPlurals() {
        assertTrue(matched("3 large Tomatoes", "Blend the tomato."))
        assertTrue(matched("1 tomato", "BLEND THE TOMATOES."))
        assertTrue(matched("250 g Paneer, cubed", "Add the paneer cubes."))
        assertTrue(matched("1 cup cashews", "Soak a few cashew nuts."))
        assertTrue(matched("2 green chillies", "Slit the chilli."))
        assertTrue(matched("1 tsp kasuri methi", "Add kasuri methi."))
        assertTrue(matched("½ tsp garam masala", "Sprinkle the masala."))
        assertTrue(matched("Salt to taste", "Add salt."))
        assertTrue(matched("2 eggs", "Beat the egg."))
        assertTrue(matched("warm water", "Soak in water."))
        assertTrue(matched("2 cups जीरा", "तेल में जीरा डालें"))
    }

    @Test fun onlyWholeWordsCount() {
        assertFalse(matched("2 tbsp oil", "Boil the pasta."))
        assertFalse(matched("1 cup rice", "Check the price."))
        assertFalse(matched("1 egg", "Slice the eggplant."))
    }

    @Test fun fillerAndSharedWordsDoNotMatchOnTheirOwn() {
        assertFalse(matched("1 tsp chilli powder", "Add turmeric powder."))
        assertFalse(matched("1 cup chopped coriander", "Add the chopped onion."))
        assertFalse(matched("1 tsp black pepper", "Add black salt."))
        assertFalse(matched("1 cup fresh cream", "Use fresh herbs."))
        assertFalse(matched("1 tbsp cooking oil", "While cooking, wait."))
    }

    @Test fun keysAreTheTellingWords() {
        assertEquals(listOf("paneer"), ingredientKeys("Paneer, cubed"))
        assertEquals(listOf("kashmiri", "chilli"), ingredientKeys("Kashmiri red chilli powder"))
        assertEquals(listOf("butter"), ingredientKeys("butter (or ghee)"))
        assertEquals(listOf("cloves"), ingredientKeys("cloves")) // the spice: nothing else to go by
    }

    @Test fun anIngredientCanBeUsedByMoreThanOneStep() {
        val steps = cookSteps(recipe(listOf("cream", "salt"), listOf("Add cream and salt.", "Swirl the cream.")))
        assertEquals(listOf(0, 1), steps[0].ingredients.map { it.index })
        assertEquals(listOf(0), steps[1].ingredients.map { it.index })
    }

    @Test fun serverIndexesPickTheIngredientsInRecipeOrder() {
        val steps = cookSteps(
            recipe(
                listOf("tomatoes", "butter", "salt"), listOf("Blend.", "Simmer."),
                // Out of order, repeated and out of range, and nothing here depends on the text.
                listOf(StepDetail("Blend", listOf(2, 0, 2, 9, -1)), StepDetail("Simmer", listOf(1))),
            ),
        )
        assertEquals(listOf(0, 2), steps[0].ingredients.map { it.index })
        assertEquals(listOf(1), steps[1].ingredients.map { it.index })
    }

    @Test fun detailsThatDoNotLineUpWithTheStepsAreIgnored() {
        val steps = cookSteps(recipe(listOf("salt"), listOf("Add salt.", "Serve."), listOf(StepDetail("Only", listOf(0)))))
        assertEquals(listOf("Add", "Serve"), steps.map { it.title })
        assertEquals(listOf(0), steps[0].ingredients.map { it.index })
    }
}

class AlsoNeededTest {
    @Test fun ingredientsNoStepMentionsGoOnStepOne() {
        val steps = cookSteps(
            recipe(listOf("rice", "ghee", "saffron", "cumin"), listOf("Wash the rice.", "Fry cumin.", "Serve.")),
        )
        assertEquals(listOf("rice"), steps[0].ingredients.map { it.name })
        assertEquals(listOf("ghee", "saffron"), steps[0].alsoNeeded.map { it.name })
        assertEquals(listOf("rice", "ghee", "saffron"), steps[0].rows.map { it.name })
        assertTrue(steps.drop(1).all { it.alsoNeeded.isEmpty() })
    }

    @Test fun sameWithServerLabels() {
        val steps = cookSteps(
            recipe(listOf("rice", "ghee"), listOf("Wash.", "Cook."), listOf(StepDetail("Wash"), StepDetail("Cook", listOf(0)))),
        )
        assertTrue(steps[0].ingredients.isEmpty())
        assertEquals(listOf("ghee"), steps[0].alsoNeeded.map { it.name })
    }

    @Test fun everythingMentionedMeansNoExtraGroup() {
        assertTrue(cookSteps(recipe(listOf("rice"), listOf("Wash the rice.")))[0].alsoNeeded.isEmpty())
    }

    @Test fun aStepMayHaveNoRowsAtAll() {
        val steps = cookSteps(recipe(listOf("rice"), listOf("Wash the rice.", "Wait.")))
        assertTrue(steps[1].rows.isEmpty())
        assertTrue(cookSteps(recipe(emptyList(), listOf("Wait.")))[0].rows.isEmpty())
    }

    @Test fun noStepsAndOneStep() {
        assertTrue(cookSteps(recipe(listOf("rice"), emptyList())).isEmpty())
        val one = cookSteps(recipe(listOf("rice", "salt"), listOf("Cook the rice."))).single()
        assertEquals(listOf("rice", "salt"), one.rows.map { it.name })
    }

    @Test fun anyNumberOfStepsWorks() {
        val steps = cookSteps(recipe(listOf("salt"), List(23) { "Stir ${it + 1}." }))
        assertEquals(23, steps.size)
        assertEquals(listOf("salt"), steps[0].alsoNeeded.map { it.name })
    }
}

class QuantitySplitTest {
    private fun split(quantity: String, item: String) = cookIngredient(0, Ingredient(quantity, item)).let { it.quantity to it.name }

    @Test fun leadingQuantitiesAreSplitFromTheName() {
        assertEquals("1 cup" to "Cashews", split("", "1 cup Cashews"))
        assertEquals("2 tbsp" to "Butter", split("", "2 tbsp Butter"))
        assertEquals("½ tsp" to "Garam masala", split("", "½ tsp Garam masala"))
        assertEquals("250 g" to "Paneer, cubed", split("", "250 g Paneer, cubed"))
        assertEquals("250g" to "paneer", split("", "250g paneer"))
        assertEquals("1 1/2 cups" to "rice", split("", "1 1/2 cups rice"))
        assertEquals("1½ cups" to "milk", split("", "1½ cups milk"))
        assertEquals("2-3" to "chillies", split("", "2-3 chillies"))
    }

    @Test fun aSavedQuantityIsUsedAsItIs() {
        assertEquals("to taste" to "Salt", split("to taste", "Salt"))
        assertEquals("3 large" to "Tomatoes", split(" 3 large ", "Tomatoes"))
    }

    @Test fun noQuantityShowsTheWholeString() {
        assertEquals("" to "Salt to taste", split("", "Salt to taste"))
        assertEquals("" to "A pinch of sugar", split("", "A pinch of sugar"))
        assertEquals("" to "2", split("", "2"))
    }
}

class IllustrationTest {
    @Test fun serverValuesMapAndUnknownOnesAreGeneric() {
        for (value in listOf(
            "PREP", "SOAK", "BLEND", "MIX", "SAUTE", "SIMMER", "BOIL", "FRY", "BAKE", "STEAM", "REST", "SERVE", "GENERIC",
        )) assertEquals(value, Illustration.from(value).name.uppercase())
        assertEquals(13, Illustration.entries.size)
        assertEquals(Illustration.Generic, Illustration.from("GRILL"))
        assertEquals(Illustration.Generic, Illustration.from(""))
    }

    @Test fun keywordsPickThePictureWhenThereIsNoLabel() {
        val cases = mapOf(
            "Blend the tomatoes" to Illustration.Blend,
            "Grind to a paste" to Illustration.Blend,
            "Simmer for 8 minutes" to Illustration.Simmer,
            "Bring to a boil" to Illustration.Boil,
            "Deep-fry until golden" to Illustration.Fry,
            "Sauté the onions" to Illustration.Saute,
            "Keep sauteing" to Illustration.Saute,
            "Chop the onions" to Illustration.Prep,
            "Cut into cubes" to Illustration.Prep,
            "Mix well" to Illustration.Mix,
            "Keep stirring" to Illustration.Mix,
            "Bake for 20 minutes" to Illustration.Bake,
            "Roast the cumin" to Illustration.Bake,
            "Steam the idlis" to Illustration.Steam,
            "Soak overnight" to Illustration.Soak,
            "Let the dough rest" to Illustration.Rest,
            "Serve hot" to Illustration.Serve,
            "Wait for a while" to Illustration.Generic,
        )
        cases.forEach { (step, picture) -> assertEquals(step, picture, illustrationFor(step)) }
    }

    @Test fun lookalikesDoNotCount() {
        assertEquals(Illustration.Generic, illustrationFor("Add the ground cumin")) // not grinding
        assertEquals(Illustration.Serve, illustrationFor("Serve with boiled eggs"))
        assertEquals(Illustration.Mix, illustrationFor("Stir in the baking soda"))
        assertEquals(Illustration.Generic, illustrationFor("Add the rest of the water"))
        assertEquals(Illustration.Generic, illustrationFor("Arrange the cutlets"))
    }

    @Test fun theEarlierKindWinsWhenAStepSaysSeveral() {
        assertEquals(Illustration.Blend, illustrationFor("Soak the cashews, then blend"))
        assertEquals(Illustration.Simmer, illustrationFor("Stir, then simmer"))
        assertEquals(Illustration.Fry, illustrationFor("Stir-fry the greens"))
    }

    @Test fun labelsBeatKeywords() {
        val step = cookSteps(recipe(emptyList(), listOf("Blend it."), listOf(StepDetail("Blend", illustration = "REST")))).single()
        assertEquals(Illustration.Rest, step.illustration)
    }
}

class CookWordsTest {
    @Test fun buttonSaysFinishOnlyOnTheLastStep() {
        assertEquals("Done, next", mainButtonLabel(0, 5))
        assertEquals("Done, next", mainButtonLabel(3, 5))
        assertEquals("Finish", mainButtonLabel(4, 5))
        assertEquals("Finish", mainButtonLabel(0, 1))
    }

    @Test fun cheersGoByPosition() {
        val lines = List(8) { cheerFor(it, 8, allTicked = false) }
        assertEquals(
            listOf(
                "Let's get cooking.", "Looking good.", "Smells great already.", "Nearly there.",
                "Looking good.", "Smells great already.", "Nearly there.", "Last step. Serve it up.",
            ),
            lines,
        )
        assertEquals("Let's get cooking.", cheerFor(0, 1, allTicked = false))
        assertEquals("Last step. Serve it up.", cheerFor(1, 2, allTicked = false))
    }

    @Test fun allTickedBeatsPosition() {
        for (i in 0..4) assertEquals("All in. Nice.", cheerFor(i, 5, allTicked = true))
    }

    @Test fun finishedLineCountsTheSteps() {
        assertEquals("All 5 steps done. Enjoy your meal.", finishedLine(5))
        assertEquals("1 step done. Enjoy your meal.", finishedLine(1))
    }
}

class CookSessionTest {
    private val rows = cookSteps(recipe(listOf("rice", "salt"), listOf("Add rice and salt.")))[0].rows

    @Test fun nextWalksTheStepsAndThenFinishes() {
        var s = CookSession()
        s = s.next(3)
        assertEquals(1, s.step)
        s = s.next(3).next(3)
        assertEquals(2, s.step)
        assertTrue(s.finished)
        assertTrue(CookSession().next(1).finished)
    }

    @Test fun previousStopsAtTheFirstStep() {
        assertEquals(0, CookSession().previous(3).step)
        assertEquals(1, CookSession(step = 2).previous(3).step)
    }

    @Test fun jumpingKeepsTheTicks() {
        val s = CookSession().toggle(0, 1).jump(4, 5).jump(2, 5)
        assertEquals(2, s.step)
        assertTrue(s.isTicked(0, 1))
        assertEquals(4, s.jump(99, 5).step)
    }

    @Test fun ticksAreKeptPerStepAndToggleOff() {
        val s = CookSession().toggle(0, 0)
        assertTrue(s.isTicked(0, 0))
        assertFalse(s.isTicked(1, 0))
        assertFalse(s.toggle(0, 0).isTicked(0, 0))
    }

    @Test fun allTickedNeedsEveryRowAndAtLeastOne() {
        var s = CookSession().toggle(0, 0)
        assertEquals(1, s.tickedCount(0, rows))
        assertFalse(s.allTicked(0, rows))
        s = s.toggle(0, 1)
        assertTrue(s.allTicked(0, rows))
        assertFalse(s.allTicked(0, emptyList()))
    }

    @Test fun cookingAgainStartsClean() {
        // "Cook it again" is a new session.
        assertEquals(CookSession(0, false, emptySet()), CookSession())
    }

    @Test fun aShorterRefreshedRecipeStaysInRange() {
        assertEquals(1, CookSession(step = 7).index(2))
        assertEquals(0, CookSession(step = 7).index(0))
        assertEquals(0, CookSession(step = 7).previous(2).step)
    }
}

class JourneyBarTest {
    @Test fun fewStopsShareTheWidth() {
        assertEquals(listOf(100, 100, 100), journeyStopWidths(listOf(40, 50, 90), 300, 64))
        assertEquals(listOf(300), journeyStopWidths(listOf(40), 300, 64))
    }

    @Test fun aLongNameGetsItsRoomWhenEqualColumnsWouldCutIt() {
        val widths = journeyStopWidths(listOf(40, 40, 150), 300, 64)
        assertEquals(listOf(71, 71, 157), widths)
    }

    @Test fun manyStopsKeepTheirSizeAndScroll() {
        val widths = journeyStopWidths(List(20) { 40 }, 300, 64)
        assertEquals(List(20) { 64 }, widths)
        assertTrue(widths.sum() > 300)
        assertTrue(journeyStopWidths(emptyList(), 300, 64).isEmpty())
    }

    @Test fun scrollingCentresTheCurrentStop() {
        val widths = List(20) { 64 }
        assertEquals(0, journeyScrollFor(widths, 0, 300, 12)) // nothing to scroll back to
        // 12 + 9 * 64 + 32 is the middle of stop 10; it lands in the middle of the view.
        assertEquals(620 - 150, journeyScrollFor(widths, 9, 300, 12))
        assertEquals(0, journeyScrollFor(widths, 40, 300, 12))
    }
}
