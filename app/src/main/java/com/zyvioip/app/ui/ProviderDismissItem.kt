package com.zyvioip.app.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.zyvioip.app.provider.ProviderCatalog
import com.zyvioip.app.provider.ProviderOutcome

/**
 * 供应商卡片 + 左滑露出删除按钮。
 *
 * 之前用的是 SwipeToDismissBox + confirmValueChange=true，滑过阈值就立刻删卡，
 * 而且删除按钮画在卡片下层，点不到 —— 想撤销只能靠 Snackbar。
 * 现在改成：左滑把卡片平移露出右侧删除按钮，点按钮才真正删除，点卡片空白处收起。
 */
@Composable
internal fun ProviderDismissItem(
    state: ProviderOutcome,
    onRetry: (String) -> Unit,
    onRemove: (String) -> Unit,
) {
    val spec = ProviderCatalog.byId(state.providerId) ?: return
    val actionWidthPx = with(LocalDensity.current) { ACTION_WIDTH.toPx() }
    var offsetX by remember(state.providerId) { mutableFloatStateOf(0f) }
    var revealed by remember(state.providerId) { mutableStateOf(false) }

    // 卡片被移出列表时把偏移归零，避免复用错位
    LaunchedEffect(Unit) { offsetX = 0f }

    val animatedOffset by animateFloatAsState(
        targetValue = if (revealed) -actionWidthPx else 0f,
        label = "provider-card-offset",
    )
    val currentOffset = if (offsetX != 0f) offsetX else animatedOffset

    Box(modifier = Modifier.fillMaxWidth()) {
        // 背景层：删除按钮。只有卡片被推开后才可点。
        Row(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .width(ACTION_WIDTH)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.errorContainer),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
        ) {
            IconButton(
                onClick = {
                    revealed = false
                    onRemove(state.providerId)
                },
                enabled = revealed || currentOffset < -actionWidthPx * 0.3f,
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "移除 ${spec.name}",
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.size(20.dp),
                )
            }
        }

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer { translationX = currentOffset }
                .pointerInput(state.providerId, actionWidthPx) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            // 超过一半就吸附到“已展开”，否则回弹
                            revealed = offsetX < -actionWidthPx * 0.5f
                            offsetX = 0f
                        },
                        onHorizontalDrag = { _, dragAmount ->
                            offsetX = (offsetX + dragAmount).coerceIn(-actionWidthPx * 1.2f, 0f)
                        },
                    )
                },
        ) {
            ProviderCard(spec = spec, result = state, onRetry = onRetry)
        }
    }
}

private val ACTION_WIDTH = 64.dp

/** 兜底：给删除动作一个明确的文案提示，避免用户只能靠手势发现该功能。 */
@Composable
internal fun RemoveHint() {
    Text(
        "左滑卡片可移除供应商",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
        modifier = Modifier.padding(start = 2.dp, bottom = 2.dp),
    )
}
