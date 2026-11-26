package com.tindi.app.domain

import com.tindi.app.data.Ingredient
import com.tindi.app.data.StepDetail

/**
 * The step labels a review started with (from a suggestion or a saved recipe) and the lists they describe.
 * The user cannot edit labels, so what is saved is worked out from how far the lists have moved.
 */
data class StepBasis(
    val ingredients: List<Ingredient>,
    val steps: List<String>,
    val details: List<StepDetail>,
) {
    /**
     * The `stepDetails` to send with these lists, or null to send none.
     *
     * Labels are only kept while the steps are the same (same count, same order). Ingredients may have been added
     * or removed meanwhile, so each index follows its ingredient to its new position, and the index of an
     * ingredient that is gone is dropped (backend/API.md, "Saving a suggestion").
     */
    fun detailsFor(newIngredients: List<Ingredient>, newSteps: List<String>): List<StepDetail>? {
        if (newSteps.map { it.trim() } != steps.map { it.trim() }) return null
        val moved = newPositions(ingredients, newIngredients)
        return details.map { d -> d.copy(ingredientIndexes = d.ingredientIndexes.mapNotNull { moved[it] }.distinct()) }
    }

    companion object {
        /** Null when there are no labels, or they do not line up with the steps. */
        fun of(ingredients: List<Ingredient>, steps: List<String>, details: List<StepDetail>?): StepBasis? =
            if (details == null || steps.isEmpty() || details.size != steps.size) null
            else StepBasis(ingredients, steps, details)

        /** Old position to new position. Identical rows are paired first, then rows with the same item. */
        private fun newPositions(old: List<Ingredient>, new: List<Ingredient>): Map<Int, Int> {
            val out = mutableMapOf<Int, Int>()
            val taken = mutableSetOf<Int>()
            val sameRow = { a: Ingredient, b: Ingredient -> a.item.trim() == b.item.trim() && a.quantity.trim() == b.quantity.trim() }
            val sameItem = { a: Ingredient, b: Ingredient -> a.item.trim() == b.item.trim() }
            for (same in listOf(sameRow, sameItem)) {
                old.forEachIndexed { i, ingredient ->
                    if (i in out) return@forEachIndexed
                    val j = new.indices.firstOrNull { it !in taken && same(ingredient, new[it]) } ?: return@forEachIndexed
                    out[i] = j
                    taken += j
                }
            }
            return out
        }
    }
}
