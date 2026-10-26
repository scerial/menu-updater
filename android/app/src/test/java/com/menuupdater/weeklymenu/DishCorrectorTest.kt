package com.menuupdater.weeklymenu

import com.menuupdater.weeklymenu.domain.DishCorrector
import com.menuupdater.weeklymenu.domain.DishSegments
import com.menuupdater.weeklymenu.domain.MealSection
import com.menuupdater.weeklymenu.domain.MenuDay
import com.menuupdater.weeklymenu.domain.MenuTable
import org.junit.Assert.assertEquals
import org.junit.Test

/** The dish corrector's rules, against a small hand-written lexicon. */
class DishCorrectorTest {
    private val lexicon = mapOf(
        "콩나물국" to 41,
        "김치" to 50,
        "감자튀김" to 10,
        "케찹" to 8,
        "된장국" to 7,
        "된장죽" to 7,
        "잡채밥" to 2,
        "국" to 30,
        "콩나물무침" to 20,
        "공나물무침" to 3, // a misread that itself recurs a little
        "열무비빔밥" to 12,
        "열무비빈밥" to 9, // recurs too often relative to the correct one (12 < 2 × 9)
    )
    private val corrector = DishCorrector(lexicon)

    @Test
    fun fixesAMisreadOneJamoAway() {
        assertEquals("콩나물국", corrector.correct("공나물국"))
    }

    @Test
    fun keepsAnExactMatch() {
        assertEquals("김치", corrector.correct("김치"))
        assertEquals("콩나물국", corrector.correct("콩나물국"))
    }

    @Test
    fun keepsAnUnknownDishWithNoNeighbour() {
        assertEquals("마라탕", corrector.correct("마라탕"))
    }

    @Test
    fun keepsATieBetweenNeighbours() {
        // 된장묵 is one jamo from both 된장국 and 된장죽, which are equally common.
        assertEquals("된장묵", corrector.correct("된장묵"))
    }

    @Test
    fun ignoresANeighbourBelowMinCount() {
        assertEquals("잡채빕", corrector.correct("잡채빕"))
    }

    @Test
    fun fixesAnInLexiconMisreadOnlyWhenTheNeighbourIsFarMoreCommon() {
        assertEquals("콩나물무침", corrector.correct("공나물무침")) // 20 >= 2 × 3
        assertEquals("열무비빈밥", corrector.correct("열무비빈밥")) // 12 < 2 × 9
    }

    @Test
    fun skipsSingleSyllableSegments() {
        assertEquals("죽", corrector.correct("죽"))
    }

    @Test
    fun correctsCompoundItemsSegmentBySegment() {
        assertEquals("콩나물국/김치", corrector.correct("공나물국/김치"))
        assertEquals("감자튀김*케찹", corrector.correct("감자튀김*케참"))
        assertEquals("콩나물국 / 김치", corrector.correct("공나물국 / 김치"))
    }

    @Test
    fun isIdempotent() {
        val items = listOf("공나물국", "공나물국/김치", "감자튀김*케참", "된장묵", "마라탕", "죽", "공나물무침")
        for (item in items) {
            val once = corrector.correct(item)
            assertEquals(item, once, corrector.correct(once))
        }
    }

    @Test
    fun followsAChainSoASecondPassChangesNothing() {
        // A → B (B is common), and B → C (C is far more common than B): one pass lands on C.
        val chain = DishCorrector(mapOf("닭강정" to 4, "닭강점" to 20, "닭갈정" to 1))
        val once = chain.correct("닭갈점")
        assertEquals(once, chain.correct(once))
    }

    @Test
    fun correctsAWholeTable() {
        val table = MenuTable(
            "향설1관",
            listOf(MenuDay(10, 20, "화", listOf(MealSection("중식", listOf("공나물국", "김치/샐러드"))))),
        )
        val fixed = corrector.correct(table)
        assertEquals(listOf("콩나물국", "김치/샐러드"), fixed.days[0].meals[0].items)
        assertEquals("향설1관", fixed.cafeteria)
    }

    @Test
    fun splitRoundTripsAndKeepsSeparators() {
        val item = "김치/샐러드/후식차"
        assertEquals(listOf("김치", "/", "샐러드", "/", "후식차"), DishSegments.split(item))
        assertEquals(item, DishSegments.split(item).joinToString(""))
        assertEquals(listOf("감자튀김", "케찹"), DishSegments.dishes("감자튀김 * 케찹"))
    }

    @Test
    fun keepsProtectedDishesAndAppliesDirectFixes() {
        val c = DishCorrector(
            mapOf("배추겉절이" to 7, "부추겉절이" to 3, "장각삼계탕" to 5, "장각삼계팅" to 4),
            keep = setOf("부추겉절이"),
            fixes = mapOf("장각삼계팅" to "장각삼계탕"),
        )
        assertEquals("부추겉절이", c.correct("부추겉절이"))
        assertEquals("장각삼계탕", c.correct("장각삼계팅"))
        assertEquals("장각삼계탕/김치", c.correct("장각삼계팅/김치"))
    }

    @Test
    fun readsTheLexiconJson() {
        val fromJson = DishCorrector.fromJson(
            """{"version":1,"builtFrom":"x","images":3,"dishes":{"콩나물국":41}}""",
        )
        assertEquals("콩나물국", fromJson.correct("공나물국"))
    }
}
