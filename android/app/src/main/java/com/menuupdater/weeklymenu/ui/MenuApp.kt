package com.menuupdater.weeklymenu.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.CompositionLocalProvider
import com.menuupdater.weeklymenu.domain.TextSize
import com.menuupdater.weeklymenu.domain.WeeklyMenu

/** How much room the window has, mirroring the iOS `ScreenLayout`. */
class ScreenLayout(val width: Dp) {
    /** Wide tablet / landscape: a sidebar replaces the bottom tab bar. */
    val usesSidebar: Boolean get() = width >= 1000.dp
    /** Both cafeterias side by side instead of a picker. */
    val showsColumns: Boolean get() = width >= 700.dp
    val isWide: Boolean get() = showsColumns
    val horizontalPadding: Dp get() = if (isWide) 40.dp else 16.dp
}

/** Relative menu-text scale for the chosen size tier (base dish size 19pt). */
val TextSize.menuScale: Float
    get() = when (this) {
        TextSize.SMALL -> 16f / 19f
        TextSize.MEDIUM -> 1f
        TextSize.LARGE -> 22f / 19f
    }

@Composable
fun MenuApp(viewModel: MenuViewModel) {
    val colors = LocalMenuColors.current
    val textSize by viewModel.textSize.collectAsState()
    val defaultCafeteria by viewModel.defaultCafeteria.collectAsState()

    var tab by remember { mutableStateOf(AppTab.TODAY) }
    var selectedCafeteria by remember { mutableStateOf<String?>(null) }

    val names = viewModel.menu?.tables?.map { it.cafeteria } ?: emptyList()
    val cafeteria = when {
        selectedCafeteria != null && names.contains(selectedCafeteria) -> selectedCafeteria!!
        names.contains(defaultCafeteria) -> defaultCafeteria
        else -> names.firstOrNull() ?: ""
    }

    CompositionLocalProvider(LocalMenuTextScale provides textSize.menuScale) {
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .background(colors.background)
                .safeDrawingPadding(),
        ) {
            val layout = ScreenLayout(maxWidth)
            if (layout.usesSidebar) {
                Row(Modifier.fillMaxSize()) {
                    Sidebar(tab) { tab = it }
                    Box(Modifier.weight(1f).fillMaxHeight()) {
                        Page(viewModel, tab, layout, cafeteria) { selectedCafeteria = it }
                    }
                }
            } else {
                Column(Modifier.fillMaxSize()) {
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        Page(viewModel, tab, layout, cafeteria) { selectedCafeteria = it }
                    }
                    BottomTabBar(tab) { tab = it }
                }
            }
        }
    }
}

@Composable
private fun Page(
    viewModel: MenuViewModel,
    tab: AppTab,
    layout: ScreenLayout,
    cafeteria: String,
    onSelectCafeteria: (String) -> Unit,
) {
    when (tab) {
        AppTab.TODAY -> MenuScreen(
            viewModel, layout, title = "오늘의 메뉴",
            subtitle = KoreanDate.full(viewModel.now.toLocalDate()),
            cafeteria = cafeteria, onSelectCafeteria = onSelectCafeteria,
        ) { menu, caf -> TodayPage(viewModel, menu, caf, layout) }

        AppTab.WEEK -> MenuScreen(
            viewModel, layout, title = "이번 주 메뉴",
            subtitle = viewModel.menu?.let { KoreanDate.range(it.tables.flatMap { t -> t.days }) }.orEmpty(),
            cafeteria = cafeteria, onSelectCafeteria = onSelectCafeteria,
        ) { menu, caf -> WeekPage(viewModel, menu, caf, layout) }

        AppTab.HOURS -> HoursScreen(viewModel, layout)
        AppTab.SETTINGS -> SettingsScreen(viewModel, layout)
    }
}

// MARK: - Navigation

@Composable
private fun BottomTabBar(selection: AppTab, onSelect: (AppTab) -> Unit) {
    val colors = LocalMenuColors.current
    Column {
        Box(Modifier.fillMaxWidth().height(1.dp).background(colors.border))
        Row(
            Modifier
                .fillMaxWidth()
                .background(colors.surface)
                .padding(horizontal = 8.dp, vertical = 6.dp),
        ) {
            for (tab in AppTab.entries) {
                val selected = tab == selection
                val tint = if (selected) colors.accent else colors.secondaryText
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 52.dp)
                        .clickable { onSelect(tab) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    TabIcon(tab, 22, tint)
                    Spacer(Modifier.height(4.dp))
                    AppText(tab.label, 13, if (selected) Bold else Regular, color = tint)
                }
            }
        }
    }
}

