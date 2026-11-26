package com.zyvioip.app.ui

import android.graphics.Canvas
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView

/**
 * 左滑平移卡片，露出**卡片内**的删除按钮（item_provider.xml 里的 btnDelete）。
 *
 * 关键点：
 * - 滑动不会删除任何数据，只改变卡片的 translationX，滑动结束回弹到「露出」位置；
 * - 真正的删除由 btnDelete 的点击触发（由 ProviderAdapter 绑定），
 *   这样有明确的确认动作，也能与「撤销」配合；
 * - 不使用 ItemTouchHelper 的 onSwiped 删除，避免「滑一下就没了」的误触。
 */
class SwipeRevealCallback(
    private val revealWidthPx: Float,
    private val onRevealed: (Int) -> Unit,
) : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.END) {

    override fun onMove(
        rv: RecyclerView,
        vh: RecyclerView.ViewHolder,
        target: RecyclerView.ViewHolder,
    ): Boolean = false

    override fun onSwiped(vh: RecyclerView.ViewHolder, direction: Int) {
        // 不用滑动删除：回弹到露出状态即可
        val pos = vh.bindingAdapterPosition
        if (pos != RecyclerView.NO_POSITION) onRevealed(pos)
    }

    override fun getSwipeThreshold(vh: RecyclerView.ViewHolder): Float = 0.5f

    override fun onChildDraw(
        c: Canvas,
        rv: RecyclerView,
        vh: RecyclerView.ViewHolder,
        dX: Float,
        dY: Float,
        actionState: Int,
        isCurrentlyActive: Boolean,
    ) {
        // 只平移「卡片」这一层，背景的删除按钮保持不动 ——
        // 否则 ItemTouchHelper 默认会连按钮一起拖动，按钮永远露不出来。
        val clamped = dX.coerceIn(-revealWidthPx, 0f)
        val card = (vh as? ProviderAdapter.VH)?.cardView
        if (card != null) {
            card.translationX = clamped
            // 不消费默认绘制，否则整个 itemView 会被二次平移
            return
        }
        super.onChildDraw(c, rv, vh, clamped, dY, actionState, isCurrentlyActive)
    }

    override fun clearView(rv: RecyclerView, vh: RecyclerView.ViewHolder) {
        super.clearView(rv, vh)
        // 松手后吸附：超过一半就保持露出，否则回弹
        val card = (vh as? ProviderAdapter.VH)?.cardView
        if (card != null) {
            val reveal = card.translationX <= -revealWidthPx / 2
            card.animate()
                .translationX(if (reveal) -revealWidthPx else 0f)
                .setDuration(160)
                .start()
        }
    }
}
