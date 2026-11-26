package com.zyvioip.app.ui

import android.graphics.Canvas
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView

/**
 * 左滑平移卡片，露出卡片内的删除按钮（item_provider.xml 的 btnDelete）。
 *
 * 设计：
 * - 滑动**不删除任何数据**，只改变卡片的 translationX；
 * - `getMovementFlags` 收到的滑动会被 onChildDraw 限制在按钮宽度内；
 * - 松手时 clearView 吸附：超过一半保持露出，否则回弹到 0；
 * - 真正的删除由 btnDelete 的点击触发（ProviderAdapter 绑定），
 *   保证有明确的确认动作，也便于与「撤销」配合。
 */
class SwipeRevealCallback(
    private val revealWidthPx: Float,
) : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.END) {

    override fun onMove(
        rv: RecyclerView,
        vh: RecyclerView.ViewHolder,
        target: RecyclerView.ViewHolder,
    ): Boolean = false

    /**
     * ItemTouchHelper 自带「滑过阈值就移除」的语义，这里必须拦掉：
     * 返回 null 表示不做默认的滑动动画与删除，完全由 onChildDraw 控制。
     */
    override fun getSwipeDirs(
        rv: RecyclerView,
        vh: RecyclerView.ViewHolder,
    ): Int = if (vh is ProviderAdapter.VH) super.getSwipeDirs(rv, vh) else 0

    override fun onSwiped(vh: RecyclerView.ViewHolder, direction: Int) {
        // 不做删除；滑动结束后由 clearView 吸附回位
    }

    override fun getSwipeThreshold(vh: RecyclerView.ViewHolder): Float = 0.5f

    /** 限制最大滑动距离，避免把卡片拖出屏幕。 */
    override fun getSwipeEscapeVelocity(defaultValue: Float): Float = defaultValue

    override fun onChildDraw(
        c: Canvas,
        rv: RecyclerView,
        vh: RecyclerView.ViewHolder,
        dX: Float,
        dY: Float,
        actionState: Int,
        isCurrentlyActive: Boolean,
    ) {
        // 只平移「卡片」这一层；按钮在下面保持不动。
        // 若调用 super，整个 itemView 会被平移，按钮跟着走就永远露不出来。
        val clamped = dX.coerceIn(-revealWidthPx, 0f)
        val card = (vh as? ProviderAdapter.VH)?.cardView
        if (card != null) {
            card.translationX = clamped
            return
        }
        super.onChildDraw(c, rv, vh, clamped, dY, actionState, isCurrentlyActive)
    }

    override fun clearView(rv: RecyclerView, vh: RecyclerView.ViewHolder) {
        super.clearView(rv, vh)
        val card = (vh as? ProviderAdapter.VH)?.cardView ?: return
        val reveal = card.translationX <= -revealWidthPx / 2
        card.animate()
            .translationX(if (reveal) -revealWidthPx else 0f)
            .setDuration(160)
            .start()
    }

    /** 收起所有卡片（例如点击了别处）。 */
    fun closeAll(rv: RecyclerView) {
        for (i in 0 until rv.childCount) {
            val vh = rv.getChildViewHolder(rv.getChildAt(i))
            (vh as? ProviderAdapter.VH)?.cardView?.animate()?.translationX(0f)?.setDuration(160)?.start()
        }
    }
}
