package com.menuupdater.weeklymenu.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.menuupdater.weeklymenu.domain.TextSize

@Composable
fun SettingsScreen(viewModel: MenuViewModel, layout: ScreenLayout) {
    val colors = LocalMenuColors.current
    val context = LocalContext.current
    val defaultCafeteria by viewModel.defaultCafeteria.collectAsState()
    val textSize by viewModel.textSize.collectAsState()
    val cafeterias = viewModel.menu?.tables?.map { it.cafeteria }?.filter { it.isNotEmpty() }.orEmpty()

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(subtitle = "기본 식당 · 화면 · 정보", title = "설정", layout = layout)

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = layout.horizontalPadding)
                .padding(top = 20.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(28.dp),
        ) {
            SettingsSection(
                title = "기본 식당",
                footer = if (cafeterias.isEmpty()) "메뉴를 불러오면 식당을 고를 수 있어요." else "앱을 열면 이 식당의 메뉴를 먼저 보여줘요.",
            ) {
                cafeterias.forEachIndexed { index, name ->
                    if (index > 0) SettingsDivider()
                    val isSelected = name == defaultCafeteria || (index == 0 && !cafeterias.contains(defaultCafeteria))
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 56.dp)
                            .clickable { viewModel.setDefaultCafeteria(name) }
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AppText(name, 17, modifier = Modifier.weight(1f))
                        if (isSelected) AppText("✓", 17, Bold, color = colors.accent)
                    }
                }
            }

            SettingsSection(title = "화면") {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    AppText("글자 크기", 17)
                    TextSizePicker(textSize) { viewModel.setTextSize(it) }
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .background(colors.chip, RoundedCornerShape(12.dp))
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        AppText("제육볶음", 19, Bold, menu = true)
                        AppText("쌀밥 · 된장국 · 잡채", 15, menu = true, color = colors.secondaryText)
                    }
                }
            }

            SettingsSection(title = "정보") {
                viewModel.menu?.sourceURL?.let { url ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 56.dp)
                            .clickable {
                                runCatching {
                                    context.startActivity(
                                        android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url)),
                                    )
                                }
                            }
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AppText("원본 게시물 보기", 17, modifier = Modifier.weight(1f))
                        AppText("›", 17, color = colors.secondaryText)
                    }
                    SettingsDivider()
                }
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AppText("앱 버전", 17, modifier = Modifier.weight(1f))
                    AppText(appVersion(context), 17, color = colors.secondaryText)
                }
            }
        }
    }
}

@Composable
fun SettingsSection(title: String, footer: String? = null, content: @Composable ColumnScope.() -> Unit) {
    val colors = LocalMenuColors.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        AppText(title, 15, Bold, color = colors.secondaryText, modifier = Modifier.padding(horizontal = 4.dp))
        Column(Modifier.fillMaxWidth().card()) { content() }
        footer?.let { AppText(it, 15, color = colors.secondaryText, modifier = Modifier.padding(horizontal = 4.dp)) }
    }
}

@Composable
fun SettingsDivider() {
    val colors = LocalMenuColors.current
    Box(Modifier.fillMaxWidth().padding(start = 16.dp).height(1.dp).background(colors.divider))
}

@Composable
private fun TextSizePicker(selection: TextSize, onSelect: (TextSize) -> Unit) {
    val colors = LocalMenuColors.current
    Row(
        Modifier.fillMaxWidth().background(colors.track, RoundedCornerShape(12.dp)).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        for (size in TextSize.entries) {
            val selected = size == selection
            Box(
                Modifier
                    .weight(1f)
                    .heightIn(min = 44.dp)
                    .background(if (selected) colors.surface else Color.Transparent, RoundedCornerShape(9.dp))
                    .clickable { onSelect(size) },
                contentAlignment = Alignment.Center,
            ) {
                AppText(size.label, 16, Bold, color = if (selected) colors.text else colors.tertiaryText)
            }
        }
    }
}

private fun appVersion(context: Context): String =
    runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "1.0"
