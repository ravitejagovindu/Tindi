package com.tindi.app.domain

import com.tindi.app.data.Confidence
import com.tindi.app.data.Ingredient
import com.tindi.app.data.RecipeDraft
import com.tindi.app.data.SourceType
import com.tindi.app.data.VideoInfo
import com.tindi.app.data.VideoSource

data class SplitText(val ingredients: List<String>, val steps: List<String>, val foundHeadings: Boolean)

/**
 * Simple rule based splitter for pasted text (same idea as the server's): lines under an "ingredients" heading
 * become ingredients, lines under a "method / steps" heading become steps. No headings: everything is steps.
 */
object RecipeTextSplitter {
    const val MAX_ITEMS = 100
    const val MAX_INGREDIENT = 120
    const val MAX_STEP = 1000

    private val ING_WORDS = "ingredients?(?:\\s+list)?|what\\s+you(?:'ll|\\u2019ll|\\s+will)?\\s+need|you(?:'ll|\\u2019ll|\\s+will)\\s+need" +
        "|things\\s+you(?:'ll|\\u2019ll|\\s+will)?\\s+need|samagri|saamagri|\\u0938\\u093e\\u092e\\u0917\\u094d\\u0930\\u0940"
    private val METHOD_WORDS = "method|steps?|directions?|instructions?|preparation|procedure|how\\s+to\\s+make(?:\\s+it)?|how\\s+to\\s+cook" +
        "|vidhi|banane\\s+ki\\s+vidhi|\\u0935\\u093f\\u0927\\u093f|\\u092c\\u0928\\u093e\\u0928\\u0947\\s+\\u0915\\u0940\\s+\\u0935\\u093f\\u0927\\u093f"
    private const val PREFIX = "(?:the\\s+|cooking\\s+|preparation\\s+|recipe\\s+)?"
    private const val TAIL = "\\s*(?:\\([^)]{1,40}\\)|for\\s+[^:]{1,40})?\\s*([:\\uff1a\\-\\u2013\\u2014])?\\s*(.*)$"
    private val opts = setOf(RegexOption.IGNORE_CASE)
    private val ingHead = Regex("^$PREFIX(?:$ING_WORDS)(?![\\p{L}\\p{M}\\p{N}])$TAIL", opts)
    private val methodHead = Regex("^$PREFIX(?:$METHOD_WORDS)(?![\\p{L}\\p{M}\\p{N}])$TAIL", opts)
    private val stopHead = Regex(
        "^(?:notes?|tips?|tricks?|serving|serves|servings|to\\s+serve|nutrition|timestamps?|chapters?|links?|follow|subscribe" +
            "|connect|social|music|equipment|disclaimer|about|business|contact|related|watch|more\\s+videos?)(?![\\p{L}\\p{M}\\p{N}]).*$",
        opts,
    )
    private val variation = Regex("[\\uFE00-\\uFE0F\\u20E3]")
    private val leadingJunk = Regex("^[^\\p{L}\\p{N}\"'(\\u00bc-\\u00be\\u2150-\\u215e]+")
    private val numbering = Regex(
        "^(?:(?:step|stage)\\s*\\d{1,3}\\s*[:.)\\-\\u2013]?\\s*|\\(?\\d{1,3}\\s*[.)\\]]\\s+|\\(?\\d{1,3}\\s*[.)\\]](?=\\p{L}))",
        opts,
    )
    private val marker = Regex(
        "^\\s*(?:[\\-\\u2013\\u2014\\u2022*\\u25cf\\u25aa\\u25ba\\u25b6\\u2714\\u2705\\u27a1\\u2192]|\\p{So}|\\(?\\d{1,3}\\s*[.)\\]]|(?i:step\\s*\\d))",
    )

    /** Strips emoji, bullets and numbering from the start of a line, and decoration from the end. */
    fun clean(line: String): String {
        var s = variation.replace(line.trim(), "")
        s = leadingJunk.replace(s, "")
        s = numbering.replace(s, "")
        s = leadingJunk.replace(s, "")
        s = s.replace(Regex("[*_#]+$"), "").replace(Regex("[^\\p{L}\\p{M}\\p{N})\\].:;!?\"'%\\u0964\\u0965]+$"), "")
        return s.trim()
    }

    /** Every non-blank line, cleaned. */
    fun lines(text: String): List<String> = text.lines().map { clean(it) }.filter { it.isNotEmpty() && !isBareHeading(it) }

    /** A heading on its own line ("Method:"), which is a label and not recipe text. */
    private fun isBareHeading(cleaned: String): Boolean = heading(cleaned)?.second?.isEmpty() == true

    private enum class Section { None, Ingredients, Method }

    private fun heading(cleaned: String): Pair<Section, String>? {
        if (cleaned.length > 80 && !cleaned.contains(':')) return null
        for ((sec, re) in listOf(Section.Ingredients to ingHead, Section.Method to methodHead)) {
            val m = re.matchEntire(cleaned) ?: continue
            val colon = m.groupValues[1].isNotEmpty()
            val tail = m.groupValues[2].trim()
            if (tail.isEmpty() || colon) return sec to tail
        }
        return null
    }

