package com.menuupdater.weeklymenu

import com.menuupdater.weeklymenu.domain.DishCorrector
import com.menuupdater.weeklymenu.domain.DishLexicon
import com.menuupdater.weeklymenu.domain.DishSegments
import com.menuupdater.weeklymenu.domain.MenuTableParser
import com.menuupdater.weeklymenu.domain.Rect
import com.menuupdater.weeklymenu.domain.RecognizedLine
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate

/**
 * Builds `assets/dish-lexicon.json` from the OCR corpus that `DishCorpusDump` pulled off a device
 * into `android/ocr-corpus/`. Not part of the normal run: set `BUILD_LEXICON=1`. Also writes
 * `android/build/lexicon-report.txt` (the rewrites the corrector would make, and a hold-out check on
 * the newest week) so the thresholds can be reviewed and tuned in seconds, without the phone.
 *
 *   $env:BUILD_LEXICON = "1"; .\gradlew.bat :app:testDebugUnitTest --tests "*BuildDishLexicon*"
 */
class BuildDishLexicon {
    @Serializable
    private data class CorpusFile(val postDate: String = "", val variant: String = "", val lines: List<CorpusLine>)

    @Serializable
    private data class CorpusLine(val text: String, val box: List<Double>)

    private class Reading(val date: LocalDate, val dishes: List<String>)

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    @Test
    fun build() {
        assumeTrue("set BUILD_LEXICON=1 to rebuild the dish lexicon", System.getenv("BUILD_LEXICON") == "1")

        // Gradle runs unit tests from the module directory (android/app).
        val module = File(System.getProperty("user.dir"))
        val corpusDir = File(module.parentFile, "ocr-corpus")
        val files = corpusDir.listFiles { f -> f.name.endsWith(".ocr.json") }?.sortedBy { it.name }
            ?: error("No corpus at $corpusDir — run DishCorpusDump on a device and adb pull it first")
        check(files.isNotEmpty()) { "Empty corpus at $corpusDir" }

        val readings = files.mapNotNull { read(it) }
        val dates = readings.map { it.date }
        val latest = dates.max()
        val holdOutFrom = latest.minusDays(6)

        val full = count(readings)
        val lexicon = full.filterValues { it >= MIN_LEXICON_COUNT }.toSortedMap()
        val builtFrom = "${dates.min()}..$latest"

        val out = File(module, "src/main/assets/dish-lexicon.json")
        out.writeText(
            json.encodeToString(DishLexicon.serializer(), DishLexicon(1, builtFrom, readings.size, lexicon, KEEP.sorted(), FIXES.toSortedMap())) + "\n",
        )

        File(module.parentFile, "build").mkdirs()
        File(module.parentFile, "build/lexicon-report.txt").writeText(
            report(readings, full, lexicon, builtFrom, holdOutFrom),
        )
        println("lexicon: ${lexicon.size} dishes from ${readings.size} readings ($builtFrom) -> $out")
    }

    private fun read(file: File): Reading? {
        val corpus = json.decodeFromString(CorpusFile.serializer(), file.readText())
        val lines = corpus.lines.filter { it.box.size == 4 }.map {
            val (l, t, r, b) = it.box
            RecognizedLine(it.text, Rect.fromTopDown(l, t, r, b))
        }
        val table = MenuTableParser.parse(lines) ?: return null
        val date = runCatching { LocalDate.parse(corpus.postDate) }.getOrNull() ?: return null
        val dishes = table.days.flatMap { it.meals }.flatMap { it.items }.flatMap { DishSegments.dishes(it) }
        return Reading(date, dishes)
    }

    /** How many readings (image x variant) each dish name appeared in. A dish counts once per reading. */
    private fun count(readings: List<Reading>): Map<String, Int> {
        val counts = HashMap<String, Int>()
        for (reading in readings) for (dish in reading.dishes.toSet()) counts.merge(dish, 1, Int::plus)
        return counts
    }

