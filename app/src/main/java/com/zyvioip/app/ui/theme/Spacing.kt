package com.zyvioip.app.ui.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * MD3 8dp 间距系统（Google I/O 2026 更新强调）。
 *
 * 所有外边距、内边距、元素间隔都应取自这组 token，
 * 而不是随手写 5.dp / 6.dp / 7.dp 这类魔法数字 ——
 * 否则不同屏幕密度下会显得松散不一致。
 *
 * 基础序列：4（半档）/ 8 / 12 / 16 / 24 / 32
 */
object Spacing {
    /** 4dp：图标与文字之间的紧凑间隔 */
    val xs: Dp = 4.dp

    /** 8dp：组件内部最小间距 */
    val sm: Dp = 8.dp

    /** 12dp：相关元素之间 */
    val md: Dp = 12.dp

    /** 16dp：屏幕左右标准边距、区块之间 */
    val lg: Dp = 16.dp

    /** 24dp：大区块分隔 */
    val xl: Dp = 24.dp

    /** 32dp：页面级留白 */
    val xxl: Dp = 32.dp
}