@Composable
private fun Sidebar(selection: AppTab, onSelect: (AppTab) -> Unit) {
    val colors = LocalMenuColors.current
    Row {
        Column(
            modifier = Modifier
                .width(280.dp)
                .fillMaxHeight()
                .background(colors.surface)
                .padding(horizontal = 16.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(28.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(horizontal = 12.dp),
            ) {
                Box(
                    Modifier.size(40.dp).background(colors.accent, RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center,
                ) { TabIcon(AppTab.TODAY, 24, Color.White) }
                AppText("학식 메뉴", 22, Bold)
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                for (tab in AppTab.entries) {
                    val selected = tab == selection
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 56.dp)
                            .background(if (selected) colors.accent else Color.Transparent, RoundedCornerShape(14.dp))
                            .clickable { onSelect(tab) }
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        TabIcon(tab, 21, if (selected) Color.White else colors.tertiaryText)
                        AppText(tab.label, 19, Bold, color = if (selected) Color.White else colors.tertiaryText)
                    }
                }
            }
        }
        Box(Modifier.width(1.dp).fillMaxHeight().background(colors.border))
    }
}

// MARK: - Header

@Composable
fun ScreenHeader(
    subtitle: String,
    title: String,
    layout: ScreenLayout,
    isRefreshing: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
    accessory: (@Composable () -> Unit)? = null,
) {
    val colors = LocalMenuColors.current
    Column {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.surface)
                .padding(horizontal = if (layout.isWide) 40.dp else 20.dp)
                .padding(top = if (layout.isWide) 36.dp else 24.dp, bottom = 22.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Row(verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    if (subtitle.isNotEmpty()) {
                        AppText(subtitle, if (layout.isWide) 18 else 16, Bold, color = colors.secondaryText)
                    }
                    AppText(title, if (layout.isWide) 40 else 32, Bold)
                }
                if (isRefreshing) {
                    CircularProgressIndicator(
                        color = colors.accent,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(22.dp).padding(bottom = 4.dp),
                    )
                }
                trailing?.invoke()
            }
            accessory?.invoke()
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(colors.border))
    }
}

// MARK: - Menu screen frame (Today/Week)

@Composable
fun MenuScreen(
    viewModel: MenuViewModel,
    layout: ScreenLayout,
    title: String,
    subtitle: String,
    cafeteria: String,
    onSelectCafeteria: (String) -> Unit,
    content: @Composable (WeeklyMenu, String) -> Unit,
) {
    val menu = viewModel.menu
    Column(Modifier.fillMaxSize()) {
        ScreenHeader(
            subtitle = subtitle,
            title = title,
            layout = layout,
            isRefreshing = viewModel.isLoading && menu != null,
            accessory = {
                if (menu != null && menu.tables.size > 1 && !layout.showsColumns) {
                    CafeteriaPicker(menu.tables.map { it.cafeteria }, cafeteria, onSelectCafeteria)
                }
            },
        )
        when {
            menu != null -> content(menu, cafeteria)
            viewModel.isLoading -> LoadingState()
            viewModel.errorMessage != null -> ErrorState(viewModel.errorMessage!!) { viewModel.refresh() }
            else -> CenterMessage("메뉴가 없어요")
        }
    }
}

@Composable
fun CafeteriaPicker(names: List<String>, selection: String, onSelect: (String) -> Unit) {
    val colors = LocalMenuColors.current
    Row(
        Modifier
            .fillMaxWidth()
            .background(colors.track, RoundedCornerShape(14.dp))
            .padding(4.dp),
    ) {
        for (name in names) {
            val selected = name == selection
            Box(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .background(if (selected) colors.accent else Color.Transparent, RoundedCornerShape(10.dp))
                    .clickable { onSelect(name) },
                contentAlignment = Alignment.Center,
            ) {
                AppText(
                    name, 17, Bold,
                    color = if (selected) Color.White else colors.tertiaryText,
                    maxLines = 1,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun LoadingState() {
    val colors = LocalMenuColors.current
    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator(color = colors.accent)
        Spacer(Modifier.height(12.dp))
        AppText("메뉴를 읽는 중…", 17, Bold)
        Spacer(Modifier.height(4.dp))
        AppText("처음 불러올 때는 시간이 조금 걸려요.", 15, color = colors.secondaryText)
    }
}

@Composable
private fun ErrorState(message: String, onRetry: () -> Unit) {
    val colors = LocalMenuColors.current
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        AppText("메뉴를 불러올 수 없어요", 20, Bold)
        Spacer(Modifier.height(8.dp))
        AppText(message, 15, color = colors.secondaryText)
        Spacer(Modifier.height(16.dp))
        Box(
            Modifier
                .background(colors.accent, RoundedCornerShape(12.dp))
                .clickable { onRetry() }
                .padding(horizontal = 20.dp, vertical = 12.dp),
        ) { AppText("다시 시도", 16, Bold, color = Color.White) }
    }
}

@Composable
private fun CenterMessage(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        AppText(text, 18, Bold)
    }
}
