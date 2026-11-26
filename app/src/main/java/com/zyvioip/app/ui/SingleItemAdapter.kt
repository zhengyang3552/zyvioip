package com.zyvioip.app.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import androidx.viewbinding.ViewBinding

/**
 * 固定单条内容的适配器，用于 ConcatAdapter 的头/尾。
 *
 * 与供应商列表用 ConcatAdapter 拼接：
 *   header（搜索区 + 摘要） + 供应商列表（DiffUtil） + footer（添加按钮）
 *
 * 这样整页可以一起滚动，而供应商条目依然是独立复用、
 * 由 DiffUtil 决定是否重绘 —— 不会退化成「把列表塞进 ScrollView」。
 */
class SingleItemAdapter<V : ViewBinding>(
    private val inflate: (LayoutInflater, ViewGroup) -> V,
    private val onBind: (V) -> Unit,
) : RecyclerView.Adapter<SingleItemAdapter<V>.Holder>() {

    private var binding: V? = null

    inner class Holder(val b: V) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
        Holder(inflate(LayoutInflater.from(parent.context), parent))

    override fun onBindViewHolder(holder: Holder, position: Int) {
        binding = holder.b
        onBind(holder.b)
    }

    override fun getItemCount(): Int = 1

    /** 供外部主动刷新内容（例如加载状态变化）。 */
    fun refresh() {
        if (binding != null) notifyItemChanged(0)
    }

    internal fun currentBinding(): V? = binding
}
