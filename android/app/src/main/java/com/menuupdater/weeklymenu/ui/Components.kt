package com.menuupdater.weeklymenu.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Text
import com.menuupdater.weeklymenu.R
import com.menuupdater.weeklymenu.domain.MealSchedule
import com.menuupdater.weeklymenu.domain.MealSection
import com.menuupdater.weeklymenu.domain.MenuTable
import com.menuupdater.weeklymenu.domain.VenueStatus

// MARK: - Text

@Composable
fun AppText(
    text: String,
    size: Int,
    weight: FontWeight = Regular,
    color: Color = LocalMenuColors.current.text,
    menu: Boolean = false,
    maxLines: Int = Int.MAX_VALUE,
    textAlign: TextAlign? = null,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        modifier = modifier,
        color = color,
        fontSize = scaledSp(size, menu),
        fontWeight = weight,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        textAlign = textAlign,
    )
}

// MARK: - Tabs

enum class AppTab(val labelRes: Int, val iconRes: Int) {
    TODAY(R.string.tab_today, R.drawable.ic_tray),
    WEEK(R.string.tab_week, R.drawable.ic_calendar),
    HOURS(R.string.tab_hours, R.drawable.ic_clock),
    SETTINGS(R.string.tab_settings, R.drawable.ic_settings),
}

@Composable
fun TabIcon(tab: AppTab, size: Int, tint: Color) {
    Icon(
        painter = painterResource(tab.iconRes),
        contentDescription = null,
        tint = tint,
        modifier = Modifier.size(size.dp),
    )
}

// MARK: - Status

@Composable
fun StatusPill(status: MealSchedule.Status) {
    val colors = LocalMenuColors.current
    val isOpen = status == MealSchedule.Status.OPEN
    val isClosed = status == MealSchedule.Status.CLOSED
    AppText(
        text = mealStatusText(status),
        size = 14,
        weight = Bold,
        color = if (isOpen) Color.White else if (isClosed) colors.secondaryText else colors.text,
        modifier = Modifier
            .background(if (isOpen) colors.accent else colors.track, CircleShape)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

/** Dot colour for a venue's live status in the 운영 시간 list. */
fun venueLevelColor(level: VenueStatus.Level, dark: Boolean): Color = when (level) {
    VenueStatus.Level.OPEN -> if (dark) Color(0xFF4CC46B) else Color(0xFF2FA84F)
    VenueStatus.Level.CLOSING_SOON -> if (dark) Color(0xFFF2C230) else Color(0xFFE0A100)
    VenueStatus.Level.CLOSED -> if (dark) Color(0xFF555B66) else Color(0xFFB8BDC7)
}

// MARK: - Chips (side dishes)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SideDishChips(items: List<String>, modifier: Modifier = Modifier) {
    val colors = LocalMenuColors.current
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        for (item in items) {
            AppText(
                text = item,
                size = 17,
                menu = true,
                modifier = Modifier
                    .background(colors.chip, RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
    }
}

/** Underlined text that opens [url] in the browser. */
@Composable
fun LinkText(label: String, url: String, size: Int = 13) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val colors = LocalMenuColors.current
    Text(
        text = label,
        color = colors.text,
        fontSize = scaledSp(size),
        textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline,
        modifier = Modifier.clickable {
            runCatching {
                context.startActivity(
                    android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url)),
                )
            }
        },
    )
}

// MARK: - Source footer / stale banner / states

@Composable
fun SourceFooter(sources: List<com.menuupdater.weeklymenu.domain.MenuSource>, modifier: Modifier = Modifier) {
    val colors = LocalMenuColors.current
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        for (source in sources) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                AppText(source.title, 13, color = colors.secondaryText)
                AppText(stringResource(R.string.posted_on, source.postedAt), 13, color = colors.secondaryText)
                LinkText(stringResource(R.string.open_source_post), source.url)
            }
        }
    }
}

@Composable
fun StaleMenuBanner(message: String, modifier: Modifier = Modifier) {
    val colors = LocalMenuColors.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0x26FF9800), RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        AppText(stringResource(R.string.stale_banner), 15, Bold)
        AppText(message, 13, color = colors.secondaryText)
    }
}

@Composable
fun EmptyMealCard(title: String, detail: String, modifier: Modifier = Modifier) {
    val colors = LocalMenuColors.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.surface, RoundedCornerShape(16.dp))
            .padding(horizontal = 24.dp, vertical = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        AppText(title, 20, Bold, textAlign = TextAlign.Center)
        AppText(detail, 16, color = colors.secondaryText, textAlign = TextAlign.Center)
    }
}

// MARK: - Model helpers

/** The meals this cafeteria serves, in table order. */
val MenuTable.mealNames: List<String>
    get() {
        val seen = LinkedHashSet<String>()
        days.forEach { day -> day.meals.forEach { seen.add(it.name) } }
        return seen.toList()
    }

/** "중식 · 11:00–14:00" ("Lunch · 11:00–14:00"), or just the meal name when hours aren't known. */
fun MealSection.timeLabel(tr: Tr): String =
    MealSchedule.hoursLabel(name)?.let { "${tr.meal(name)} · $it" } ?: tr.meal(name)
