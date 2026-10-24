package com.menuupdater.weeklymenu.domain

import java.time.DayOfWeek
import java.time.LocalDateTime

/**
 * Serving windows for the 조식/중식/석식 columns on the menu, used to highlight the meal being served
 * now. A port of the iOS `MealSchedule`. Closed on weekends; 향설 lunch stops at 13:30 and Friday
 * dinner at 18:00, earlier than the posted hours.
 */
object MealSchedule {
    enum class Status(val label: String) {
        UPCOMING("운영 예정"),
        OPEN("운영 중"),
        CLOSED("운영 종료"),
    }

    private val hours: Map<String, IntRange> = mapOf(
        "조식" to (8 * 60 until 10 * 60),
        "중식" to (11 * 60 until 14 * 60),
        "석식" to (17 * 60 until 18 * 60 + 30),
    )

    /** "08:00–10:00", or null for a meal without known hours. */
    fun hoursLabel(meal: String): String? =
        hours[meal]?.let { "${clock(it.first)}–${clock(it.last + 1)}" }

    fun status(meal: String, at: LocalDateTime, cafeteria: String = ""): Status? {
        val range = hours[meal] ?: return null
        if (at.dayOfWeek == DayOfWeek.SATURDAY || at.dayOfWeek == DayOfWeek.SUNDAY) return Status.CLOSED

        var end = range.last + 1
        if (cafeteria.contains("향설")) {
            if (meal == "중식") end = 13 * 60 + 30
            if (meal == "석식" && at.dayOfWeek == DayOfWeek.FRIDAY) end = 18 * 60
        }
        val minute = at.hour * 60 + at.minute
        if (minute < range.first) return Status.UPCOMING
        if (minute < end) return Status.OPEN
        return Status.CLOSED
    }

    /** The meal to highlight: the one being served now, otherwise the next one today. */
    fun focusMeal(meals: List<String>, at: LocalDateTime, cafeteria: String = ""): String? =
        meals.firstOrNull { status(it, at, cafeteria) == Status.OPEN }
            ?: meals.firstOrNull { status(it, at, cafeteria) == Status.UPCOMING }

    private fun clock(minutes: Int): String = "%02d:%02d".format(minutes / 60, minutes % 60)
}
