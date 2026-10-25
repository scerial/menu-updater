package com.menuupdater.weeklymenu

import com.menuupdater.weeklymenu.domain.DishCorrector
import com.menuupdater.weeklymenu.domain.DishSegments
import com.menuupdater.weeklymenu.domain.MenuTable
import com.menuupdater.weeklymenu.domain.MenuTableParser
import com.menuupdater.weeklymenu.domain.Rect
import com.menuupdater.weeklymenu.domain.RecognizedLine
import com.menuupdater.weeklymenu.domain.TableVoter
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate
import java.time.temporal.IsoFields

/**
 * Measures whether voting across the three preprocessing variants helps, on the local OCR corpus.
 * For each week the lexicon is rebuilt WITHOUT that week, then the week is read (a) with the
 * shipped single reading, (b) voted across the three variants, each with and without the corrector.
 * Score = share of dish names the other weeks never produced. Real new dishes inflate it equally
 * in every condition, so a drop means fewer misreads. Run with EVAL_VOTING=1; writes
 * `android/build/voting-eval.txt`.
 */
class EvaluateVoting {
    @Serializable
    private data class CorpusFile(val postDate: String = "", val variant: String = "", val image: String = "", val lines: List<CorpusLine>)

    @Serializable
    private data class CorpusLine(val text: String, val box: List<Double>)

    private class Reading(val week: String, val imageKey: String, val variant: String, val table: MenuTable)

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun evaluate() {
        assumeTrue("set EVAL_VOTING=1 to run", System.getenv("EVAL_VOTING") == "1")
        val module = File(System.getProperty("user.dir"))
        val dir = File(module.parentFile, "ocr-corpus")
        val readings = dir.listFiles { f -> f.name.endsWith(".ocr.json") }!!.sortedBy { it.name }.mapNotNull { read(it) }

        // Lexicon from readings of weeks other than `exclude` (count >= 2, as the real lexicon).
        fun lexiconWithout(exclude: String): Map<String, Int> {
            val counts = HashMap<String, Int>()
            for (r in readings) if (r.week != exclude) {
                for (dish in dishes(r.table).toSet()) counts.merge(dish, 1, Int::plus)
            }
            return counts.filterValues { it >= 2 }
        }

        val weeks = readings.map { it.week }.distinct().sorted()
        val names = listOf("single, raw", "single + lexicon", "voted, raw", "voted + lexicon")
        val unknown = IntArray(4)
        val total = IntArray(4)
        val sb = StringBuilder()
        sb.appendLine("Voting evaluation, leave-one-week-out over ${weeks.size} weeks, ${readings.size} readings")
        sb.appendLine("%-10s %6s  %s".format("week", "dishes", names.joinToString("  ") { "%-17s".format(it) }))

        for (week in weeks) {
            val lexicon = lexiconWithout(week)
            val corrector = DishCorrector(lexicon, BuildDishLexicon.KEEP, BuildDishLexicon.FIXES)
            val perImage = readings.filter { it.week == week }.groupBy { it.imageKey }

            val per = Array(4) { mutableListOf<String>() }
            for ((_, variants) in perImage) {
                val main = variants.firstOrNull { it.variant == "CONTRAST_UPSCALE" } ?: continue
                val ordered = listOf(main) + variants.filter { it !== main }
                val single = main.table
                val voted = TableVoter.vote(ordered.map { it.table }, isKnown = { corrector.knows(it) })
                per[0] += dishes(single)
                per[1] += dishes(corrector.correct(single))
                per[2] += dishes(voted)
                per[3] += dishes(corrector.correct(voted))
            }
            if (week in weeks.takeLast(4)) {
                sb.appendLine("  [$week] still unknown after voting + lexicon: " +
                    per[3].filter { it !in lexicon }.distinct().sorted().joinToString(", "))
            }
            val cells = (0..3).map { i ->
                val u = per[i].count { it !in lexicon }
                unknown[i] += u
                total[i] += per[i].size
                "%5d (%4.1f%%)    ".format(u, 100.0 * u / per[i].size.coerceAtLeast(1))
            }
            sb.appendLine("%-10s %6d  %s".format(week, per[0].size, cells.joinToString("  ")))
        }
        sb.appendLine()
        sb.appendLine("TOTAL unknown dish names (lower is better):")
        for (i in 0..3) sb.appendLine("  %-18s %5d of %d  (%.2f%%)".format(names[i], unknown[i], total[i], 100.0 * unknown[i] / total[i]))

        File(module.parentFile, "build").mkdirs()
        File(module.parentFile, "build/voting-eval.txt").writeText(sb.toString())
        println(sb)
    }

    private fun dishes(table: MenuTable): List<String> =
        table.days.flatMap { it.meals }.flatMap { it.items }.flatMap { DishSegments.dishes(it) }

    private fun read(file: File): Reading? {
        val c = json.decodeFromString(CorpusFile.serializer(), file.readText())
        val date = runCatching { LocalDate.parse(c.postDate) }.getOrNull() ?: return null
        val lines = c.lines.filter { it.box.size == 4 }.map {
            val (l, t, r, b) = it.box
            RecognizedLine(it.text, Rect.fromTopDown(l, t, r, b))
        }
        val table = MenuTableParser.parse(lines) ?: return null
        val week = "%d-W%02d".format(date.get(IsoFields.WEEK_BASED_YEAR), date.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR))
        return Reading(week, c.image, c.variant, table)
    }
}
