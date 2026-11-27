package com.zyvioip.app.ui

import android.annotation.SuppressLint
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.zyvioip.app.R
import kotlin.math.abs

/**
 * 卡片左滑露出删除按钮。
 *
 * 实现要点：
 * - itemView 显式设为 clickable，否则 FrameLayout 的 OnTouchListener
 *   在事件被子 View 消费时不会触发（卡片里全是 TextView/Button，
 *   手指落在文字上就滑不动）；
 * - 方向判定：横向位移 > 纵向 × 1.5 且 > 12dp 才认定为滑动，
 *   否则原样返回，点击与纵向滚动都不受影响；
 * - 确认横滑后 requestDisallowInterceptTouchEvent(true)，
 *   防止 RecyclerView 把手势抢过去当滚动；
 * - 删除按钮默认 invisible + 不可点，只有滑开超过 30% 才启用。
 */
object SwipeRevealHelper {

    @SuppressLint("ClickableViewAccessibility")
    fun attach(itemView: View) {
        val card = itemView.findViewById<View>(R.id.card) ?: return
        val btn = itemView.findViewById<View>(R.id.btnDelete) ?: return
        val revealWidth = itemView.resources.getDimension(R.dimen.delete_button_size)

        var downX = 0f
        var downY = 0f
        var startTx = 0f
        var horizontal = false

        // 初始态：收起
        card.translationX = 0f
        setBtn(btn, false)

        val handler = object : View.OnTouchListener {
            @SuppressLint("ClickableViewAccessibility")
            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        downX = event.rawX
                        downY = event.rawY
                        startTx = card.translationX
                        horizontal = false
                        return false
                    }

                    MotionEvent.ACTION_MOVE -> {
                        val dx = event.rawX - downX
                        val dy = event.rawY - downY
                        if (!horizontal && abs(dx) > abs(dy) * 1.5f && abs(dx) > 12f) {
                            horizontal = true
                            (itemView.parent as? ViewGroup)
                                ?.requestDisallowInterceptTouchEvent(true)
                        }
                        if (horizontal) {
                            val target = (startTx + dx).coerceIn(-revealWidth, 0f)
                            card.translationX = target
                            setBtn(btn, target < -revealWidth * 0.3f)
                            return true
                        }
                        return false
                    }

                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        (itemView.parent as? ViewGroup)
                            ?.requestDisallowInterceptTouchEvent(false)
                        if (horizontal) {
                            snap(card, btn, card.translationX < -revealWidth / 2, revealWidth)
                            return true
                        }
                        return false
                    }
                }
                return false
            }
        }

        // itemView 必须可点击，OnTouchListener 才会收到未被消费的事件
        itemView.isClickable = true
        itemView.setOnTouchListener(handler)
    }

    private fun setBtn(btn: View, revealed: Boolean) {
        btn.visibility = if (revealed) View.VISIBLE else View.INVISIBLE
        btn.isClickable = revealed
        btn.isEnabled = revealed
    }

    private fun snap(card: View, btn: View, reveal: Boolean, revealWidth: Float) {
        card.animate()
            .translationX(if (reveal) -revealWidth else 0f)
            .setDuration(150)
            .start()
        setBtn(btn, reveal)
    }

    /** 列表滚动 / 其它卡片展开时，收起所有卡片。 */
    fun collapseAll(rv: RecyclerView) {
        val revealWidth = rv.resources.getDimension(R.dimen.delete_button_size)
        for (i in 0 until rv.childCount) {
            val item = rv.getChildAt(i)
            val card = item.findViewById<View>(R.id.card) ?: continue
            val btn = item.findViewById<View>(R.id.btnDelete) ?: continue
            if (card.translationX != 0f) snap(card, btn, false, revealWidth)
        }
    }
}
