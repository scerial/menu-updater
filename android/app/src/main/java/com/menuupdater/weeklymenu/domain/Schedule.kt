package com.menuupdater.weeklymenu.domain

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.math.min

data class VenueStatus(val level: Level, val label: String, val hours: String?) {
    enum class Level { OPEN, CLOSING_SOON, CLOSED }
}

/**
 * One restaurant from the 학내식당 운영 안내. Times are minutes after midnight; day ranges are
 * `[open, close]` pairs. A port of the iOS `Venue`, but loaded from `schedule.json` instead of
 * being hard-coded, so replacing the semester's hours is a data edit.
 */
@Serializable
data class Venue(
    val name: String,
    /** Monday to Friday; null when closed on weekdays. */
    val weekday: List<Int>? = null,
    /** Fridays close earlier at some venues. */
    val fridayEnd: Int? = null,
    val saturday: List<Int>? = null,
    val sunday: List<Int>? = null,
    /** Last serving time when earlier than the posted closing time (향설 lunch: 13:30). */
    val serviceEnd: Int? = null,
    /** False for venues the notice lists as 미운영 (not operating). */
    val isOperating: Boolean = true,
) {
    private data class Span(val start: Int, val end: Int)

    /** Today's opening hours, or null on a closed day. */
    private fun hours(day: DayOfWeek): Span? = when (day) {
        DayOfWeek.SUNDAY -> sunday?.span()
        DayOfWeek.SATURDAY -> saturday?.span()
        DayOfWeek.FRIDAY -> weekday?.let { Span(it[0], min(fridayEnd ?: it[1], it[1])) }
        else -> weekday?.span()
    }

    fun status(at: LocalDateTime): VenueStatus {
        if (!isOperating) return VenueStatus(VenueStatus.Level.CLOSED, "미운영", null)
        val range = hours(at.dayOfWeek)
            ?: return VenueStatus(VenueStatus.Level.CLOSED, "오늘 휴무", null)
        val minute = at.hour * 60 + at.minute
        val hoursText = "${clock(range.start)}–${clock(range.end)}"
        val end = min(range.end, serviceEnd ?: range.end)

        if (minute < range.start) {
            return VenueStatus(VenueStatus.Level.CLOSED, "${clock(range.start)} 오픈", hoursText)
        }
        if (minute >= end) {
            return VenueStatus(VenueStatus.Level.CLOSED, "운영 종료", hoursText)
        }
        val remaining = end - minute
        if (remaining <= CLOSING_SOON_MINUTES) {
            return VenueStatus(VenueStatus.Level.CLOSING_SOON, "${remaining}분 후 마감", hoursText)
        }
        return VenueStatus(VenueStatus.Level.OPEN, "운영 중", hoursText)
    }

    private fun List<Int>.span() = Span(this[0], this[1])

    companion object {
        const val CLOSING_SOON_MINUTES = 30
        fun clock(minutes: Int): String = "%02d:%02d".format(minutes / 60, minutes % 60)
    }
}

@Serializable
data class VenueGroup(val building: String, val venues: List<Venue>)

/**
 * The campus dining schedule, loaded from `assets/schedule.json`. Holds the notice's validity
 * window and the restaurants grouped by building. Replaces the hard-coded iOS `VenueDirectory`.
 */
@Serializable
data class Schedule(
    val semesterLabel: String,
    /** First and last day the notice covers, as ISO dates. */
    val validFrom: String,
    val validUntil: String,
    /** Footer shown under the hours list. */
    val footer: String,
    val groups: List<VenueGroup>,
) {
    private val from: LocalDate get() = LocalDate.parse(validFrom)
    private val until: LocalDate get() = LocalDate.parse(validUntil)

    fun isWithinNotice(date: LocalDate): Boolean = !date.isBefore(from) && !date.isAfter(until)

    /**
     * [groups] in the user's saved building order (comma-separated names). Buildings missing from
     * the saved order keep their default position at the end; unknown names are ignored.
     */
    fun groups(orderedBy: String): List<VenueGroup> {
        val names = orderedBy.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        val ranked = names.mapNotNull { name -> groups.firstOrNull { it.building == name } }
        return ranked + groups.filter { group -> names.none { it == group.building } }
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun fromJson(text: String): Schedule = json.decodeFromString(serializer(), text)
    }
}
