package com.tindi.app.domain

import com.tindi.app.data.Confidence
import com.tindi.app.data.Method
import com.tindi.app.data.SourceType
import com.tindi.app.data.SuggestionInfo

/** The two recipe lists a server warning can point into. */
enum class RecipeList(val word: String) { Ingredients("ingredient"), Steps("step") }

/**
 * One server warning. [list] says which list it is about. [index] (0-based) is set when it points at one item,
 * which then gets a warning icon. Warnings about the recipe as a whole have neither.
 */
data class ReviewWarning(val raw: String, val list: RecipeList? = null, val index: Int? = null) {
    /** The sentence to show. The item number follows the item when rows above it are removed. */
    val text: String
        get() {
            val l = list
            val i = index
            return if (l != null && i != null) "Check ${l.word} ${i + 1}: ${raw.substringAfter(": ")}" else raw
        }
}

// The sentences the server writes (backend/API.md: item numbers are 1-based positions in the returned lists).
// Anything that does not match is still shown as it came, it just marks no item.
private val ITEM = Regex("^Check (ingredient|step) (\\d{1,3}): .+")
private val ABOUT_LIST = Regex("^(?:Removed \\d+|No|Only the first \\d+) (ingredient|step)s?\\b.*")

private fun listFor(word: String) = RecipeList.entries.first { it.word == word }

fun parseWarnings(warnings: List<String>, ingredientCount: Int, stepCount: Int): List<ReviewWarning> =
    warnings.map { it.trim() }.filter { it.isNotEmpty() }.map { raw ->
        val item = ITEM.matchEntire(raw)
        if (item != null) {
            val list = listFor(item.groupValues[1])
            val index = item.groupValues[2].toInt() - 1
            val count = if (list == RecipeList.Ingredients) ingredientCount else stepCount
            if (index in 0 until count) ReviewWarning(raw, list, index) else ReviewWarning(raw)
        } else {
            ReviewWarning(raw, ABOUT_LIST.matchEntire(raw)?.let { listFor(it.groupValues[1]) })
        }
    }

private fun bannerText(info: SuggestionInfo, source: SourceType): String? {
    val pasted = source == SourceType.Pasted
    val from = if (pasted) "pasted text" else "video text"
    return when {
        info.confidence == Confidence.None -> {
            val panel = if (pasted) "Source text" else "From the video"
            "Couldn't find a recipe automatically. Use the text in '$panel' below."
        }
        info.method == Method.Llm -> "Auto-structured from the $from. Please check before saving."
        info.method == Method.Rules -> "Matched from the $from by simple rules. Please check before saving."
        else -> null // a server that does not say how it made the suggestion
    }
}

/**
 * What Review knows about the server's suggestion and how far the user has moved away from it.
 * Empty (the default) for saved recipes being edited and for text split on the phone.
 */
data class ReviewHints(
    val suggestion: SuggestionInfo? = null,
    val warnings: List<ReviewWarning> = emptyList(),
    val dismissed: Boolean = false,
    /** True once the ingredients or steps differ from what the server suggested. */
    val edited: Boolean = false,
) {
    /** The banner sentence, or null when there is no banner. */
    fun banner(source: SourceType): String? =
        if (suggestion == null || dismissed || edited) null else bannerText(suggestion, source)

    /** Positions in [list] that get a warning icon. */
    fun flagged(list: RecipeList): Set<Int> = warnings.filter { it.list == list }.mapNotNull { it.index }.toSet()

    fun dismiss() = copy(dismissed = true)

    /** Row [index] of [list] was removed: its warnings go and the ones below move up. */
    fun removed(list: RecipeList, index: Int) = copy(
        edited = true,
        warnings = warnings.mapNotNull { w ->
            val i = w.index
            when {
                w.list != list || i == null -> w
                i == index -> null
                i > index -> w.copy(index = i - 1)
                else -> w
            }
        },
    )

    /** A row was added at the end of [list], so "No ... were found" no longer holds. */
    fun added(list: RecipeList) = copy(
        edited = true,
        warnings = warnings.filterNot { it.list == list && it.raw.startsWith("No ") },
    )

    /** The whole of [list] was replaced: nothing said about the old rows applies any more. */
    fun replaced(list: RecipeList) = copy(edited = true, warnings = warnings.filterNot { it.list == list })

    companion object {
        fun from(info: SuggestionInfo?, ingredientCount: Int, stepCount: Int) =
            if (info == null) ReviewHints()
            else ReviewHints(info, parseWarnings(info.warnings, ingredientCount, stepCount))
    }
}
