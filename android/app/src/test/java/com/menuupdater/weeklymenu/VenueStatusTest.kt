package com.menuupdater.weeklymenu

import com.menuupdater.weeklymenu.domain.MealSchedule
import com.menuupdater.weeklymenu.domain.Schedule
import com.menuupdater.weeklymenu.domain.Venue
import com.menuupdater.weeklymenu.domain.VenueStatus
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime

/** Ported from the iOS `VenueStatusTests`, driven by the committed `assets/schedule.json`. */
class VenueStatusTest {
    private val schedule = Schedule.fromJson(OcrFixtures.scheduleJson())

    /** 2026-10-21 is a Wednesday; 10-23 Friday; 10-24 Saturday; 10-25 Sunday. */
    private fun at(day: Int, hour: Int, minute: Int = 0) = LocalDateTime.of(2026, 10, day, hour, minute)

    private fun venue(name: String): Venue =
        schedule.groups.flatMap { it.venues }.first { it.name == name }

    @Test
    fun openClosingSoonAndClosedOnAWeekday() {
        val venue = venue("크앙분식")
        assertEquals(VenueStatus.Level.OPEN, venue.status(at(21, 12)).level)
        val soon = venue.status(at(21, 19, 45))
        assertEquals(VenueStatus.Level.CLOSING_SOON, soon.level)
        assertEquals("15분 후 마감", soon.label)
        assertEquals("09:00 오픈", venue.status(at(21, 8, 30)).label)
        assertEquals("운영 종료", venue.status(at(21, 20, 0)).label)
    }

    @Test
    fun statusCarriesWhatTheEnglishLabelNeeds() {
        val venue = venue("크앙분식")
        val soon = venue.status(at(21, 19, 45))
        assertEquals(VenueStatus.Kind.CLOSING_SOON, soon.kind)
        assertEquals(15, soon.minutesLeft)
        val before = venue.status(at(21, 8, 30))
        assertEquals(VenueStatus.Kind.OPENS_AT, before.kind)
        assertEquals("09:00", before.opensAt)
        assertEquals(VenueStatus.Kind.DAY_OFF, venue.status(at(25, 12)).kind)
        assertEquals(VenueStatus.Kind.NOT_OPERATING, venue("향설생활관3").status(at(21, 12)).kind)
    }

    @Test
    fun closingSoonStartsThirtyMinutesBeforeClose() {
        val venue = venue("크앙분식")
        assertEquals(VenueStatus.Level.OPEN, venue.status(at(21, 19, 29)).level)
        assertEquals(VenueStatus.Level.CLOSING_SOON, venue.status(at(21, 19, 30)).level)
    }

    @Test
    fun fridayClosesEarlier() {
        val venue = venue("크앙분식")
        assertEquals(VenueStatus.Level.CLOSING_SOON, venue.status(at(23, 14, 40)).level)
        assertEquals(VenueStatus.Level.CLOSED, venue.status(at(23, 15, 10)).level)
    }

    @Test
    fun weekendHours() {
        assertEquals("오늘 휴무", venue("크앙분식").status(at(24, 12)).label)
        assertEquals(VenueStatus.Level.OPEN, venue("얼큰한 뼈해장국").status(at(24, 12)).level)
        assertEquals(VenueStatus.Level.OPEN, venue("또바기육개장").status(at(25, 12)).level)
        assertEquals(VenueStatus.Level.CLOSED, venue("또바기육개장").status(at(25, 18, 45)).level)
    }

    @Test
    fun nonOperatingVenue() {
        val status = venue("향설생활관3").status(at(21, 12))
        assertEquals(VenueStatus.Level.CLOSED, status.level)
        assertEquals("미운영", status.label)
    }

    @Test
    fun hyangseolLunchStopsServingAtOneThirty() {
        val lunch = schedule.groups.first { it.building == "향설생활관1" }.venues.first { it.name == "중식" }
        assertEquals(VenueStatus.Level.CLOSING_SOON, lunch.status(at(21, 13, 0)).level)
        assertEquals(VenueStatus.Level.CLOSED, lunch.status(at(21, 13, 35)).level)
        assertEquals("11:00–14:00", lunch.status(at(21, 13, 35)).hours)
    }

    @Test
    fun hyangseolMealCutoffsAndWeekends() {
        assertEquals(MealSchedule.Status.OPEN, MealSchedule.status("중식", at(21, 13, 15), "향설 1관"))
        assertEquals(MealSchedule.Status.CLOSED, MealSchedule.status("중식", at(21, 13, 45), "향설 1관"))
        assertEquals(MealSchedule.Status.OPEN, MealSchedule.status("중식", at(21, 13, 45), "교직원식당"))
        assertEquals(MealSchedule.Status.CLOSED, MealSchedule.status("석식", at(23, 18, 15), "향설 1관"))
        assertEquals(MealSchedule.Status.OPEN, MealSchedule.status("석식", at(21, 18, 15), "향설 1관"))
        assertEquals(MealSchedule.Status.CLOSED, MealSchedule.status("중식", at(24, 12), "향설 1관"))
    }

    @Test
    fun savedBuildingOrderIsApplied() {
        val defaults = schedule.groups.map { it.building }
        assertEquals(defaults, schedule.groups(orderedBy = "").map { it.building })

        val order = schedule.groups(orderedBy = "학생회관,향설생활관1").map { it.building }
        assertEquals(listOf("학생회관", "향설생활관1"), order.take(2))
        assertEquals(defaults.toSet(), order.toSet())
        assertEquals(defaults.size, order.size)

        // Names that no longer exist are ignored.
        assertEquals(defaults, schedule.groups(orderedBy = "없는건물").map { it.building })
    }
}