    private fun report(
        readings: List<Reading>,
        full: Map<String, Int>,
        lexicon: Map<String, Int>,
        builtFrom: String,
        holdOutFrom: LocalDate,
    ): String {
        val sb = StringBuilder()
        sb.appendLine("Dish lexicon report")
        sb.appendLine("corpus: ${readings.size} readings, $builtFrom, ${full.size} distinct names, ${lexicon.size} in lexicon (count >= $MIN_LEXICON_COUNT)")
        sb.appendLine("thresholds: MIN_COUNT=${DishCorrector.MIN_COUNT} RATIO=${DishCorrector.RATIO} MIN_SYLLABLES=${DishCorrector.MIN_SYLLABLES}")
        sb.appendLine()

        val corrector = DishCorrector(lexicon, KEEP, FIXES)
        sb.appendLine("== Mappings: every name seen in the corpus that the corrector would rewrite ==")
        val mappings = full.keys.sorted()
            .map { it to corrector.correct(it) }
            .filter { (from, to) -> from != to }
        for ((from, to) in mappings) {
            sb.appendLine("  %s (%d) -> %s (%d)".format(from, full[from] ?: 0, to, lexicon[to] ?: full[to] ?: 0))
        }
        sb.appendLine("  total: ${mappings.size}")
        sb.appendLine()

        // Hold-out: a lexicon that has never seen the newest week, applied to that week.
        val past = readings.filter { it.date < holdOutFrom }
        val week = readings.filter { it.date >= holdOutFrom }
        val pastLexicon = count(past).filterValues { it >= MIN_LEXICON_COUNT }
        val pastCorrector = DishCorrector(pastLexicon, KEEP, FIXES)
        sb.appendLine("== Hold-out: lexicon from ${past.size} readings before $holdOutFrom, applied to ${week.size} readings of the newest week ==")
        val changes = week.flatMap { it.dishes }.groupingBy { it }.eachCount().toSortedMap()
            .map { (dish, n) -> Triple(dish, pastCorrector.correct(dish), n) }
            .filter { (from, to, _) -> from != to }
        for ((from, to, n) in changes) {
            // Judge against the full corpus: a fix to a name that is common overall is likely right.
            val verdict = when {
                (full[to] ?: 0) > (full[from] ?: 0) * DishCorrector.RATIO -> "likely right"
                (full[to] ?: 0) > (full[from] ?: 0) -> "unsure"
                else -> "CHECK (target is rarer overall)"
            }
            sb.appendLine("  %s -> %s  x%d  [%s]  overall %d -> %d".format(from, to, n, verdict, full[from] ?: 0, full[to] ?: 0))
        }
        sb.appendLine("  total changes: ${changes.size}")
        sb.appendLine()

        sb.appendLine("== Not fixed: rare names in the newest week with no confident neighbour (new dishes or hard misreads) ==")
        val kept = week.flatMap { it.dishes }.toSet()
            .filter { (pastLexicon[it] ?: 0) == 0 && pastCorrector.correct(it) == it }.sorted()
        sb.appendLine("  " + kept.joinToString(", "))
        return sb.toString()
    }

    companion object {
        const val MIN_LEXICON_COUNT = 2

        // Reviewed by hand from lexicon-report.txt; carried into the JSON on every rebuild.
        /** Real dishes one jamo from a more common dish. Counting alone would "fix" them wrongly. */
        val KEEP = setOf(
            "부추겉절이", // not 배추겉절이
            "곤약메추리알장조림", // not 돈육메추리알장조림
        )

        /** Misreads too close to call by count (e.g. 4 reads vs 5), so they are fixed directly. */
        val FIXES = mapOf(
            "장각삼계팅" to "장각삼계탕",
            "숭능" to "숭늉", // the reader never gets 숭늉 right
            "중늉" to "숭늉",
            "간장돈육고기" to "간장돈육불고기", // a dropped syllable is out of the fixer's range
        )
    }
}
