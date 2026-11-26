package com.tindi.app.domain

import com.tindi.app.data.Ingredient
import com.tindi.app.data.Recipe
import java.util.Locale
import kotlin.math.floor
import kotlin.math.max

/** The pictures of the Cooking view. The names match the server's `illustration` values, ignoring case. */
enum class Illustration {
    Prep, Soak, Blend, Mix, Saute, Simmer, Boil, Fry, Bake, Steam, Rest, Serve, Generic;

    companion object {
        /** A value this app does not know shows [Generic], so the server's set can grow. */
        fun from(value: String): Illustration = entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: Generic
    }
}

/** One "Tap as you add" row. [index] is the position in the recipe's ingredient list. */
data class CookIngredient(val index: Int, val quantity: String, val name: String)

/** Everything the Cooking view shows for one step. */
data class CookStep(
    val title: String,
    val text: String,
    val illustration: Illustration,
    val ingredients: List<CookIngredient>,
    /** Ingredients no step mentions. Only the first step has them. */
    val alsoNeeded: List<CookIngredient> = emptyList(),
) {
    val rows: List<CookIngredient> get() = ingredients + alsoNeeded
}

/** The recipe as steps to cook, from the server's labels when it has them and from the step text when not. */
fun cookSteps(recipe: Recipe): List<CookStep> {
    val details = recipe.stepDetails?.takeIf { it.size == recipe.steps.size }
    val rows = recipe.ingredients.mapIndexed { i, ingredient -> cookIngredient(i, ingredient) }
    val keys = if (details == null) rows.map { ingredientKeys(it.name) } else emptyList()

    val used: List<List<Int>> = recipe.steps.mapIndexed { i, text ->
        if (details != null) {
            details[i].ingredientIndexes.filter { it in rows.indices }.distinct().sorted()
        } else {
            val inStep = words(text).flatMapTo(mutableSetOf()) { forms(it) }
            rows.indices.filter { r -> keys[r].any { key -> forms(key).any { it in inStep } } }
        }
    }
    val mentioned = used.flatten().toSet()
    val unmentioned = rows.filter { it.index !in mentioned }

    return recipe.steps.mapIndexed { i, text ->
        val label = details?.get(i)
        CookStep(
            title = label?.name?.trim()?.takeIf { it.isNotEmpty() } ?: titleFromText(text) ?: "Step ${i + 1}",
            text = text,
            illustration = if (label != null) Illustration.from(label.illustration) else illustrationFor(text),
            ingredients = used[i].map { rows[it] },
            alsoNeeded = if (i == 0) unmentioned else emptyList(),
        )
    }
}

// ---------- Chapter name ----------

/** The first word of the step, capitalised, if it is a plain word of 3 to 12 letters. Otherwise null. */
fun titleFromText(step: String): String? {
    val first = step.trim().takeWhile { !it.isWhitespace() }.trimEnd(',', '.', ':', ';', '!')
    if (first.length !in 3..12 || !first.all { it.isLetter() }) return null
    return first.lowercase(Locale.ROOT).replaceFirstChar { it.titlecase(Locale.ROOT) }
}

// ---------- Ingredients ----------

/** The quantity (shown bold) and the name. A row with no leading amount is all name. */
fun cookIngredient(index: Int, ingredient: Ingredient): CookIngredient {
    val parts = if (ingredient.quantity.isBlank()) RecipeTextSplitter.parseIngredient(ingredient.item) else ingredient
    return CookIngredient(index, parts.quantity.trim(), parts.item.trim())
}

private val WORD = Regex("[\\p{L}\\p{M}]+")

private fun words(text: String): List<String> = WORD.findAll(text.lowercase(Locale.ROOT)).map { it.value }.toList()

/** Words that say how much or how it was prepared, not what it is. */
private val FILLER = (
    "a an and or of to for the with as in on at per from plus some few more any if " +
        "fresh freshly chopped sliced diced minced grated crushed peeled washed soaked boiled cooked cooking roasted " +
        "fried dried ground whole finely roughly thinly large small medium big optional required needed taste garnish " +
        "garnishing serving serve cup cups tbsp tsp tablespoon tablespoons teaspoon teaspoons kg kgs gm gms gram grams " +
        "ml litre litres liter liters oz lb lbs pinch pinches handful handfuls bunch piece pieces pcs inch inches"
    ).split(' ').toSet()

/** Words many ingredients share. They only count when the name has nothing more telling. */
private val WEAK = (
    "red green black white yellow brown hot warm cold powder paste sauce juice leaf leaves seed seeds cube cubes " +
        "slice slices stick sticks sprig sprigs clove cloves florets baking"
    ).split(' ').toSet()

/** The main words of an ingredient name: what has to appear in a step for the step to use it. */
fun ingredientKeys(name: String): List<String> {
    val all = words(name.substringBefore(',').replace(Regex("\\([^)]*\\)"), " ")).filter { it.length > 1 }
    val meaningful = all.filter { it !in FILLER }
    return meaningful.filter { it !in WEAK }.ifEmpty { meaningful }.ifEmpty { all }
}

