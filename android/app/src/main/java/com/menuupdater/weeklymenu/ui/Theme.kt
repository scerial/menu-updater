package com.menuupdater.weeklymenu.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The "Today's Menu" palette with dark-mode counterparts, ported from iOS `Theme.swift`. Exposed
 * through [LocalMenuColors] so composables read the right set for the current colour scheme.
 */
data class MenuColors(
    val accent: Color,
    val background: Color,
    val surface: Color,
    val text: Color,
    val secondaryText: Color,
    val tertiaryText: Color,
    val border: Color,
    val divider: Color,
    val track: Color,
    val chip: Color,
    val dashedBorder: Color,
)

private fun hex(value: Long) = Color(0xFF000000 or value)

private val LightColors = MenuColors(
    accent = hex(0xE27D68),
    background = hex(0xF4F5F7),
    surface = hex(0xFFFFFF),
    text = hex(0x16181D),
    secondaryText = hex(0x555B68),
    tertiaryText = hex(0x3B414D),
    border = hex(0xE2E4E9),
    divider = hex(0xEEF0F3),
    track = hex(0xEDEFF2),
    chip = hex(0xF4F5F7),
    dashedBorder = hex(0xC4C8D0),
)

private val DarkColors = MenuColors(
    accent = hex(0xE8866F),
    background = hex(0x0F1114),
    surface = hex(0x1B1E23),
    text = hex(0xF2F3F5),
    secondaryText = hex(0xA3A9B5),
    tertiaryText = hex(0xC5CAD3),
    border = hex(0x2C3038),
    divider = hex(0x262A31),
    track = hex(0x2A2E35),
    chip = hex(0x262A31),
    dashedBorder = hex(0x3A3F48),
)

val LocalMenuColors = staticCompositionLocalOf { LightColors }

/** Overall text scale; 80% was picked on a device as the best fit (iOS `Theme.uiScale`). */
const val UI_SCALE = 0.8f

/** Menu-text scale from the Settings size tier; 1.0 for non-menu UI text. */
val LocalMenuTextScale = staticCompositionLocalOf { 1f }

object MenuTheme {
    val colors: MenuColors
        @Composable get() = LocalMenuColors.current
}

@Composable
fun MenuTheme(content: @Composable () -> Unit) {
    val colors = if (isSystemInDarkTheme()) DarkColors else LightColors
    CompositionLocalProvider(LocalMenuColors provides colors, content = content)
}

/** A rounded card on the surface colour with a hairline (or accent) border. */
@Composable
fun Modifier.card(highlighted: Boolean = false, radius: Dp = 16.dp): Modifier {
    val colors = LocalMenuColors.current
    val shape = RoundedCornerShape(radius)
    return this
        .background(colors.surface, shape)
        .border(if (highlighted) 2.dp else 1.dp, if (highlighted) colors.accent else colors.border, shape)
}

/** Design point size scaled by the overall UI scale and, for menu text, the Settings size tier. */
@Composable
fun scaledSp(size: Int, menu: Boolean = false): androidx.compose.ui.unit.TextUnit {
    val menuScale = if (menu) LocalMenuTextScale.current else 1f
    return androidx.compose.ui.unit.TextUnit((size * UI_SCALE * menuScale), androidx.compose.ui.unit.TextUnitType.Sp)
}

/** Font weights used across the UI. */
val Bold = FontWeight.Bold
val Medium = FontWeight.Medium
val Regular = FontWeight.Normal
