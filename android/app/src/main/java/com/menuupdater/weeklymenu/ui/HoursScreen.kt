package com.menuupdater.weeklymenu.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.menuupdater.weeklymenu.domain.Venue
import com.menuupdater.weeklymenu.domain.VenueGroup
import com.menuupdater.weeklymenu.domain.VenueStatus
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.LocalDateTime

@Composable
fun HoursScreen(viewModel: MenuViewModel, layout: ScreenLayout) {
    val colors = LocalMenuColors.current
    val savedOrder by viewModel.buildingOrder.collectAsState()
    var isEditing by remember { mutableStateOf(false) }

    // Re-evaluate the live status every 30s so the dots change while the app is open.
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            now = LocalDateTime.now()
        }
    }

    val groups = viewModel.schedule.groups(orderedBy = savedOrder)

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(
            subtitle = "${viewModel.schedule.semesterLabel} · 8월 31일 – 12월 11일",
            title = "운영 시간",
            layout = layout,
            trailing = {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    AppText(
                        if (isEditing) "완료" else "순서 편집", 16, Bold, color = colors.accent,
                        modifier = Modifier.clickable { isEditing = !isEditing },
                    )
                    if (isEditing && savedOrder.isNotEmpty()) {
                        AppText(
                            "기본 순서로", 16, color = colors.secondaryText,
                            modifier = Modifier.clickable { viewModel.setBuildingOrder("") },
                        )
                    }
                }
            },
        )

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = layout.horizontalPadding)
                .padding(top = 20.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            VenuePanel(
                groups = groups,
                now = now,
                usesColumns = layout.showsColumns,
                isEditing = isEditing,
                move = { building, offset -> moveBuilding(viewModel, groups, building, offset) },
            )
            AppText(footerText(viewModel.schedule.footer, viewModel.schedule.isWithinNotice(now.toLocalDate())), 13, color = colors.secondaryText, modifier = Modifier.padding(horizontal = 4.dp))
        }
    }
}

private fun footerText(base: String, withinNotice: Boolean): String =
    if (withinNotice) base else "지금은 이 안내가 다루는 기간이 아니라서 시간이 다를 수 있어요. $base"

private fun moveBuilding(viewModel: MenuViewModel, groups: List<VenueGroup>, building: String, offset: Int) {
    val names = groups.map { it.building }.toMutableList()
    val from = names.indexOf(building)
    val to = from + offset
    if (from < 0 || to !in names.indices) return
    names[from] = names[to].also { names[to] = names[from] }
    viewModel.setBuildingOrder(names.joinToString(","))
}

@Composable
private fun VenuePanel(
    groups: List<VenueGroup>,
    now: LocalDateTime,
    usesColumns: Boolean,
    isEditing: Boolean,
    move: (String, Int) -> Unit,
) {
    val first = groups.firstOrNull()?.building
    val last = groups.lastOrNull()?.building
    if (usesColumns) {
        val split = minOf(3, groups.size)
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Box(Modifier.weight(1f)) { VenueCard(groups.take(split), now, isEditing, first, last, move) }
            Box(Modifier.weight(1f)) { VenueCard(groups.drop(split), now, isEditing, first, last, move) }
        }
    } else {
        VenueCard(groups, now, isEditing, first, last, move)
    }
}

@Composable
private fun VenueCard(
    groups: List<VenueGroup>,
    now: LocalDateTime,
    isEditing: Boolean,
    firstBuilding: String?,
    lastBuilding: String?,
    move: (String, Int) -> Unit,
) {
    val colors = LocalMenuColors.current
    Column(Modifier.fillMaxWidth().card().padding(bottom = 6.dp)) {
        groups.forEachIndexed { index, group ->
            if (index > 0) SettingsDivider()
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 12.dp, bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppText(group.building, 14, Bold, color = colors.secondaryText, modifier = Modifier.weight(1f))
                if (isEditing) {
                    MoveButton("▲", group.building, -1, disabled = group.building == firstBuilding, move)
                    MoveButton("▼", group.building, 1, disabled = group.building == lastBuilding, move)
                }
            }
            for (venue in group.venues) {
                VenueRow(venue, venue.status(now))
            }
        }
    }
}

@Composable
private fun MoveButton(symbol: String, building: String, offset: Int, disabled: Boolean, move: (String, Int) -> Unit) {
    val colors = LocalMenuColors.current
    Box(
        Modifier
            .size(width = 44.dp, height = 36.dp)
            .clickable(enabled = !disabled) { move(building, offset) },
        contentAlignment = Alignment.Center,
    ) {
        AppText(symbol, 15, Bold, color = if (disabled) colors.secondaryText.copy(alpha = 0.4f) else colors.accent)
    }
}

@Composable
private fun VenueRow(venue: Venue, status: VenueStatus) {
    val colors = LocalMenuColors.current
    val isClosed = status.level == VenueStatus.Level.CLOSED
    val dot = venueLevelColor(status.level, isSystemInDarkTheme())
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .alpha(if (isClosed) 0.7f else 1f)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(10.dp).background(dot, CircleShape))
        AppText(
            venue.name, 17, if (isClosed) Regular else Bold,
            color = if (isClosed) colors.secondaryText else colors.text,
            modifier = Modifier.weight(1f),
        )
        Column(horizontalAlignment = Alignment.End) {
            AppText(status.label, 15, Bold, color = if (isClosed) colors.secondaryText else colors.text)
            status.hours?.let { AppText(it, 13, color = colors.secondaryText) }
        }
    }
}