    fun split(text: String): SplitText {
        val ingredients = mutableListOf<String>()
        val steps = mutableListOf<String>()
        var section = Section.None
        var found = false
        var blankBefore = false
        var markered = false
        var items = 0

        fun add(value: String) {
            val v = clean(value)
            if (v.isEmpty()) return
            when (section) {
                Section.Ingredients -> if (ingredients.size < MAX_ITEMS) ingredients += v.take(MAX_INGREDIENT).trimEnd()
                Section.Method -> if (steps.size < MAX_ITEMS) steps += v.take(MAX_STEP).trimEnd()
                Section.None -> Unit
            }
        }

        for (raw in text.lines()) {
            val line = raw.trim()
            if (line.isEmpty()) { blankBefore = true; continue }
            val cleaned = clean(line)
            val head = heading(cleaned)
            if (head != null) {
                found = true
                section = head.first
                blankBefore = false; markered = false; items = 0
                if (head.second.isNotBlank()) { items++; add(head.second) }
                continue
            }
            if (section == Section.None) { blankBefore = false; continue }
            if (cleaned.length <= 60 && stopHead.matches(cleaned)) { section = Section.None; blankBefore = false; continue }
            val hasMarker = marker.containsMatchIn(line)
            if (blankBefore && items > 0 && !hasMarker && markered) { section = Section.None; blankBefore = false; continue }
            blankBefore = false
            if (cleaned.endsWith(":") && !hasMarker) continue // "For the gravy:"
            if (hasMarker) markered = true
            items++
            add(cleaned)
        }
        return SplitText(dedupe(ingredients), dedupe(steps), found)
    }

    private fun dedupe(list: List<String>): List<String> {
        val out = mutableListOf<String>()
        for (s in list) if (out.isEmpty() || !out.last().equals(s, ignoreCase = true)) out += s
        return out
    }

    /** Pasted text: split by headings. Without headings (or nothing under them) all lines go to Steps. */
    fun forPaste(text: String): Pair<List<String>, List<String>> {
        val s = split(text)
        if (s.foundHeadings && (s.ingredients.isNotEmpty() || s.steps.isNotEmpty())) return s.ingredients to s.steps
        return emptyList<String>() to lines(text).take(MAX_ITEMS).map { it.take(MAX_STEP) }
    }

    /** "Use as ingredients" on a block of video text: its ingredients section if it has one, else every line. */
    fun asIngredients(text: String): List<String> {
        val s = split(text)
        val list = if (s.foundHeadings && s.ingredients.isNotEmpty()) s.ingredients else lines(text)
        return list.take(MAX_ITEMS).map { it.take(MAX_INGREDIENT).trimEnd() }
    }

    /** "Use as steps" on a block of video text: its method section if it has one, else every line. */
    fun asSteps(text: String): List<String> {
        val s = split(text)
        val list = if (s.foundHeadings && s.steps.isNotEmpty()) s.steps else lines(text)
        return list.take(MAX_ITEMS).map { it.take(MAX_STEP).trimEnd() }
    }

    private val unitWords = "cups?|tbsp|tsp|tablespoons?|teaspoons?|kgs?|grams?|gms?|g|ml|ltrs?|litres?|liters?|l|oz|lbs?|pinch(?:es)?" +
        "|cloves?|pieces?|pcs|inch(?:es)?|bunch(?:es)?|handfuls?|sprigs?|slices?|cans?|packets?|sticks?"
    private val quantity = Regex(
        "^((?:\\d+(?:[./]\\d+)?|[\\u00bc-\\u00be\\u2150-\\u215e])(?:\\s*(?:-|to)\\s*(?:\\d+(?:[./]\\d+)?|[\\u00bc-\\u00be\\u2150-\\u215e]))?" +
            "(?:\\s*[\\u00bc-\\u00be\\u2150-\\u215e])?(?:\\s*(?:$unitWords)\\b\\.?)?)\\s+(.+)$",
        opts,
    )

    /** "250 g paneer, cubed" becomes quantity "250 g" and item "paneer, cubed". No leading amount: all of it is the item. */
    fun parseIngredient(line: String): Ingredient {
        val v = line.trim()
        val m = quantity.matchEntire(v)
        if (m != null && m.groupValues[1].length <= 40) return Ingredient(m.groupValues[1].trim(), m.groupValues[2].trim())
        return Ingredient("", v)
    }
}

/** Builds the Review draft for a fetched video. The suggested lists may be empty: the user fills them in. */
fun draftFromVideo(src: VideoSource) = RecipeDraft(
    sourceType = SourceType.YouTube,
    sourceUrl = src.url,
    title = src.title,
    ingredients = src.suggestedIngredients.map(RecipeTextSplitter::parseIngredient),
    steps = src.suggestedSteps,
    video = VideoInfo(src.title, src.channel, src.thumbnailUrl, src.description, src.comments, src.confidence, src.foundIn),
)

/** Builds the Review draft for pasted text, with no network call. */
fun draftFromPaste(text: String): RecipeDraft {
    val (ingredients, steps) = RecipeTextSplitter.forPaste(text)
    return RecipeDraft(
        sourceType = SourceType.Pasted,
        sourceUrl = null,
        ingredients = ingredients.map(RecipeTextSplitter::parseIngredient),
        steps = steps,
        sourceText = text.trim(),
    )
}

/** The helper panel's note applies when the server was not confident. */
val VideoInfo.needsNote: Boolean get() = confidence != Confidence.High

const val LOW_CONFIDENCE_NOTE = "Couldn't find the recipe automatically. Check the text below and copy what you need."
