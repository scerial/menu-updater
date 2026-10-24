package com.menuupdater.weeklymenu.domain

import kotlinx.serialization.Serializable
import java.time.LocalDate

@Serializable
data class MealSection(val name: String, val items: List<String>)

@Serializable
data class MenuDay(val month: Int, val day: Int, val weekday: String, val meals: List<MealSection>) {
    val heading: String get() = "${month}월 ${day}일 (${weekday})"
}

@Serializable
data class MenuTable(val cafeteria: String, val days: List<MenuDay>)

/** A board post a menu was read from. */
@Serializable
data class MenuSource(val title: String, val postedAt: String, val url: String)

/** A cafeteria's menu for one day; the result of matching [WeeklyMenu.entries]. */
data class MenuEntry(val cafeteria: String, val day: MenuDay)

@Serializable
data class WeeklyMenu(
    val title: String,
    val postedAt: String,
    val sourceURL: String,
    val tables: List<MenuTable>,
    /** Further posts merged into this menu (the 의과대학 post). Optional so older cached menus still load. */
    val additionalSources: List<MenuSource>? = null,
) {
    /** Every post this menu came from, main post first. */
    val sources: List<MenuSource>
        get() = listOf(MenuSource(title, postedAt, sourceURL)) + (additionalSources ?: emptyList())

    /** Each cafeteria's menu for [date], matched on the month/day printed in the column headers. */
    fun entries(date: LocalDate): List<MenuEntry> = tables.mapNotNull { table ->
        table.days.firstOrNull { it.month == date.monthValue && it.day == date.dayOfMonth }
            ?.let { MenuEntry(table.cafeteria, it) }
    }
}