/** A word and its likely singulars, so "tomatoes" meets "tomato" and "chillies" meets "chilli". */
private fun forms(word: String): Set<String> = buildSet {
    add(word)
    if (word.length > 3 && word.endsWith("s")) add(word.dropLast(1))
    if (word.length > 3 && word.endsWith("es")) add(word.dropLast(2))
    if (word.length > 4 && word.endsWith("ies")) add(word.dropLast(3) + "y")
}

// ---------- Illustration ----------

// In order of priority: a step that says several of these gets the first.
// Past forms ("chopped", "boiled") are left out because they mostly describe an ingredient.
private val KEYWORDS: List<Pair<Illustration, Set<String>>> = listOf(
    Illustration.Blend to "blend blends blending blender grind grinds grinding grinder",
    Illustration.Simmer to "simmer simmers simmering",
    Illustration.Boil to "boil boils boiling",
    Illustration.Fry to "fry fries frying",
    Illustration.Saute to "saute sauté sautes sautés sauteing sauteeing sautéing",
    Illustration.Prep to "chop chops chopping cut cuts cutting",
    Illustration.Mix to "mix mixes mixing stir stirs stirring",
    Illustration.Bake to "bake bakes baking roast roasts roasting",
    Illustration.Steam to "steam steams steaming",
    Illustration.Soak to "soak soaks soaking",
    Illustration.Rest to "rest rests resting",
    Illustration.Serve to "serve serves serving",
).map { (picture, list) -> picture to list.split(' ').toSet() }

/** The picture for a step with no label, picked by what the text says. */
fun illustrationFor(step: String): Illustration {
    val all = words(step)
    val verbs = all.filterIndexed { i, w ->
        val notAVerb = (w == "baking" && all.getOrNull(i + 1) in setOf("soda", "powder")) ||
            (w == "rest" && all.getOrNull(i - 1) == "the") // "add the rest of the water"
        !notAVerb
    }.toSet()
    return KEYWORDS.firstOrNull { (_, keys) -> keys.any { it in verbs } }?.first ?: Illustration.Generic
}

// ---------- Words on screen ----------

private val MIDDLE_CHEERS = listOf("Looking good.", "Smells great already.", "Nearly there.")

/** The speech bubble. A one step recipe counts as its first step. */
fun cheerFor(index: Int, total: Int, allTicked: Boolean): String = when {
    allTicked -> "All in. Nice."
    index == 0 -> "Let's get cooking."
    index >= total - 1 -> "Last step. Serve it up."
    else -> MIDDLE_CHEERS[(index - 1) % MIDDLE_CHEERS.size]
}

fun mainButtonLabel(index: Int, total: Int): String = if (index >= total - 1) "Finish" else "Done, next"

fun finishedLine(total: Int): String =
    if (total == 1) "1 step done. Enjoy your meal." else "All $total steps done. Enjoy your meal."

// ---------- One cooking session ----------

/** An ingredient ticked on a step. The same ingredient on another step is ticked on its own. */
data class Tick(val step: Int, val ingredient: Int)

/** Where the cook is. Lives in memory while the Cooking view is open, and is never saved. */
data class CookSession(
    val step: Int = 0,
    val finished: Boolean = false,
    val ticks: Set<Tick> = emptySet(),
) {
    /** The step to show: a refreshed recipe may have fewer steps than before. */
    fun index(total: Int): Int = step.coerceIn(0, max(total - 1, 0))

    fun next(total: Int): CookSession =
        if (index(total) >= total - 1) copy(finished = true) else copy(step = index(total) + 1)

    fun previous(total: Int): CookSession = copy(step = max(index(total) - 1, 0))

    fun jump(to: Int, total: Int): CookSession = copy(step = to.coerceIn(0, max(total - 1, 0)), finished = false)

    fun toggle(step: Int, ingredient: Int): CookSession {
        val tick = Tick(step, ingredient)
        return copy(ticks = if (tick in ticks) ticks - tick else ticks + tick)
    }

    fun isTicked(step: Int, ingredient: Int): Boolean = Tick(step, ingredient) in ticks

    fun tickedCount(step: Int, rows: List<CookIngredient>): Int = rows.count { isTicked(step, it.index) }

    /** False for a step with no ingredients: there is nothing to have finished adding. */
    fun allTicked(step: Int, rows: List<CookIngredient>): Boolean =
        rows.isNotEmpty() && tickedCount(step, rows) == rows.size
}

// ---------- Journey bar ----------

/**
 * How wide each stop is, in pixels. A stop is never narrower than its name or [minimum]. Stops that fit share the
 * whole [available] width (equally when the longest name allows it); otherwise they keep their size and scroll.
 */
fun journeyStopWidths(labels: List<Int>, available: Int, minimum: Int): List<Int> {
    if (labels.isEmpty()) return emptyList()
    val natural = labels.map { max(it, minimum) }
    if (natural.max() * natural.size <= available) return List(natural.size) { available / natural.size }
    val spare = available - natural.sum()
    return if (spare >= 0) natural.map { it + floor(spare.toDouble() / natural.size).toInt() } else natural
}

/** The scroll position that puts stop [index] in the middle. The scroller clamps it at the far end. */
fun journeyScrollFor(widths: List<Int>, index: Int, viewport: Int, sidePadding: Int): Int {
    if (index !in widths.indices) return 0
    val centre = sidePadding + widths.take(index).sum() + widths[index] / 2
    return max(centre - viewport / 2, 0)
}
