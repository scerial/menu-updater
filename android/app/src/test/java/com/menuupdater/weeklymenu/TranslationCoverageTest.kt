package com.menuupdater.weeklymenu

import com.menuupdater.weeklymenu.domain.DishCorrector
import com.menuupdater.weeklymenu.domain.DishSegments
import com.menuupdater.weeklymenu.domain.MenuTableParser
import com.menuupdater.weeklymenu.domain.Rect
import com.menuupdater.weeklymenu.domain.RecognizedLine
import com.menuupdater.weeklymenu.domain.Translator
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * How much of a real menu the committed `en.json` translates: by dictionary, by word-by-word guess,
 * or left in Korean. Always runs on the 3 OCR fixtures; with `EVAL_TRANSLATION=1` also on the local
 * corpus, writing `android/build/translation-report.txt` with every Korean leftover and every guess.
 */
class TranslationCoverageTest {
    private fun resource(name: String) = javaClass.classLoader!!.getResourceAsStream(name)!!.bufferedReader().use { it.readText() }

    private val translator = Translator.fromJson(resource("en.json"))
    private val corrector = DishCorrector.fromJson(resource("dish-lexicon.json"))

    /** The weakest way any part of [item] was translated. */
    private fun overall(item: String): Translator.Source {
        val sources = translator.sources(item)
        return when {
            Translator.Source.NONE in sources -> Translator.Source.NONE
            Translator.Source.GUESS in sources -> Translator.Source.GUESS
            else -> Translator.Source.DICTIONARY
        }
    }

    private fun dishes(lines: List<RecognizedLine>): List<String> =
        corrector.correct(MenuTableParser.parse(lines)!!).days
            .flatMap { it.meals }.flatMap { it.items }.flatMap { DishSegments.dishes(it) }

    @Test
    fun fixturesAreMostlyTranslated() {
        val all = listOf("hyangseol1", "faculty", "medical").flatMap { dishes(OcrFixtures.lines(it)) }
        val sources = all.map { overall(it) }
        val untranslated = all.filterIndexed { i, _ -> sources[i] == Translator.Source.NONE }
        val share = 1.0 - untranslated.size.toDouble() / all.size
        println("fixtures: ${all.size} dishes, ${"%.1f".format(share * 100)}% translated, Korean left: $untranslated")
        assertTrue("only ${"%.1f".format(share * 100)}% translated: $untranslated", share >= 0.95)
    }

    @Serializable
    private data class CorpusFile(val variant: String = "", val lines: List<CorpusLine>)

    @Serializable
    private data class CorpusLine(val text: String, val box: List<Double>)

    @Test
    fun corpusReport() {
        assumeTrue("set EVAL_TRANSLATION=1", System.getenv("EVAL_TRANSLATION") == "1")
        val module = File(System.getProperty("user.dir"))
        val json = Json { ignoreUnknownKeys = true }
        val counts = mutableMapOf<String, Int>()
        for (file in File(module.parentFile, "ocr-corpus").listFiles { f -> f.name.endsWith("_CONTRAST_UPSCALE.ocr.json") }!!) {
            val c = json.decodeFromString(CorpusFile.serializer(), file.readText())
            val lines = c.lines.filter { it.box.size == 4 }.map {
                val (l, t, r, b) = it.box
                RecognizedLine(it.text, Rect.fromTopDown(l, t, r, b))
            }
            if (MenuTableParser.parse(lines) == null) continue
            for (dish in dishes(lines)) counts.merge(dish, 1, Int::plus)
        }
        val bySource = counts.entries.groupBy { overall(it.key) }
        val total = counts.values.sum()
        val sb = StringBuilder("Translation coverage over the corpus (shipped reading, after the corrector)\n")
        for (s in Translator.Source.entries) {
            val n = bySource[s].orEmpty().sumOf { it.value }
            sb.appendLine("  %-10s %5d of %d (%.1f%%)".format(s, n, total, 100.0 * n / total))
        }
        sb.appendLine("\n== Guesses (check these read well) ==")
        bySource[Translator.Source.GUESS].orEmpty().sortedByDescending { it.value }
            .forEach { sb.appendLine("  ${it.key} -> ${translator.dish(it.key)}  x${it.value}") }
        sb.appendLine("\n== Left in Korean ==")
        bySource[Translator.Source.NONE].orEmpty().sortedByDescending { it.value }
            .forEach { sb.appendLine("  ${it.key}  x${it.value}") }
        File(module.parentFile, "build").mkdirs()
        File(module.parentFile, "build/translation-report.txt").writeText(sb.toString())
        println(sb)
    }
}
