package com.menuupdater.weeklymenu.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.menuupdater.weeklymenu.domain.MealSchedule
import com.menuupdater.weeklymenu.domain.MealSection
import com.menuupdater.weeklymenu.domain.MenuDay
import com.menuupdater.weeklymenu.domain.MenuTable
import com.menuupdater.weeklymenu.domain.WeeklyMenu
import java.time.LocalDateTime

// MARK: - Shared scroll body

@Composable
private fun MenuScroll(
    viewModel: MenuViewModel,
    menu: WeeklyMenu,
    layout: ScreenLayout,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = layout.horizontalPadding)
            .padding(top = 20.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        viewModel.errorMessage?.let { StaleMenuBanner(it) }
        content()
        SourceFooter(menu.sources, Modifier.padding(top = 12.dp))
    }
}

// MARK: - Today

@Composable
fun TodayPage(viewModel: MenuViewModel, menu: WeeklyMenu, cafeteria: String, layout: ScreenLayout) {
    val now = viewModel.now
    val entries = menu.entries(now.toLocalDate())

    MenuScroll(viewModel, menu, layout) {
        if (entries.isEmpty()) {
            EmptyMealCard("오늘 메뉴가 없어요", "이 게시물에는 오늘 날짜가 없어요. 이번 주 탭을 확인해 주세요.")
        } else if (layout.showsColumns) {
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                for (table in menu.tables) {
                    Box(Modifier.weight(1f)) {
                        TodayColumn(table, entries.firstOrNull { it.cafeteria == table.cafeteria }?.day, now, isWide = true)
                    }
                }
            }
        } else {
            val table = menu.tables.firstOrNull { it.cafeteria == cafeteria } ?: menu.tables.firstOrNull()
            if (table != null) {
                TodayColumn(table, entries.firstOrNull { it.cafeteria == table.cafeteria }?.day, now, isWide = false)
            }
        }
    }
}

@Composable
private fun TodayColumn(table: MenuTable, day: MenuDay?, now: LocalDateTime, isWide: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        CafeteriaHeading(table, isWide)
        if (day != null) {
            val focus = MealSchedule.focusMeal(day.meals.map { it.name }, now, table.cafeteria)
            for (meal in day.meals) {
                MealCard(
                    meal = meal,
                    status = MealSchedule.status(meal.name, now, table.cafeteria),
                    isFocused = meal.name == focus,
                    isWide = isWide,
                )
            }
        } else {
            EmptyMealCard("오늘 메뉴가 없어요", "${table.cafeteria} 메뉴에 오늘 날짜가 없어요.")
        }
    }
}

@Composable
private fun CafeteriaHeading(table: MenuTable, isWide: Boolean) {
    val colors = LocalMenuColors.current
    Column(
        Modifier.padding(horizontal = 4.dp).padding(bottom = 4.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        if (table.cafeteria.isNotEmpty()) {
            AppText(table.cafeteria, if (isWide) 26 else 24, Bold)
        }
        if (table.mealNames.isNotEmpty()) {
            AppText(table.mealNames.joinToString(" · "), if (isWide) 16 else 15, color = colors.secondaryText)
        }
    }
}

@Composable
private fun MealCard(meal: MealSection, status: MealSchedule.Status?, isFocused: Boolean, isWide: Boolean) {
    val colors = LocalMenuColors.current
    Column(
        Modifier
            .fillMaxWidth()
            .card(highlighted = isFocused, radius = if (isWide) 18.dp else 16.dp)
            .padding(if (isWide) 22.dp else 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppText(
                meal.timeLabel, 16, Bold,
                color = if (isFocused) colors.accent else colors.secondaryText,
                modifier = Modifier.weight(1f),
            )
            status?.let { StatusPill(it) }
        }
        meal.items.firstOrNull()?.let { AppText(it, 24, Bold, menu = true) }
        if (meal.items.size > 1) {
            SideDishChips(meal.items.drop(1))
        }
    }
}

// MARK: - Week

@Composable
fun WeekPage(viewModel: MenuViewModel, menu: WeeklyMenu, cafeteria: String, layout: ScreenLayout) {
    val now = viewModel.now
    val tables = if (layout.showsColumns) {
        menu.tables
    } else {
        menu.tables.firstOrNull { it.cafeteria == cafeteria }?.let { listOf(it) } ?: menu.tables.take(1)
    }
    val periods = weekPeriods(tables)
    var period by remember { mutableStateOf<String?>(null) }
    val active = period?.takeIf { periods.contains(it) } ?: defaultPeriod(periods, now)

    Column(Modifier.fillMaxSize()) {
        if (periods.size > 1) {
            PeriodBar(periods, active) { period = it }
        }
        MenuScroll(viewModel, menu, layout) {
            if (layout.showsColumns) {
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    for (table in tables) {
                        Box(Modifier.weight(1f)) { CafeteriaHeading(table, isWide = true) }
                    }
                }
                WeekGrid(tables, active, now)
            } else {
                for (table in tables) {
                    WeekRows(table, active, now, isWide = false)
                }
            }
        }
    }
}

