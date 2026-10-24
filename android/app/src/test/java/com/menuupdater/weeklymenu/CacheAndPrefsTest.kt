package com.menuupdater.weeklymenu

import com.menuupdater.weeklymenu.domain.MenuCacheCodec
import com.menuupdater.weeklymenu.domain.MenuDay
import com.menuupdater.weeklymenu.domain.MealSection
import com.menuupdater.weeklymenu.domain.MenuTable
import com.menuupdater.weeklymenu.domain.RefreshPolicy
import com.menuupdater.weeklymenu.domain.TextSize
import com.menuupdater.weeklymenu.domain.WeeklyMenu
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZoneOffset

class CacheAndPrefsTest {
    private val sampleMenu = WeeklyMenu(
        title = "26년 10월 4주차 순천향대학교 식단",
        postedAt = "2026-10-16",
        sourceURL = "https://www.agoodplacefnb.com/today",
        tables = listOf(MenuTable("향설1관", listOf(MenuDay(10, 20, "화", listOf(MealSection("중식", listOf("김치"))))))),
    )

    // --- MenuCacheCodec ---

    @Test
    fun cacheRoundTripsMenuAndTimestamp() {
        val encoded = MenuCacheCodec.encode(sampleMenu, fetchedAtEpochMs = 1_700_000_000_000L)
        val decoded = MenuCacheCodec.decode(encoded)!!
        assertEquals(sampleMenu, decoded.menu)
        assertEquals(1_700_000_000_000L, decoded.fetchedAtEpochMs)
        assertEquals(MenuCacheCodec.CURRENT_VERSION, decoded.version)
    }

    @Test
    fun decodesOlderBareWeeklyMenuAsStale() {
        // The iOS cache stored a bare WeeklyMenu with no wrapper and no additionalSources.
        val legacy = """
            {"title":"old","postedAt":"2026-10-09","sourceURL":"https://x/y",
             "tables":[{"cafeteria":"향설1관","days":[]}]}
        """.trimIndent()
        val decoded = MenuCacheCodec.decode(legacy)!!
        assertEquals("old", decoded.menu.title)
        assertNull(decoded.menu.additionalSources)
        assertEquals(0L, decoded.fetchedAtEpochMs) // forces a refresh
    }

    @Test
    fun decodeReturnsNullForGarbage() {
        assertNull(MenuCacheCodec.decode("not json"))
    }

    // --- TextSize ---

    @Test
    fun textSizeMapsRawValuesAndFallsBackToMedium() {
        assertEquals(TextSize.SMALL, TextSize.fromRaw(-1))
        assertEquals(TextSize.MEDIUM, TextSize.fromRaw(0))
        assertEquals(TextSize.LARGE, TextSize.fromRaw(1))
        assertEquals(TextSize.MEDIUM, TextSize.fromRaw(null))
        assertEquals(TextSize.MEDIUM, TextSize.fromRaw(99))
    }

    // --- RefreshPolicy ---

    private val utc: ZoneId = ZoneOffset.UTC
    private fun ms(day: Int, hour: Int, minute: Int): Long =
        java.time.LocalDateTime.of(2026, 10, day, hour, minute).toInstant(ZoneOffset.UTC).toEpochMilli()

    @Test
    fun refreshesWhenNeverFetchedOrStaleOrDateChanged() {
        assertTrue(RefreshPolicy.shouldRefresh(0L, ms(21, 12, 0), utc))
        // Fresh: 10 minutes old, same day.
        assertFalse(RefreshPolicy.shouldRefresh(ms(21, 12, 0), ms(21, 12, 10), utc))
        // Stale: over 30 minutes.
        assertTrue(RefreshPolicy.shouldRefresh(ms(21, 12, 0), ms(21, 12, 40), utc))
        // Date changed within 30 minutes (crossed midnight).
        assertTrue(RefreshPolicy.shouldRefresh(ms(21, 23, 50), ms(22, 0, 5), utc))
    }
}
