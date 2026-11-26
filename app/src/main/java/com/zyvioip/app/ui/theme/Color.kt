package com.zyvioip.app.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * MD3 基线色板（Baseline color scheme）。
 *
 * 只定义品牌 primary 的种子色，其余 26+ 个角色按 MD3 规范配齐 —— 
 * 之前只写了 primary/onPrimary/secondary/surface/background 六个值，
 * 缺失的角色全部落到 Compose 默认值，导致深浅色对比度不一致、
 * 容器色（container）与表面层级（surfaceContainer*）完全没生效。
 *
 * 用法约束（来自 MD3 规范）：
 *  - surface          : 页面背景
 *  - surfaceContainer : 导航区/次级面板
 *  - surfaceContainerLow/High : 卡片等容器的层级区分
 *  - outlineVariant   : 分割线（不要用 outline）
 *  - outline          : 需要 3:1 对比度的交互边界（输入框边框）
 *
 * 注：surfaceDim / surfaceBright 需要 material3 1.3.0+，
 * 本项目 BOM 2024.06（material3 1.2.1）暂不支持，故未定义。
 */

// ---- Light ----
internal val LightPrimary = Color(0xFF2E4B8F)
internal val LightOnPrimary = Color(0xFFFFFFFF)
internal val LightPrimaryContainer = Color(0xFFDAE2FF)
internal val LightOnPrimaryContainer = Color(0xFF001945)

internal val LightSecondary = Color(0xFF565E71)
internal val LightOnSecondary = Color(0xFFFFFFFF)
internal val LightSecondaryContainer = Color(0xFFDAE2F9)
internal val LightOnSecondaryContainer = Color(0xFF131C2B)

internal val LightTertiary = Color(0xFF705573)
internal val LightOnTertiary = Color(0xFFFFFFFF)
internal val LightTertiaryContainer = Color(0xFFFAD8FB)
internal val LightOnTertiaryContainer = Color(0xFF28132C)

internal val LightError = Color(0xFFBA1A1A)
internal val LightOnError = Color(0xFFFFFFFF)
internal val LightErrorContainer = Color(0xFFFFDAD6)
internal val LightOnErrorContainer = Color(0xFF410002)

internal val LightSurface = Color(0xFFFAF8FF)
internal val LightOnSurface = Color(0xFF1A1B20)
internal val LightSurfaceVariant = Color(0xFFE1E2EC)
internal val LightOnSurfaceVariant = Color(0xFF44474F)
internal val LightOutline = Color(0xFF75777F)
internal val LightOutlineVariant = Color(0xFFC5C6D0)

internal val LightSurfaceContainerLowest = Color(0xFFFFFFFF)
internal val LightSurfaceContainerLow = Color(0xFFF4F3FA)
internal val LightSurfaceContainer = Color(0xFFEEEDF4)
internal val LightSurfaceContainerHigh = Color(0xFFE8E7EF)
internal val LightSurfaceContainerHighest = Color(0xFFE3E1E9)

// ---- Dark ----
internal val DarkPrimary = Color(0xFFB3C5FF)
internal val DarkOnPrimary = Color(0xFF002C71)
internal val DarkPrimaryContainer = Color(0xFF0F3E85)
internal val DarkOnPrimaryContainer = Color(0xFFDAE2FF)

internal val DarkSecondary = Color(0xFFBEC6DC)
internal val DarkOnSecondary = Color(0xFF283041)
internal val DarkSecondaryContainer = Color(0xFF3E4759)
internal val DarkOnSecondaryContainer = Color(0xFFDAE2F9)

internal val DarkTertiary = Color(0xFFDDBCDF)
internal val DarkOnTertiary = Color(0xFF3F2843)
internal val DarkTertiaryContainer = Color(0xFF573E5B)
internal val DarkOnTertiaryContainer = Color(0xFFFAD8FB)

internal val DarkError = Color(0xFFFFB4AB)
internal val DarkOnError = Color(0xFF690005)
internal val DarkErrorContainer = Color(0xFF93000A)
internal val DarkOnErrorContainer = Color(0xFFFFDAD6)

internal val DarkSurface = Color(0xFF121318)
internal val DarkOnSurface = Color(0xFFE3E1E9)
internal val DarkSurfaceVariant = Color(0xFF44474F)
internal val DarkOnSurfaceVariant = Color(0xFFC5C6D0)
internal val DarkOutline = Color(0xFF8F9099)
internal val DarkOutlineVariant = Color(0xFF44474F)

internal val DarkSurfaceContainerLowest = Color(0xFF0D0E13)
internal val DarkSurfaceContainerLow = Color(0xFF1A1B20)
internal val DarkSurfaceContainer = Color(0xFF1E1F25)
internal val DarkSurfaceContainerHigh = Color(0xFF282A2F)
internal val DarkSurfaceContainerHighest = Color(0xFF33353A)