@Composable
private fun PeriodBar(periods: List<String>, selection: String, onSelect: (String) -> Unit) {
    val colors = LocalMenuColors.current
    Column {
        Row(Modifier.fillMaxWidth().background(colors.surface).padding(horizontal = 12.dp)) {
            for (name in periods) {
                val selected = name == selection
                Box(
                    Modifier.weight(1f).heightIn(min = 52.dp).clickable { onSelect(name) },
                ) {
                    Column(
                        Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        AppText(name, 17, Bold, color = if (selected) colors.text else colors.secondaryText)
                        MealSchedule.hoursLabel(name)?.let {
                            AppText(it, 13, color = if (selected) colors.text else colors.secondaryText)
                        }
                    }
                    Box(
                        Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(3.dp)
                            .background(if (selected) colors.accent else Color.Transparent),
                    )
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(colors.border))
    }
}

@Composable
private fun WeekRows(table: MenuTable, period: String, now: LocalDateTime, isWide: Boolean) {
    val rows = table.days.mapNotNull { day -> day.meals.firstOrNull { it.name == period }?.let { day to it } }
    if (rows.isEmpty()) {
        EmptyMealCard("이 시간에는 운영하지 않아요", "${table.cafeteria}은 ${table.mealNames.joinToString(" · ")}만 운영해요")
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            for ((day, meal) in rows) {
                WeekRow(day, meal, isToday(day, now), isWide)
            }
        }
    }
}

/** iPad week: one row per day across cafeteria columns, cells in a row sharing a height. */
@Composable
private fun WeekGrid(tables: List<MenuTable>, period: String, now: LocalDateTime) {
    val perTable = tables.map { table ->
        table.days.mapNotNull { day ->
            day.meals.firstOrNull { it.name == period }?.let { (day.month * 100 + day.day) to (day to it) }
        }.toMap()
    }
    val servesPeriod = perTable.map { it.isNotEmpty() }
    val dayIds = perTable.flatMap { it.keys }.distinct().sorted()

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        dayIds.forEachIndexed { rowIndex, id ->
            Row(
                Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                for (col in tables.indices) {
                    Box(Modifier.weight(1f).fillMaxHeight()) {
                        val cell = perTable[col][id]
                        when {
                            cell != null -> WeekRow(cell.first, cell.second, isToday(cell.first, now), isWide = true, Modifier.fillMaxHeight())
                            !servesPeriod[col] && rowIndex == 0 ->
                                EmptyMealCard("이 시간에는 운영하지 않아요", "${tables[col].cafeteria}은 ${tables[col].mealNames.joinToString(" · ")}만 운영해요")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WeekRow(day: MenuDay, meal: MealSection, isToday: Boolean, isWide: Boolean, modifier: Modifier = Modifier) {
    val colors = LocalMenuColors.current
    Row(
        modifier
            .fillMaxWidth()
            .card(highlighted = isToday)
            .padding(horizontal = if (isWide) 18.dp else 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(if (isWide) 16.dp else 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            Modifier.width(if (isWide) 52.dp else 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val dayColor = if (isToday) colors.accent else colors.text
            AppText(day.weekday, if (isWide) 16 else 15, Bold, color = dayColor)
            AppText("${day.day}", if (isWide) 24 else 22, Bold, color = dayColor)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AppText(meal.items.firstOrNull().orEmpty(), if (isWide) 21 else 19, Bold, menu = true, modifier = Modifier.weight(1f, fill = false))
                if (isToday) {
                    AppText(
                        "오늘", if (isWide) 14 else 13, Bold, color = Color.White,
                        modifier = Modifier
                            .background(colors.accent, RoundedCornerShape(50))
                            .padding(horizontal = if (isWide) 9.dp else 8.dp, vertical = 2.dp),
                    )
                }
            }
            if (meal.items.size > 1) {
                AppText(meal.items.drop(1).joinToString(" · "), 15, menu = true, color = colors.secondaryText)
            }
        }
    }
}

// MARK: - Week helpers

private fun weekPeriods(tables: List<MenuTable>): List<String> =
    tables.flatMap { it.mealNames }.distinct()

private fun defaultPeriod(periods: List<String>, now: LocalDateTime): String =
    MealSchedule.focusMeal(periods, now)
        ?: if (periods.contains("중식")) "중식" else periods.firstOrNull().orEmpty()

private fun isToday(day: MenuDay, now: LocalDateTime): Boolean =
    day.month == now.monthValue && day.day == now.dayOfMonth
