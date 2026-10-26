package com.menuupdater.weeklymenu.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import com.menuupdater.weeklymenu.R
import com.menuupdater.weeklymenu.data.MenuError
import com.menuupdater.weeklymenu.domain.MealSchedule
import com.menuupdater.weeklymenu.domain.MenuDay
import com.menuupdater.weeklymenu.domain.TextSize
import com.menuupdater.weeklymenu.domain.Translator
import com.menuupdater.weeklymenu.domain.VenueStatus
import java.time.LocalDate
import java.time.Month
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/**
 * Display-time translation. Korean stays the data everywhere (meal names, cafeterias, prefs); a
 * [Tr] only changes what is drawn. [Tr.Korean] draws everything as stored.
 */
class Tr(private val translator: Translator?) {
    val isEnglish: Boolean get() = translator != null

    fun dish(text: String): String = translator?.dish(text) ?: text
    fun meal(name: String): String = translator?.meal(name) ?: name
    fun place(name: String): String = translator?.place(name) ?: name
    fun weekday(name: String): String = translator?.weekday(name) ?: name

    /** A venue in the hours list: a place name, or a meal name (향설생활관1's 조식/중식/석식). */
    fun venue(name: String): String {
        val t = translator ?: return name
        return t.place(name).takeIf { it != name } ?: t.meal(name)
    }

    companion object {
        val Korean = Tr(null)
    }
}

/** The screen-wide translation; a menu card can override it with [Tr.Korean] when flipped. */
val LocalTr = staticCompositionLocalOf { Tr.Korean }

/**
 * A menu card's language. In English, tapping the card flips it to Korean and back (remembered per
 * [key] while the screen lives); in Korean the card isn't tappable. Returns the card's [Tr] and the
 * modifier that makes it tappable, to apply after `card()`.
 */
@Composable
fun rememberCardLanguage(key: String, radius: Dp): Pair<Tr, Modifier> {
    val screen = LocalTr.current
    var showKorean by rememberSaveable(key) { mutableStateOf(false) }
    if (!screen.isEnglish) return screen to Modifier
    val label = stringResource(R.string.tap_to_switch_language)
    val modifier = Modifier
        .clip(RoundedCornerShape(radius))
        .clickable(onClickLabel = label) { showKorean = !showKorean }
    return (if (showKorean) Tr.Korean else screen) to modifier
}

// MARK: - Dates

/** Menu dates in the screen's language: "10월 21일 수요일" / "Wednesday, October 21". */
object AppDate {
    private val koreanFull = DateTimeFormatter.ofPattern("M월 d일 EEEE", Locale.KOREAN)
    private val englishFull = DateTimeFormatter.ofPattern("EEEE, MMMM d", Locale.ENGLISH)

    fun full(date: LocalDate, english: Boolean): String = date.format(if (english) englishFull else koreanFull)

    /** The span a post covers: "10월 19일 – 10월 23일" / "Oct 19 – 23". */
    fun range(days: List<MenuDay>, english: Boolean): String? {
        val sorted = days.sortedBy { it.month * 100 + it.day }
        val first = sorted.firstOrNull() ?: return null
        val last = sorted.last()
        return span(first.month, first.day, last.month, last.day, english)
    }

    /** "8월 31일 – 12월 11일" / "Aug 31 – Dec 11". */
    fun range(from: LocalDate, until: LocalDate, english: Boolean): String =
        span(from.monthValue, from.dayOfMonth, until.monthValue, until.dayOfMonth, english)

    private fun span(m1: Int, d1: Int, m2: Int, d2: Int, english: Boolean): String {
        val same = m1 == m2 && d1 == d2
        if (!english) return if (same) "${m1}월 ${d1}일" else "${m1}월 ${d1}일 – ${m2}월 ${d2}일"
        val start = "${month(m1)} $d1"
        return when {
            same -> start
            m1 == m2 -> "$start – $d2"
            else -> "$start – ${month(m2)} $d2"
        }
    }

    private fun month(m: Int): String = Month.of(m).getDisplayName(TextStyle.SHORT, Locale.ENGLISH)
}

// MARK: - Labels for domain values

@Composable
fun mealStatusText(status: MealSchedule.Status): String = stringResource(
    when (status) {
        MealSchedule.Status.UPCOMING -> R.string.status_upcoming
        MealSchedule.Status.OPEN -> R.string.status_open
        MealSchedule.Status.CLOSED -> R.string.status_closed
    },
)

@Composable
fun venueStatusText(status: VenueStatus): String = when (status.kind) {
    VenueStatus.Kind.NOT_OPERATING -> stringResource(R.string.venue_not_operating)
    VenueStatus.Kind.DAY_OFF -> stringResource(R.string.venue_day_off)
    VenueStatus.Kind.OPENS_AT -> stringResource(R.string.venue_opens_at, status.opensAt.orEmpty())
    VenueStatus.Kind.ENDED -> stringResource(R.string.venue_ended)
    VenueStatus.Kind.CLOSING_SOON -> stringResource(R.string.venue_closing_soon, status.minutesLeft ?: 0)
    VenueStatus.Kind.OPEN -> stringResource(R.string.venue_open)
}

@Composable
fun menuErrorText(error: MenuError): String = stringResource(
    when (error) {
        MenuError.NO_ELIGIBLE_POST -> R.string.error_no_post
        MenuError.NO_MENU_IMAGES -> R.string.error_no_images
        MenuError.NO_RECOGNIZED_TEXT -> R.string.error_no_text
        MenuError.LOAD_FAILED -> R.string.error_generic
    },
)

@Composable
fun textSizeLabel(size: TextSize): String = stringResource(
    when (size) {
        TextSize.SMALL -> R.string.size_small
        TextSize.MEDIUM -> R.string.size_medium
        TextSize.LARGE -> R.string.size_large
    },
)
