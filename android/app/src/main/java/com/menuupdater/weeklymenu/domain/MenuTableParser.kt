package com.menuupdater.weeklymenu.domain

import kotlin.math.abs

/**
 * Rebuilds a weekly menu table (one column per day, one row group per meal) from OCR lines and
 * their positions. A direct port of the iOS `MenuTableParser`: ML Kit's line order is unreliable,
 * so the table is reconstructed from the bounding boxes. Boxes are in bottom-left normalized space
 * (see [Rect]).
 */
object MenuTableParser {
    private val mealNames = setOf("조식", "중식", "석식")
    private val headerRegex =
        Regex("""(\d{1,2})\s*월\s*(\d{1,2})\s*일\s*\(\s*([월화수목금토일])\s*\)""")

    // The title reads "<cafeteria> 주간메뉴표". ML Kit sometimes drops a character (주간메뉴표 → 주간뉴표),
    // so match 주간…표 loosely rather than the exact word, then strip it to leave the cafeteria name.
    private val titleRegex = Regex("""주간\S*표""")

    // The restaurant's logo/watermark (AGOODPLACE, HALAL, SCH, …) OCRs into cells as fake dishes.
    // Dish names are Korean, so strip runs of Latin letters and drop anything with no Hangul left.
    private val latinRun = Regex("[A-Za-z]{2,}")

    private data class Header(val month: Int, val day: Int, val weekday: String, val box: Rect)

    fun parse(lines: List<RecognizedLine>): MenuTable? {
        val headers = lines.mapNotNull { header(it) }.sortedBy { it.box.midX }
        val firstHeader = headers.firstOrNull() ?: return null

        val headerBottom = headers.minOf { it.box.minY }
        val columnSpacing = columnSpacing(headers)

        // Meal labels sit in the left-hand column of the table.
        val mealLabels = lines
            .filter { it.text.trim() in mealNames && it.box.midX < firstHeader.box.minX }
            .sortedByDescending { it.box.midY }

        // Everything at or below the origin-notice block is footer text. The block's first row can
        // sit above its "원산지" label, so take the highest line that looks like origin text.
        val originKeywords = listOf("원산지", "국내산", "외국산", "중국산", "수입산")
        val originTops = lines
            .filter { line -> originKeywords.any { line.text.contains(it) } }
            .map { it.box.midY }
        val footerCutoff = (originTops.maxOrNull() ?: -0.02) + 0.02

        val cells = MutableList(headers.size) {
            MutableList(maxOf(mealLabels.size, 1)) { mutableListOf<RecognizedLine>() }
        }
        for (line in lines) {
            val text = line.text.trim()
            if (line.box.midY >= headerBottom || line.box.midY <= footerCutoff) continue
            if (text in mealNames || cleanItem(text) == null) continue
            val column = nearestColumn(line, headers, columnSpacing * 0.6) ?: continue
            val meal = nearestMeal(line, mealLabels)
            cells[column][meal].add(line)
        }

        val mealTitles = if (mealLabels.isEmpty()) listOf("메뉴") else mealLabels.map { it.text.trim() }
        val commonItems = mealTitles.indices.map { extractCommonItem(cells, it) }

        val days = headers.mapIndexed { column, header ->
            val meals = mealTitles.indices.mapNotNull { meal ->
                val items = cells[column][meal]
                    .sortedByDescending { it.box.midY }
                    .mapNotNull { cleanItem(it.text) }
                    .toMutableList()
                commonItems[meal]?.let { items.add(it) }
                if (items.isEmpty()) null else MealSection(mealTitles[meal], items)
            }
            MenuDay(header.month, header.day, header.weekday, meals)
        }

        val cafeteria = lines.firstOrNull { titleRegex.containsMatchIn(it.text) }
            ?.text?.replace(titleRegex, "")?.trim() ?: ""
        return MenuTable(cafeteria, days)
    }

    private fun header(line: RecognizedLine): Header? {
        val match = headerRegex.find(line.text) ?: return null
        val (month, day, weekday) = match.destructured
        return Header(month.toInt(), day.toInt(), weekday, line.box)
    }

    private fun columnSpacing(headers: List<Header>): Double {
        if (headers.size <= 1) return (headers.firstOrNull()?.box?.width ?: 0.1) * 2
        val gaps = headers.zipWithNext { a, b -> b.box.midX - a.box.midX }
        return gaps.sum() / gaps.size
    }

    private fun nearestColumn(line: RecognizedLine, headers: List<Header>, maxDistance: Double): Int? {
        val nearest = headers.indices.minByOrNull { abs(headers[it].box.midX - line.box.midX) }
            ?: return null
        if (abs(headers[nearest].box.midX - line.box.midX) > maxDistance) return null
        return nearest
    }

    private fun nearestMeal(line: RecognizedLine, labels: List<RecognizedLine>): Int =
        labels.indices.minByOrNull { abs(labels[it].box.midY - line.box.midY) } ?: 0

    /**
     * Rows that span every day (e.g. "아산쌀밥/김치/샐러드") are centered, so they OCR into the middle
     * column. Treat the lowest middle-column item as shared when it sits below every other column's
     * last item.
     */
    private fun extractCommonItem(cells: MutableList<MutableList<MutableList<RecognizedLine>>>, meal: Int): String? {
        if (cells.size % 2 == 0) return null
        val center = cells.size / 2
        val centerLines = cells[center][meal].sortedByDescending { it.box.midY }
        val candidate = centerLines.lastOrNull() ?: return null

        val otherBottoms = cells.indices.filter { it != center }
            .mapNotNull { cells[it][meal].minOfOrNull { line -> line.box.midY } }
        val otherBottom = otherBottoms.minOrNull() ?: return null
        if (candidate.box.midY >= otherBottom - 0.01) return null

        cells[center][meal].removeAll { it == candidate }
        return cleanItem(candidate.text)
    }

    /**
     * Cleans a recognized line into a dish name, or returns null if it is logo/watermark noise.
     * Strips runs of Latin letters (e.g. a trailing "HALAL") and drops the line when no Hangul
     * remains, or when only a stray syllable is left after stripping (e.g. "MAL사").
     */
    private fun cleanItem(text: String): String? {
        val hadLatin = latinRun.containsMatchIn(text)
        val cleaned = text.replace(latinRun, " ").replace(Regex("""\s{2,}"""), " ").trim()
        val hangulCount = cleaned.count { it in '가'..'힣' }
        if (hangulCount == 0) return null
        if (hadLatin && hangulCount < 2) return null
        return cleaned
    }
}
