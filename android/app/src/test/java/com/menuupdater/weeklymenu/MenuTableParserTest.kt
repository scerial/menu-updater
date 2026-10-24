package com.menuupdater.weeklymenu

import com.menuupdater.weeklymenu.domain.MenuTableParser
import com.menuupdater.weeklymenu.domain.Rect
import com.menuupdater.weeklymenu.domain.RecognizedLine
import com.menuupdater.weeklymenu.domain.WeeklyMenu
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Ported from the iOS `MenuTableParserTests`, run against the real ML Kit fixtures from step 2.
 * Expected dish strings are the ML Kit output (misreads included: 삼계탕→삼계팅, 겉절이→걸절이,
 * 콩나물→공나물, 숭늉→숭능), so these guard the parser's geometry, not OCR accuracy.
 */
class MenuTableParserTest {
    private fun parse(name: String) = MenuTableParser.parse(OcrFixtures.lines(name))!!

    @Test
    fun parsesCafeteriaNameAndFiveDays() {
        val table = parse("hyangseol1")
        assertEquals("향설1관", table.cafeteria)
        assertEquals(
            listOf("10월 19일 (월)", "10월 20일 (화)", "10월 21일 (수)", "10월 22일 (목)", "10월 23일 (금)"),
            table.days.map { it.heading },
        )
    }

    @Test
    fun groupsItemsByColumnAndMealInReadingOrder() {
        val tuesday = parse("hyangseol1").days.first { it.day == 20 }

        assertEquals(listOf("조식", "중식", "석식"), tuesday.meals.map { it.name })
        assertEquals(
            listOf("순대야채볶음", "군만두", "숙주무침", "청경채무침", "고추무침/된장국", "아산쌀밥/김치/샐러드"),
            tuesday.meals[0].items,
        )
        assertEquals(
            listOf("장각삼계팅", "비빔만두", "열무된장무침", "콩조림", "무말랭이무침", "김치/샐러드/후식차"),
            tuesday.meals[1].items,
        )
    }

    @Test
    fun sharedRowIsAddedToEveryDayAndNotLostFromTheCenterColumn() {
        val table = parse("hyangseol1")

        for (day in table.days) {
            assertEquals(day.heading, "아산쌀밥/김치/샐러드", day.meals[0].items.last())
        }
        // Wednesday's own breakfast items must remain, with the shared row only once.
        val wednesday = table.days.first { it.day == 21 }
        assertEquals(1, wednesday.meals[0].items.count { it == "아산쌀밥/김치/샐러드" })
        assertTrue(wednesday.meals[0].items.contains("도시락김/초코머핀"))
    }

    @Test
    fun excludesTitleLogoAndOriginFooter() {
        val everything = parse("hyangseol1").days.flatMap { it.meals }.flatMap { it.items }

        assertFalse(everything.any { it.contains("원산지") || it.contains("국내산") || it.contains("상기메뉴") })
        assertFalse(everything.contains("SCH"))
        assertFalse(everything.contains("순천향대학교"))
    }

    @Test
    fun singleMealTableKeepsExtraCenterColumnItemSeparateFromSharedRow() {
        val table = parse("faculty")
        val wednesday = table.days.first { it.day == 21 }

        assertEquals("교직원식당", table.cafeteria)
        assertEquals(listOf("중식"), wednesday.meals.map { it.name })
        // The centered "샐러드, 토스트&딸기잼" row spans every day and lands last on each.
        for (day in table.days) {
            assertEquals(day.heading, "샐러드, 토스트&딸기잼", day.meals[0].items.last())
        }
        // 숭능 (숭늉) is Wednesday's own extra center-column item, kept separate from the shared row.
        assertTrue(wednesday.meals[0].items.contains("숭능"))
        assertEquals(1, table.days.count { it.meals[0].items.contains("숭능") })
    }

    /** ML Kit drops a character in 의대식당's title (주간메뉴표 → 주간뉴표); the name must still parse. */
    @Test
    fun parsesMedicalCafeteriaDespiteTitleMisread() {
        val table = parse("medical")
        assertEquals("순천향의대식당", table.cafeteria)
        assertEquals(5, table.days.size)
        assertTrue(table.days.all { day -> day.meals.map { it.name } == listOf("중식") })
    }

    @Test
    fun returnsNullWhenNoDateHeadersAreRecognized() {
        val noHeaders = listOf(RecognizedLine("밥", Rect(0.2, 0.5, 0.05, 0.02)))
        assertNull(MenuTableParser.parse(noHeaders))
    }

    @Test
    fun todayEntriesMatchOnMonthAndDay() {
        val menu = WeeklyMenu(
            title = "26년 10월 4주차 순천향대학교 식단",
            postedAt = "2026-10-16",
            sourceURL = "https://www.agoodplacefnb.com/today",
            tables = listOf(parse("hyangseol1"), parse("faculty")),
        )

        val tuesday = menu.entries(LocalDate.of(2026, 10, 20))
        assertEquals(listOf("향설1관", "교직원식당"), tuesday.map { it.cafeteria })
        assertTrue(tuesday.all { it.day.day == 20 })

        assertTrue(menu.entries(LocalDate.of(2026, 10, 27)).isEmpty())
    }

    /** 의대식당's origin block starts on a row above its "원산지" label; it must not become a menu item. */
    @Test
    fun originRowsAboveTheLabelAreNotMenuItems() {
        fun line(text: String, x: Double, y: Double, w: Double = 0.12) =
            RecognizedLine(text, Rect(x, y, w, 0.03))

        val table = MenuTableParser.parse(
            listOf(
                line("순천향의대식당 주간메뉴표", 0.3, 0.85, 0.4),
                line("10월20일(화)", 0.30, 0.72),
                line("10월21일(수)", 0.50, 0.72),
                line("중식", 0.08, 0.55, 0.04),
                line("사태김치찜", 0.30, 0.65),
                line("깍두기볶음밥", 0.50, 0.65),
                line("김치", 0.30, 0.45),
                line("김치", 0.50, 0.45),
                line("쌀:국내산/ 돈육:국내산/ 도토리묵:중국산", 0.15, 0.34, 0.7),
                line("잡곡류:국내산,미국산/ 돈까스:국내산", 0.15, 0.30, 0.7),
                line("원산지", 0.05, 0.32, 0.08),
            ),
        )!!

        val items = table.days.flatMap { it.meals.flatMap { meal -> meal.items } }
        assertFalse(items.any { it.contains("국내산") })
        assertEquals(listOf("사태김치찜", "김치"), table.days.first().meals.first().items)
    }
}
