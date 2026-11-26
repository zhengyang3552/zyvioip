package com.zyvioip.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * MD3 圆角刻度（来自规范 typography-and-shape.md）。
 *
 *   extraSmall 4  | small 8  | medium 12 | large 16 | extraLarge 28
 *
 * 组件默认值：
 *   card -> medium(12)   textField/chip -> small(8)   dialog/sheet -> extraLarge(28)
 *   button -> full（胶囊，用 CircleShape 或 50% 圆角）
 */
internal val Md3Shapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(28.dp),
)
