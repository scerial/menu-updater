package com.menuupdater.weeklymenu.domain

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * `shared/translations/en.json`, shared with iOS: English for dishes, cafeteria and venue names,
 * meal names and weekdays. Korean stays the key everywhere in the app; this is display-only.
 */
@Serializable
data class TranslationData(
    val version: Int = 1,
    /** Canonical dish name → English. */
    val dishes: Map<String, String> = emptyMap(),
    /** Common OCR misread → canonical dish name (iOS has no corrector, so it relies on these). */
    val aliases: Map<String, String> = emptyMap(),
    /**
     * Parts for the word-by-word guess. A value starting with `^` moves to the front in English
     * (무침 → "^Seasoned": 고사리무침 → "Seasoned Bracken"); the rest keep the Korean order, which
     * already reads as an English compound (돈육김치볶음 → "Pork Kimchi Stir-fry").
     */
    val glossary: Map<String, String> = emptyMap(),
    /** Cafeterias (OCR titles), buildings and venues from the hours notice. */
    val places: Map<String, String> = emptyMap(),
    /** 조식/중식/석식 and the hours list's venue-level meal names. */
    val meals: Map<String, String> = emptyMap(),
    /** 월 → Mon, as printed in the menu's column headers. */
    val weekdays: Map<String, String> = emptyMap(),
)

/**
 * Turns Korean menu text into English for display. Every function returns its input unchanged
 * when it has no translation, so a missing or partial file never hides anything.
 */
class Translator(private val data: TranslationData) {
    enum class Source { DICTIONARY, GUESS, NONE }

    private val maxPart = data.glossary.keys.maxOfOrNull { it.length } ?: 0

    /**
     * A whole menu item, translated segment by segment. Separators are kept but spaced for English
     * ("토스트&딸기잼" → "Toast & Strawberry Jam"), and `*` (the menu's "with") reads as `+`.
     */
    fun dish(item: String): String =
        tokens(item).joinToString("") { token ->
            when {
                isSeparator(token) -> SPACED[token] ?: token
                else -> segment(token).first.trim()
            }
        }.replace(Regex(" {2,}"), " ").trim()

    /** How each dish in [item] was translated; for coverage reports. */
    fun sources(item: String): List<Source> =
        tokens(item).filterNot { isSeparator(it) || it.isBlank() }.map { segment(it).second }

    // The menu also joins dishes with + and ~ (청포묵+양념장); the corrector's segments don't need to.
    private fun tokens(item: String): List<String> =
        DishSegments.split(item).flatMap { token ->
            if (DishSegments.isSeparator(token)) listOf(token) else EXTRA_SEPARATORS.splitKeeping(token)
        }

    private fun isSeparator(token: String): Boolean =
        DishSegments.isSeparator(token) || (token.length == 1 && token[0] in EXTRA_SEPARATOR_CHARS)

    private fun Regex.splitKeeping(text: String): List<String> {
        val out = mutableListOf<String>()
        var last = 0
        for (m in findAll(text)) {
            if (m.range.first > last) out += text.substring(last, m.range.first)
            out += m.value
            last = m.range.last + 1
        }
        if (last < text.length) out += text.substring(last)
        return out
    }

    fun meal(name: String): String = data.meals[name.trim()] ?: name

    fun weekday(name: String): String = data.weekdays[name.trim()] ?: name

    fun place(name: String): String = data.places[name.trim()] ?: name

    /** One dish, keeping the whitespace around it. */
    private fun segment(raw: String): Pair<String, Source> {
        val core = raw.trim()
        if (core.isEmpty()) return raw to Source.NONE
        val (english, source) = translate(core)
        if (source == Source.NONE) return raw to Source.NONE
        val start = raw.indexOf(core)
        return (raw.substring(0, start) + english + raw.substring(start + core.length)) to source
    }

    private fun translate(core: String): Pair<String, Source> {
        val compact = core.replace(" ", "")
        for (candidate in listOf(core, compact)) {
            val key = data.aliases[candidate] ?: candidate
            data.dishes[key]?.let { return it to Source.DICTIONARY }
        }
        guess(core)?.let { return it to Source.GUESS }
        return core to Source.NONE
    }

    private class Phrase(val text: String, val wholeDish: Boolean)

    /**
     * Every space-separated word must resolve, or there is no guess. Words that are whole dishes
     * (or several dishes run together, 감자튀김케찹) are joined with "&"; otherwise a word is built
     * from glossary parts, fewest parts first.
     */
    private fun guess(core: String): String? {
        val phrases = core.split(Regex("\\s+")).filter { it.isNotEmpty() }.map { resolve(it) ?: return null }
        if (phrases.isEmpty()) return null
        val separator = if (phrases.size > 1 && phrases.all { it.wholeDish }) " & " else " "
        return phrases.joinToString(separator) { it.text }
    }

    private fun resolve(word: String): Phrase? {
        dishName(word)?.let { return Phrase(it, wholeDish = true) }
        split(word, dishKeys, maxDish, minLength = 2)?.takeIf { it.size > 1 }?.let { parts ->
            return Phrase(parts.joinToString(" & ") { dishName(it)!! }, wholeDish = true)
        }
        val parts = split(word, data.glossary.keys, maxPart, minLength = 1) ?: return null
        val front = mutableListOf<String>()
        val rest = mutableListOf<String>()
        for (part in parts) {
            val english = data.glossary.getValue(part)
            if (english.startsWith("^")) front += english.substring(1) else rest += english
        }
        return Phrase((front + rest).joinToString(" "), wholeDish = false)
    }

    private fun dishName(word: String): String? = data.dishes[data.aliases[word] ?: word]

    private val dishKeys: Set<String> = data.dishes.keys + data.aliases.keys
    private val maxDish = dishKeys.maxOfOrNull { it.length } ?: 0

    /** The fewest [keys] that exactly cover [word], each at least [minLength] long, or null. */
    private fun split(word: String, keys: Set<String>, maxLength: Int, minLength: Int): List<String>? {
        if (maxLength == 0) return null
        val n = word.length
        val best = IntArray(n + 1) { Int.MAX_VALUE }
        val from = IntArray(n + 1) { -1 }
        best[0] = 0
        for (end in 1..n) {
            for (len in minLength..minOf(maxLength, end)) {
                val start = end - len
                if (best[start] == Int.MAX_VALUE) continue
                if (word.substring(start, end) !in keys) continue
                if (best[start] + 1 < best[end]) {
                    best[end] = best[start] + 1
                    from[end] = start
                }
            }
        }
        if (best[n] == Int.MAX_VALUE) return null
        val parts = ArrayDeque<String>()
        var end = n
        while (end > 0) {
            val start = from[end]
            parts.addFirst(word.substring(start, end))
            end = start
        }
        return parts.toList()
    }

    companion object {
        private const val EXTRA_SEPARATOR_CHARS = "+~"
        private val SPACED = mapOf("/" to " / ", "*" to " + ", "&" to " & ", "," to ", ", "+" to " + ", "~" to " ~ ")
        private val EXTRA_SEPARATORS = Regex("[+~]")
        private val json = Json { ignoreUnknownKeys = true }

        fun fromJson(text: String): Translator = Translator(json.decodeFromString(TranslationData.serializer(), text))
    }
}
