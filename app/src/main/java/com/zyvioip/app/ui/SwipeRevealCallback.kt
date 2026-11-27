package com.zyvioip.app.ui

import android.graphics.Canvas
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.zyvioip.app.R

/**
 * 左滑平移卡片，露出卡片内的删除按钮（item_provider.xml 的 btnDelete）。
 *
 * 注意：不能用 `vh as? ProviderAdapter.VH` 取卡片 ——
 * 列表外层套了 ConcatAdapter，onChildDraw 收到的 ViewHolder 可能被包装过，
 * 转型会失败、静默走 fallback，表现为「左滑没反应」。
 * 这里直接从 itemView 按 id 找控件，不依赖 ViewHolder 的具体类型。
 */
class SwipeRevealCallback(
    private val revealWidthPx: Float,
) : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.END) {

    override fun onMove(
        rv: RecyclerView,
        vh: RecyclerView.ViewHolder,
        target: RecyclerView.ViewHolder,
    ): Boolean = false

    override fun onSwiped(vh: RecyclerView.ViewHolder, direction: Int) {
        // 不做删除：松手后由 clearView 吸附回位
    }

    override fun getSwipeThreshold(vh: RecyclerView.ViewHolder): Float = 0.5f

    /** header / footer 不是供应商卡片，不响应滑动。 */
    override fun getSwipeDirs(rv: RecyclerView, vh: RecyclerView.ViewHolder): Int =
        if (vh.itemView.findViewById<android.view.View>(R.id.card) != null) {
            super.getSwipeDirs(rv, vh)
        } else {
            0
        }

    override fun onChildDraw(
        c: Canvas,
        rv: RecyclerView,
        vh: RecyclerView.ViewHolder,
        dX: Float,
        dY: Float,
        actionState: Int,
        isCurrentlyActive: Boolean,
    ) {
        val card = vh.itemView.findViewById<android.view.View>(R.id.card)
        if (card == null) {
            // 不是供应商卡片（header/footer），交给默认处理
            super.onChildDraw(c, rv, vh, dX, dY, actionState, isCurrentlyActive)
            return
        }
        // 只平移卡片层；若调用 super，整个 itemView 会被平移，按钮跟着走就露不出来
        card.translationX = dX.coerceIn(-revealWidthPx, 0f)
    }

    override fun clearView(rv: RecyclerView, vh: RecyclerView.ViewHolder) {
        super.clearView(rv, vh)
        val card = vh.itemView.findViewById<android.view.View>(R.id.card) ?: return
        val reveal = card.translationX <= -revealWidthPx / 2
        card.animate()
            .translationX(if (reveal) -revealWidthPx else 0f)
            .setDuration(160)
            .start()
    }
}
