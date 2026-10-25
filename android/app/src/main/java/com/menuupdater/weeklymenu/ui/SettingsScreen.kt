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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.menuupdater.weeklymenu.R
import com.menuupdater.weeklymenu.domain.TextSize

@Composable
fun SettingsScreen(viewModel: MenuViewModel, layout: ScreenLayout) {
    val colors = LocalMenuColors.current
    val context = LocalContext.current
    val defaultCafeteria by viewModel.defaultCafeteria.collectAsState()
    val textSize by viewModel.textSize.collectAsState()
    val cafeterias = viewModel.menu?.tables?.map { it.cafeteria }?.filter { it.isNotEmpty() }.orEmpty()

    val tr = LocalTr.current

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(subtitle = stringResource(R.string.settings_subtitle), title = stringResource(R.string.settings_title), layout = layout)

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = layout.horizontalPadding)
                .padding(top = 20.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(28.dp),
        ) {
            SettingsSection(
                title = stringResource(R.string.settings_default_cafeteria),
                footer = stringResource(if (cafeterias.isEmpty()) R.string.settings_default_empty else R.string.settings_default_footer),
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
                        AppText(tr.place(name), 17, modifier = Modifier.weight(1f))
                        if (isSelected) AppText("✓", 17, Bold, color = colors.accent)
                    }
                }
            }

            SettingsSection(title = stringResource(R.string.settings_display)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    AppText(stringResource(R.string.settings_text_size), 17)
                    TextSizePicker(textSize) { viewModel.setTextSize(it) }
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .background(colors.chip, RoundedCornerShape(12.dp))
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        // A sample dish, drawn like a menu card so the size tiers can be compared.
                        AppText(tr.dish("제육볶음"), 19, Bold, menu = true)
                        AppText(listOf("쌀밥", "된장국", "잡채").joinToString(" · ") { tr.dish(it) }, 15, menu = true, color = colors.secondaryText)
                    }
                }
            }

            SettingsSection(title = stringResource(R.string.settings_about)) {
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
                        AppText(stringResource(R.string.settings_source), 17, modifier = Modifier.weight(1f))
                        AppText("›", 17, color = colors.secondaryText)
                    }
                    SettingsDivider()
                }
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AppText(stringResource(R.string.settings_version), 17, modifier = Modifier.weight(1f))
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
                AppText(textSizeLabel(size), 16, Bold, color = if (selected) colors.text else colors.tertiaryText)
            }
        }
    }
}

private fun appVersion(context: Context): String =
    runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "1.0"
