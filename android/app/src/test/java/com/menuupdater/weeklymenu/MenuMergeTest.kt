package com.menuupdater.weeklymenu

import com.menuupdater.weeklymenu.domain.MenuMerge
import com.menuupdater.weeklymenu.domain.MenuTable
import com.menuupdater.weeklymenu.domain.WeeklyMenu
import org.junit.Assert.assertSame
import org.junit.Test

class MenuMergeTest {
    private fun menu(title: String, tables: Int) = WeeklyMenu(
        title = title,
        postedAt = "2026-10-16",
        sourceURL = "https://example.com",
        tables = List(tables) { MenuTable("식당$it", emptyList()) },
    )

    @Test
    fun aPartialRefreshOfTheSamePostKeepsTheFullerMenu() {
        val current = menu("10월 4주차", 3)
        assertSame(current, MenuMerge.preferFuller(current, menu("10월 4주차", 1)))
    }

    @Test
    fun anEqualOrFullerRefreshReplacesTheCurrentMenu() {
        val current = menu("10월 4주차", 2)
        val same = menu("10월 4주차", 2)
        val fuller = menu("10월 4주차", 3)
        assertSame(same, MenuMerge.preferFuller(current, same))
        assertSame(fuller, MenuMerge.preferFuller(current, fuller))
    }

    @Test
    fun aNewWeekIsAcceptedEvenWithFewerTables() {
        val current = menu("10월 4주차", 3)
        val next = menu("10월 5주차", 2)
        assertSame(next, MenuMerge.preferFuller(current, next))
    }

    @Test
    fun withNothingCachedTheFreshMenuIsUsed() {
        val fresh = menu("10월 4주차", 1)
        assertSame(fresh, MenuMerge.preferFuller(null, fresh))
    }
}
