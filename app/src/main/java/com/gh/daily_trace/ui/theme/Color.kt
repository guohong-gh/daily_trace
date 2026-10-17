package com.gh.daily_trace.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/*
 * 品牌色板：以「生长绿」为主色（打卡 = 习惯养成），琥珀色为辅助强调色（备注/补卡/提醒）。
 * 明暗两套均按 Material 3 明度分级挑选，正文与底色对比度 ≥ 4.5:1，粗体/大字 ≥ 3:1。
 * 色盲友好约定：颜色仅作辅助信息，选项始终保留 emoji 图标（双重编码）。
 */

val BrandLightColors = lightColorScheme(
    primary = Color(0xFF2E6B4A),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFB2F1C6),
    onPrimaryContainer = Color(0xFF00210F),

    secondary = Color(0xFF516350),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD4E8D0),
    onSecondaryContainer = Color(0xFF0F1F10),

    tertiary = Color(0xFF835500),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFDEA6),
    onTertiaryContainer = Color(0xFF2A1800),

    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),

    background = Color(0xFFF7FBF2),
    onBackground = Color(0xFF181D18),
    surface = Color(0xFFF7FBF2),
    onSurface = Color(0xFF181D18),
    surfaceVariant = Color(0xFFDDE5DA),
    onSurfaceVariant = Color(0xFF414941),
    outline = Color(0xFF717971),
    outlineVariant = Color(0xFFC1C9BF),

    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF1F5EC),
    surfaceContainer = Color(0xFFEBF0E6),
    surfaceContainerHigh = Color(0xFFE6EAE0),
    surfaceContainerHighest = Color(0xFFE0E4DA),

    inverseSurface = Color(0xFF2D322C),
    inverseOnSurface = Color(0xFFEEF2E9),
    inversePrimary = Color(0xFF97D5AA),
)

val BrandDarkColors = darkColorScheme(
    primary = Color(0xFF97D5AA),
    onPrimary = Color(0xFF00391F),
    primaryContainer = Color(0xFF115232),
    onPrimaryContainer = Color(0xFFB2F1C6),

    secondary = Color(0xFFB8CCB5),
    onSecondary = Color(0xFF243424),
    secondaryContainer = Color(0xFF3A4B3A),
    onSecondaryContainer = Color(0xFFD4E8D0),

    tertiary = Color(0xFFFFBA4A),
    onTertiary = Color(0xFF452B00),
    tertiaryContainer = Color(0xFF614000),
    onTertiaryContainer = Color(0xFFFFDEA6),

    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),

    background = Color(0xFF101510),
    onBackground = Color(0xFFE0E4DC),
    surface = Color(0xFF101510),
    onSurface = Color(0xFFE0E4DC),
    surfaceVariant = Color(0xFF414941),
    onSurfaceVariant = Color(0xFFC1C9BF),
    outline = Color(0xFF8B938A),
    outlineVariant = Color(0xFF414941),

    surfaceContainerLowest = Color(0xFF0B0F0B),
    surfaceContainerLow = Color(0xFF181D18),
    surfaceContainer = Color(0xFF1C211C),
    surfaceContainerHigh = Color(0xFF262B26),
    surfaceContainerHighest = Color(0xFF313630),

    inverseSurface = Color(0xFFE0E4DC),
    inverseOnSurface = Color(0xFF2D322C),
    inversePrimary = Color(0xFF2E6B4A),
)
