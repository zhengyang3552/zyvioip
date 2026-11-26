package com.zyvioip.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zyvioip.app.ui.theme.Spacing

/**
 * MD3 胶囊标签（Chip）。
 *
 * MD3 把 chip 归类为 Input 组件，圆角为 `full`（胶囊形），
 * 用于承载「短小、离散」的元信息 —— 正好对应本 App 的
 * IP 来源（原生/广播/机房）与风险标记（VPN/代理/Tor）。
 *
 * 之前这类信息是普通文本（"来源：xxx"、"标记：a、b"），
 * 混在正文里不易扫读；改成胶囊后可按颜色与位置快速区分。
 */
@Composable
fun InfoPill(
    text: String,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.secondaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onSecondaryContainer,
    leadingIcon: ImageVector? = null,
    emphasized: Boolean = false,
) {
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = if (emphasized) MaterialTheme.colorScheme.primary else containerColor,
        contentColor = if (emphasized) MaterialTheme.colorScheme.onPrimary else contentColor,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.xs + 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            if (leadingIcon != null) {
                Icon(leadingIcon, contentDescription = null, modifier = Modifier.size(14.dp))
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * 描边样式胶囊：用于次级信息，视觉权重低于实心胶囊，
 * 避免一屏全是重色块。
 */
@Composable
fun OutlinedPill(
    text: String,
    modifier: Modifier = Modifier,
    contentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = Color.Transparent,
        contentColor = contentColor,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.xs + 2.dp),
        )
    }
}

/**
 * 胶囊横向排列。
 *
 * 之前用 FlowRow：它是 Compose 里测量开销最高的布局之一（需要两趟测量、
 * 且每帧都要重新计算换行位置）。每张供应商卡片都用它，滚动时开销累加明显。
 *
 * 这里改为单行横向滚动：普通 Row 的测量是一趟、线性复杂度。
 * 胶囊总宽度超屏时用户可横向滑动查看，比强制换行更可控，
 * 也不会让卡片高度随内容跳变。
 */
@Composable
fun PillRow(
    modifier: Modifier = Modifier,
    spacing: androidx.compose.ui.unit.Dp = Spacing.xs,
    content: @Composable () -> Unit,
) {
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(spacing),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        content()
    }
}
