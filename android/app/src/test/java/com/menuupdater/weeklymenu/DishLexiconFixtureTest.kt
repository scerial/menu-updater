package com.menuupdater.weeklymenu

import com.menuupdater.weeklymenu.domain.DishCorrector
import com.menuupdater.weeklymenu.domain.DishLexicon
import com.menuupdater.weeklymenu.domain.MenuTable
import com.menuupdater.weeklymenu.domain.MenuTableParser
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The committed `assets/dish-lexicon.json` applied to the real ML Kit fixtures from the Note 20. */
class DishLexiconFixtureTest {
    private val lexiconText = javaClass.classLoader!!.getResourceAsStream("dish-lexicon.json")!!
        .bufferedReader().readText()
    private val file = Json { ignoreUnknownKeys = true }.decodeFromString(DishLexicon.serializer(), lexiconText)
    private val lexicon = file.dishes
    private val corrector = DishCorrector.fromJson(lexiconText)

    private fun table(name: String): MenuTable = MenuTableParser.parse(OcrFixtures.lines(name))!!

    private fun items(table: MenuTable): List<String> =
        table.days.flatMap { it.meals }.flatMap { it.items }

    @Test
    fun fixesTheKnownMisreads() {
        val all = listOf("hyangseol1", "faculty", "medical").map { table(it) }
        val before = all.flatMap { items(it) }
        val after = all.map { corrector.correct(it) }.flatMap { items(it) }

        // Misreads the fixtures contain, and the spellings they should become.
        assertTrue(before.any { it.contains("공나물") })
        assertFalse("공나물 should be fixed", after.any { it.contains("공나물") })
        assertTrue(before.any { it.contains("삼계팅") })
        assertFalse("삼계팅 should be fixed", after.any { it.contains("삼계팅") })
        assertTrue(before.any { it.contains("걸절이") })
        assertFalse("걸절이 should be fixed", after.any { it.contains("걸절이") })
    }

    @Test
    fun leavesDishesAlreadyDominantInTheLexiconAlone() {
        for (name in listOf("hyangseol1", "faculty", "medical")) {
            val original = table(name)
            val fixed = corrector.correct(original)
            for ((a, b) in items(original).zip(items(fixed))) {
                if (a != b && a !in file.fixes) {
                    // A changed dish must be a rare spelling, not one the lexicon already knows well.
                    val own = lexicon[a] ?: 0
                    val target = lexicon[b] ?: 0
                    assertTrue("$a -> $b ($own vs $target)", target >= DishCorrector.RATIO * own)
                }
            }
        }
    }

    @Test
    fun correctionIsStableOnFixtures() {
        for (name in listOf("hyangseol1", "faculty", "medical")) {
            val once = corrector.correct(table(name))
            assertEquals(once, corrector.correct(once))
        }
    }
}
