package com.menuupdater.weeklymenu.domain

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.text.Normalizer
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Splits a menu item into dish segments and the separators between them. Separators are kept as
 * their own tokens, so `split(item).joinToString("") == item`.
 * Examples: `김치/샐러드/후식차`, `감자튀김*케찹`.
 */
object DishSegments {
    private val separators = setOf('/', '*', '&', ',')

    fun isSeparator(token: String): Boolean = token.length == 1 && token[0] in separators

    fun split(item: String): List<String> {
        val tokens = mutableListOf<String>()
        val current = StringBuilder()
        for (ch in item) {
            if (ch in separators) {
                if (current.isNotEmpty()) tokens += current.toString()
                current.clear()
                tokens += ch.toString()
            } else {
                current.append(ch)
            }
        }
        if (current.isNotEmpty()) tokens += current.toString()
        return tokens
    }

    /** The dish names in [item], trimmed, without separators or blanks. */
    fun dishes(item: String): List<String> =
        split(item).filterNot { isSeparator(it) }.map { it.trim() }.filter { it.isNotEmpty() }
}

/** The committed `assets/dish-lexicon.json`: how often each dish name was read in past menus. */
@Serializable
data class DishLexicon(
    val version: Int = 1,
    val builtFrom: String = "",
    val images: Int = 0,
    val dishes: Map<String, Int>,
    /** Real dishes that are one jamo from a more common dish (부추겉절이/배추겉절이); never rewritten. */
    val keep: List<String> = emptyList(),
    /** Known misreads too close to call by count alone, fixed directly (장각삼계팅 → 장각삼계탕). */
    val fixes: Map<String, String> = emptyMap(),
)

/**
 * Fixes OCR misreads in dish names using a frequency list of past menus. A dish one jamo away from
 * a much more common dish (공나물국 → 콩나물국) is replaced by it; anything uncertain is left alone,
 * so a new dish is never changed. Correction is idempotent.
 */
class DishCorrector(
    private val lexicon: Map<String, Int>,
    private val keep: Set<String> = emptySet(),
    private val fixes: Map<String, String> = emptyMap(),
) {
    private class Entry(val word: String, val jamo: String, val count: Int)

    // Entries bucketed by jamo length, so a lookup only compares words of a similar length.
    private val buckets: Map<Int, List<Entry>> = lexicon
        .map { (word, count) -> Entry(word, jamo(word), count) }
        .groupBy { it.jamo.length }

    /** True when every dish in [item] is in the lexicon, so a reading of it is probably right. */
    fun knows(item: String): Boolean {
        val dishes = DishSegments.dishes(item)
        return dishes.isNotEmpty() && dishes.all { (lexicon[it] ?: 0) > 0 }
    }

    fun correct(menu: WeeklyMenu): WeeklyMenu = menu.copy(tables = menu.tables.map { correct(it) })

    fun correct(table: MenuTable): MenuTable = table.copy(
        days = table.days.map { day ->
            day.copy(meals = day.meals.map { meal -> meal.copy(items = meal.items.map { correct(it) }) })
        },
    )

    fun correct(item: String): String =
        DishSegments.split(item).joinToString("") { token ->
            if (DishSegments.isSeparator(token)) token else correctSegment(token)
        }

    /** Corrects a segment's trimmed text, keeping any whitespace around it. */
    private fun correctSegment(segment: String): String {
        val core = segment.trim()
        if (core.isEmpty()) return segment
        val fixed = correctWord(core)
        if (fixed == core) return segment
        val start = segment.indexOf(core)
        return segment.substring(0, start) + fixed + segment.substring(start + core.length)
    }

    /**
     * Follows replacements until a word is stable, so a second pass changes nothing (idempotent).
     * The visited set guards against a cycle, which the thresholds shouldn't allow anyway.
     */
    private fun correctWord(word: String): String {
        var current = word
        val visited = mutableSetOf(current)
        while (true) {
            val next = replacement(current) ?: return current
            if (!visited.add(next)) return current
            current = next
        }
    }

    /** The single replacement for [word], or null to keep it. */
    private fun replacement(word: String): String? {
        fixes[word]?.let { return it.takeIf { fixed -> fixed != word } }
        if (word in keep) return null
        if (word.count { it in '가'..'힣' } < MIN_SYLLABLES) return null
        val j = jamo(word)
        val maxDistance = if (j.length >= LONG_JAMO) 2 else 1
        val neighbours = (j.length - maxDistance..j.length + maxDistance)
            .flatMap { buckets[it].orEmpty() }
            .filter { it.word != word && distance(j, it.jamo, maxDistance) <= maxDistance }
        if (neighbours.isEmpty()) return null

        val topCount = neighbours.maxOf { it.count }
        val top = neighbours.filter { it.count == topCount }
        if (top.size > 1) return null // a tie: no way to tell which is right

        val own = lexicon[word]
        val needed = if (own == null) MIN_COUNT else max(MIN_COUNT, RATIO * own)
        return if (topCount >= needed) top.single().word else null
    }

    companion object {
        /** An unknown segment is replaced only by a neighbour read at least this many times. */
        const val MIN_COUNT = 3

        /** A known segment is replaced only by a neighbour this many times as common. */
        const val RATIO = 2

        /** Segments with fewer Hangul syllables (국, 죽) are too risky to change. */
        const val MIN_SYLLABLES = 2

        /** From this many jamo on, two jamo of difference are allowed. */
        const val LONG_JAMO = 12

        private val json = Json { ignoreUnknownKeys = true }

        fun fromJson(text: String): DishCorrector {
            val lexicon = json.decodeFromString(DishLexicon.serializer(), text)
            return DishCorrector(lexicon.dishes, lexicon.keep.toSet(), lexicon.fixes)
        }

        /** Decomposes Hangul syllables into jamo (콩 → ㅋㅗㅇ), so 콩 and 공 differ by one. */
        fun jamo(text: String): String = Normalizer.normalize(text, Normalizer.Form.NFD)

        /** Levenshtein distance, giving up early (returning > [limit]) once it must exceed [limit]. */
        fun distance(a: String, b: String, limit: Int): Int {
            if (abs(a.length - b.length) > limit) return limit + 1
            var previous = IntArray(b.length + 1) { it }
            var current = IntArray(b.length + 1)
            for (i in 1..a.length) {
                current[0] = i
                var rowMin = current[0]
                for (k in 1..b.length) {
                    val cost = if (a[i - 1] == b[k - 1]) 0 else 1
                    current[k] = min(min(current[k - 1] + 1, previous[k] + 1), previous[k - 1] + cost)
                    rowMin = min(rowMin, current[k])
                }
                if (rowMin > limit) return limit + 1
                val swap = previous; previous = current; current = swap
            }
            return previous[b.length]
        }
    }
}
