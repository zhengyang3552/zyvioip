package com.zyvioip.app.ui

import android.annotation.SuppressLint
import android.view.MotionEvent
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import com.zyvioip.app.R
import kotlin.math.abs

/**
 * 卡片左滑露出删除按钮（不依赖 ItemTouchHelper）。
 *
 * 为什么不用 ItemTouchHelper：
 * - 列表外层套了 ConcatAdapter，getSwipeDirs 阶段视图未就绪，
 *   按位置/按 id 判断都不可靠，结果是「滑不动」；
 * - onChildDraw 里手动改 translationX 与 ItemTouchHelper 自身的偏移计算叠加，
 *   会出现「点按位置与实际控件错位」。
 *
 * 这里直接在卡片上做水平拖拽：只关心手指横向位移，
 * 不参与 RecyclerView 的布局与测量，因此不会与列表滚动冲突
 * （纵向手势仍交给 RecyclerView）。
 */
object SwipeRevealHelper {

    /** 给一个卡片 item 绑定滑动行为。 */
    @SuppressLint("ClickableViewAccessibility")
    fun attach(itemView: View) {
        val card = itemView.findViewById<View>(R.id.card)
        val btn = itemView.findViewById<View>(R.id.btnDelete)
        // header / footer 没有这两个控件
        if (card == null || btn == null) return

        val revealWidth = itemView.resources.getDimension(R.dimen.delete_button_size)
        var downX = 0f
        var downY = 0f
        var startTx = 0f
        // 是否已判定为横向手势（决定要不要拦截事件）
        var horizontal = false

        // 初始态：收起
        card.translationX = 0f
        btn.visibility = View.INVISIBLE
        btn.isClickable = false

        card.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    downY = event.rawY
                    startTx = card.translationX
                    horizontal = false
                    false
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downX
                    val dy = event.rawY - downY
                    if (!horizontal && abs(dx) > abs(dy) * 1.5f && abs(dx) > 12f) {
                        horizontal = true
                        // 开始横向拖动时让父容器不再滚动
                        itemView.parent?.requestDisallowInterceptTouchEvent(true)
                    }
                    if (horizontal) {
                        val target = (startTx + dx).coerceIn(-revealWidth, 0f)
                        card.translationX = target
                        val revealed = target < -revealWidth * 0.3f
                        btn.visibility = if (revealed) View.VISIBLE else View.INVISIBLE
                        btn.isClickable = revealed
                        true
                    } else {
                        false
                    }
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (horizontal) {
                        itemView.parent?.requestDisallowInterceptTouchEvent(false)
                        val reveal = card.translationX < -revealWidth / 2
                        card.animate()
                            .translationX(if (reveal) -revealWidth else 0f)
                            .setDuration(150)
                            .start()
                        btn.visibility = if (reveal) View.VISIBLE else View.INVISIBLE
                        btn.isClickable = reveal
                        true
                    } else {
                        false
                    }
                }

                else -> false
            }
        }
    }

    /**
     * 列表滚动时收起所有已展开的卡片，避免带着偏移滚出屏幕。
     */
    fun collapseAll(rv: RecyclerView) {
        for (i in 0 until rv.childCount) {
            val item = rv.getChildAt(i)
            val card = item.findViewById<View>(R.id.card) ?: continue
            val btn = item.findViewById<View>(R.id.btnDelete) ?: continue
            if (card.translationX != 0f) {
                card.animate().translationX(0f).setDuration(120).start()
                btn.visibility = View.INVISIBLE
                btn.isClickable = false
            }
        }
    }
}
