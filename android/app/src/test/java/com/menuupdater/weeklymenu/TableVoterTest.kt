package com.menuupdater.weeklymenu

import com.menuupdater.weeklymenu.domain.MealSection
import com.menuupdater.weeklymenu.domain.MenuDay
import com.menuupdater.weeklymenu.domain.MenuTable
import com.menuupdater.weeklymenu.domain.TableVoter
import org.junit.Assert.assertEquals
import org.junit.Test

class TableVoterTest {
    private fun table(vararg items: String, cafeteria: String = "향설1관", day: Int = 20) =
        MenuTable(cafeteria, listOf(MenuDay(10, day, "화", listOf(MealSection("중식", items.toList())))))

    private fun items(t: MenuTable) = t.days.single().meals.single().items

    @Test
    fun theMajoritySpellingWinsOverTheReference() {
        val voted = TableVoter.vote(
            listOf(table("공나물국", "김치"), table("콩나물국", "김치"), table("콩나물국", "김치")),
        )
        assertEquals(listOf("콩나물국", "김치"), items(voted))
    }

    @Test
    fun aMisreadInOneOtherReadingIsOutvoted() {
        val voted = TableVoter.vote(
            listOf(table("콩나물국"), table("공나물국"), table("콩나물국")),
        )
        assertEquals(listOf("콩나물국"), items(voted))
    }

    @Test
    fun aTieKeepsTheReference() {
        val voted = TableVoter.vote(listOf(table("콩나물국"), table("공나물국")))
        assertEquals(listOf("콩나물국"), items(voted))
    }

    @Test
    fun aTieGoesToTheKnownDishWhenTheReferenceIsUnknown() {
        val known = setOf("콩나물국")
        val voted = TableVoter.vote(listOf(table("공나물국"), table("콩나물국")), isKnown = { it in known })
        assertEquals(listOf("콩나물국"), items(voted))
    }

    @Test
    fun theReferenceDecidesWhichDishesExist() {
        // A reading that drops, adds or reorders dishes doesn't change the layout.
        val voted = TableVoter.vote(
            listOf(table("콩나물국", "김치", "단무지"), table("김치", "마라탕"), table("콩나물국")),
        )
        assertEquals(listOf("콩나물국", "김치", "단무지"), items(voted))
    }

    @Test
    fun farApartDishesAreNotMerged() {
        val voted = TableVoter.vote(listOf(table("콩나물국"), table("미역국"), table("미역국")))
        assertEquals(listOf("콩나물국"), items(voted))
    }

    @Test
    fun aReadingOfAnotherDayDoesNotVote() {
        val voted = TableVoter.vote(
            listOf(table("공나물국", day = 20), table("콩나물국", day = 21), table("콩나물국", day = 21)),
        )
        assertEquals(listOf("공나물국"), items(voted))
    }

    @Test
    fun aMissingCafeteriaNameIsFilledFromAnotherReading() {
        val voted = TableVoter.vote(listOf(table("김치", cafeteria = ""), table("김치", cafeteria = "교직원식당")))
        assertEquals("교직원식당", voted.cafeteria)
    }

    @Test
    fun oneReadingIsReturnedUnchanged() {
        val only = table("공나물국")
        assertEquals(only, TableVoter.vote(listOf(only)))
    }
}
