package com.tindi.app

import com.tindi.app.data.Confidence
import com.tindi.app.data.Method
import com.tindi.app.data.SourceType
import com.tindi.app.data.SuggestionInfo
import com.tindi.app.domain.RecipeList
import com.tindi.app.domain.ReviewHints
import com.tindi.app.domain.parseWarnings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SuggestionBannerTest {
    private val llmVideo = "Auto-structured from the video text. Please check before saving."
    private val rulesVideo = "Matched from the video text by simple rules. Please check before saving."
    private val noneVideo = "Couldn't find a recipe automatically. Use the text in 'From the video' below."
    private val llmPasted = "Auto-structured from the pasted text. Please check before saving."
    private val rulesPasted = "Matched from the pasted text by simple rules. Please check before saving."
    private val nonePasted = "Couldn't find a recipe automatically. Use the text in 'Source text' below."

    private val someWarnings = listOf("Check step 1: it is worded differently from the source text.")

    private fun hints(method: Method, confidence: Confidence, warnings: List<String> = emptyList()) =
        ReviewHints.from(SuggestionInfo(method, confidence, warnings), ingredientCount = 3, stepCount = 3)

    @Test fun everyMethodAndConfidenceWithAndWithoutWarnings() {
        val expected = mapOf(
            (Method.Llm to Confidence.High) to (llmVideo to llmPasted),
            (Method.Llm to Confidence.Low) to (llmVideo to llmPasted),
            (Method.Llm to Confidence.None) to (noneVideo to nonePasted),
            (Method.Rules to Confidence.High) to (rulesVideo to rulesPasted),
            (Method.Rules to Confidence.Low) to (rulesVideo to rulesPasted),
            (Method.Rules to Confidence.None) to (noneVideo to nonePasted),
            (Method.Unknown to Confidence.High) to (null to null),
            (Method.Unknown to Confidence.Low) to (null to null),
            (Method.Unknown to Confidence.None) to (noneVideo to nonePasted),
        )
        assertEquals(Method.entries.size * Confidence.entries.size, expected.size)
        for ((case, text) in expected) {
            for (warnings in listOf(emptyList(), someWarnings)) {
                val h = hints(case.first, case.second, warnings)
                assertEquals("$case $warnings", text.first, h.banner(SourceType.YouTube))
                assertEquals("$case $warnings", text.second, h.banner(SourceType.Pasted))
                // The "Check these" row follows the warnings alone.
                assertEquals("$case $warnings", warnings, h.warnings.map { it.text })
            }
        }
    }

    @Test fun noSuggestionMeansNoBannerAndNoWarnings() {
        // A saved recipe being edited, and text split on the phone.
        for (h in listOf(ReviewHints(), ReviewHints.from(null, 2, 2))) {
            assertNull(h.banner(SourceType.YouTube))
            assertNull(h.banner(SourceType.Pasted))
            assertEquals(0, h.warnings.size)
        }
    }

    @Test fun dismissingHidesTheBannerButKeepsTheWarnings() {
        val h = hints(Method.Llm, Confidence.Low, someWarnings).dismiss()
        assertNull(h.banner(SourceType.YouTube))
        assertEquals(setOf(0), h.flagged(RecipeList.Steps))
    }

    @Test fun anyChangeToTheListsHidesTheBanner() {
        val h = hints(Method.Llm, Confidence.High)
        for (changed in listOf(
            h.added(RecipeList.Ingredients), h.added(RecipeList.Steps),
            h.removed(RecipeList.Ingredients, 0), h.removed(RecipeList.Steps, 0),
            h.replaced(RecipeList.Ingredients), h.replaced(RecipeList.Steps),
        )) assertNull(changed.banner(SourceType.YouTube))
    }

    /** The wording comes from the server's GroundingCheck and RecipeStructurer. */
    private val all = listOf(
        "Check ingredient 2: a number or quantity does not match the source text.",
        "Check step 1: it is worded differently from the source text.",
        "Check step 3: it was too long and has been cut.",
        "Removed 2 ingredients that are not in the source text.",
        "Removed 1 step that is not in the source text.",
        "Only the first 100 steps are listed.",
        "No ingredients were found.",
        "The recipe may be partial: Check step 2: oven temperature is missing.",
    )

    @Test fun serverWarningsPointAtTheirItems() {
        val w = parseWarnings(all, ingredientCount = 3, stepCount = 3)
        assertEquals(all, w.map { it.text })
        assertEquals(
            listOf(
                RecipeList.Ingredients to 1, RecipeList.Steps to 0, RecipeList.Steps to 2,
                RecipeList.Ingredients to null, RecipeList.Steps to null, RecipeList.Steps to null,
                RecipeList.Ingredients to null, null to null,
            ),
            w.map { it.list to it.index },
        )
    }

    @Test fun warningsThatNameNoRealItemAreStillShownAsText() {
        val odd = listOf("Check step 0: x.", "Check step 9: y.", "Check ingredient 1: z.", "Something new from the server.", "  ")
        val w = parseWarnings(odd, ingredientCount = 0, stepCount = 3)
        assertEquals(odd.dropLast(1), w.map { it.text })
        assertEquals(emptyList<Int>(), w.mapNotNull { it.index })
    }

    @Test fun iconsAndNumbersFollowTheItemsWhenRowsAreRemoved() {
        var h = ReviewHints.from(SuggestionInfo(Method.Llm, Confidence.Low, all), 3, 3)
        assertEquals(setOf(1), h.flagged(RecipeList.Ingredients))
        assertEquals(setOf(0, 2), h.flagged(RecipeList.Steps))

        h = h.removed(RecipeList.Steps, 1) // an unflagged step above the third one
        assertEquals(setOf(0, 1), h.flagged(RecipeList.Steps))
        assertEquals("Check step 2: it was too long and has been cut.", h.warnings[2].text)
        assertEquals(setOf(1), h.flagged(RecipeList.Ingredients)) // the other list is untouched

        h = h.removed(RecipeList.Steps, 0) // the flagged step itself
        assertEquals(setOf(0), h.flagged(RecipeList.Steps))
        assertEquals("Check step 1: it was too long and has been cut.", h.warnings[1].text)
        // The model's own note is never renumbered.
        assertEquals(all.last(), h.warnings.last().text)

        h = h.removed(RecipeList.Ingredients, 2) // below the flagged ingredient
        assertEquals(setOf(1), h.flagged(RecipeList.Ingredients))
    }

    @Test fun fillingAListClearsWhatNoLongerHolds() {
        val h = ReviewHints.from(SuggestionInfo(Method.Llm, Confidence.Low, all), 3, 3)
        val added = h.added(RecipeList.Ingredients)
        assertEquals(all - "No ingredients were found.", added.warnings.map { it.text })
        val replaced = h.replaced(RecipeList.Steps)
        assertEquals(emptySet<Int>(), replaced.flagged(RecipeList.Steps))
        assertEquals(listOf(all[0], all[3], all[6], all[7]), replaced.warnings.map { it.text })
    }
}
