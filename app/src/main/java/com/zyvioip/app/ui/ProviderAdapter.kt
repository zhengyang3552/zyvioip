package com.zyvioip.app.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.zyvioip.app.R
import com.zyvioip.app.databinding.ItemProviderBinding
import com.zyvioip.app.provider.ProviderCatalog
import com.zyvioip.app.provider.ProviderFlag
import com.zyvioip.app.provider.ProviderOutcome
import com.zyvioip.app.provider.ProviderStatus

/**
 * 供应商列表适配器。
 *
 * 关键点（对照 ZYNotificationHistory 的做法）：
 * - 继承 ListAdapter，由 **DiffUtil 强制**做差异比对：
 *   数据内容没变时 areContentsTheSame 返回 true，条目完全不重绑；
 *   这解决了 Compose 里「整体替换 List 就全量重组」的问题。
 * - 展开状态记录在 expandedIds 中，跨 submitList 保留；
 * - 展开的字段列表按需创建，收起时清空子 View，避免长期占用内存。
 */
class ProviderAdapter(
    private val onRetry: (String) -> Unit,
    private val onRemove: (String) -> Unit,
) : ListAdapter<ProviderOutcome, ProviderAdapter.VH>(Diff) {

    /** 当前展开的 providerId，跨数据刷新保留。 */
    private val expandedIds = mutableSetOf<String>()

    fun isExpanded(id: String): Boolean = id in expandedIds

    fun toggleExpanded(id: String): Boolean {
        val next = if (id in expandedIds) {
            expandedIds.remove(id); false
        } else {
            expandedIds.add(id); true
        }
        val pos = currentList.indexOfFirst { it.providerId == id }
        if (pos >= 0) notifyItemChanged(pos, PAYLOAD_EXPAND)
        return next
    }

    /** 某条被移除后清理它的展开状态。 */
    fun forgetExpanded(id: String) {
        expandedIds.remove(id)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemProviderBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(getItem(position))
    }

    override fun onBindViewHolder(holder: VH, position: Int, payloads: MutableList<Any>) {
        // 只切展开/收起时走局部刷新，不重绑整行
        if (payloads.contains(PAYLOAD_EXPAND)) {
            holder.applyExpanded(getItem(position), isExpanded(getItem(position).providerId))
        } else {
            super.onBindViewHolder(holder, position, payloads)
        }
    }

    inner class VH(private val b: ItemProviderBinding) : RecyclerView.ViewHolder(b.root) {

        /** 供滑动回调平移的卡片层（不含背景删除按钮）。 */
        val cardView: android.view.View get() = b.card

        init {
            // 卡片左滑露出删除按钮（不依赖 ItemTouchHelper）
            SwipeRevealHelper.attach(b.root)

            b.btnDelete.setOnClickListener {
                val pos = bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) {
                    b.card.translationX = 0f
                    onRemove(getItem(pos).providerId)
                }
            }
            b.btnRetry.setOnClickListener {
                val pos = bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) onRetry(getItem(pos).providerId)
            }
            b.btnExpand.setOnClickListener {
                val pos = bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) toggleExpanded(getItem(pos).providerId)
            }
        }

        fun bind(item: ProviderOutcome) {
            // 复用视图时复位滑动位移与删除按钮状态，否则会带着上一张卡的状态
            b.card.translationX = 0f
            b.btnDelete.visibility = View.INVISIBLE
            b.btnDelete.isClickable = false
            b.btnDelete.isEnabled = false
            val spec = ProviderCatalog.byId(item.providerId)
            b.tvName.text = spec?.name ?: item.providerId

            bindStatus(item.status)
            bindChips(item)
            b.tvDetail.text = detailText(item, spec?.note.orEmpty(), spec?.configLabel.orEmpty())

            b.btnRetry.isEnabled = item.status != ProviderStatus.LOADING
            b.btnRetry.alpha = if (item.status == ProviderStatus.LOADING) 0.4f else 1f

            // 没有任何原始字段时不显示展开按钮
            val hasDetail = item.rawFields.isNotEmpty()
            b.btnExpand.visibility = if (hasDetail) View.VISIBLE else View.GONE

            applyExpanded(item, hasDetail && isExpanded(item.providerId))
        }

        fun applyExpanded(item: ProviderOutcome, expanded: Boolean) {
            b.btnExpand.setIconResource(
                if (expanded) R.drawable.ic_expand_less else R.drawable.ic_expand_more
            )
            if (!expanded) {
                // 收起时清空子 View：避免长期持有大量 TextView
                b.detailContainer.removeAllViews()
                b.detailContainer.visibility = View.GONE
                return
            }
            b.detailContainer.removeAllViews()
            b.detailContainer.visibility = View.VISIBLE

            val ctx = b.root.context
            val inflater = LayoutInflater.from(ctx)

            val header = android.widget.TextView(ctx).apply {
                text = ctx.getString(R.string.field_count, item.rawFields.size)
                setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_LabelMedium)
                setTextColor(ctx.getColor(R.color.status_neutral))
            }
            b.detailContainer.addView(header)

            item.rawFields.forEach { (key, value) ->
                val row = inflater.inflate(R.layout.item_field_row, b.detailContainer, false)
                row.findViewById<android.widget.TextView>(R.id.tvFieldKey).text = key
                row.findViewById<android.widget.TextView>(R.id.tvFieldValue).text = value
                b.detailContainer.addView(row)
            }
        }

        private fun bindStatus(status: ProviderStatus) {
            val ctx = b.root.context
            val (textRes, color) = when (status) {
                ProviderStatus.PENDING -> R.string.status_pending to ctx.getColor(R.color.status_neutral)
                ProviderStatus.LOADING -> R.string.status_loading to ctx.getColor(R.color.status_neutral)
                ProviderStatus.SUCCESS -> R.string.status_success to ctx.getColor(R.color.status_success)
                ProviderStatus.ERROR -> R.string.status_error to ctx.getColor(R.color.status_error)
                ProviderStatus.NEEDS_CONFIG -> R.string.status_needs_config to ctx.getColor(R.color.status_warn)
                ProviderStatus.SKIPPED -> R.string.status_skipped to ctx.getColor(R.color.status_neutral)
            }
            b.tvStatus.setText(textRes)
            b.tvStatus.setTextColor(color)
            // 圆角取足够大的固定值（≥ 高度的一半），保证任何字号下都是胶囊形，
            // 而不是随 chip_height 变化的圆角矩形。
            b.tvStatus.background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = ctx.resources.displayMetrics.density * 99f
                setColor(android.graphics.Color.argb(38,
                    android.graphics.Color.red(color),
                    android.graphics.Color.green(color),
                    android.graphics.Color.blue(color)))
            }
        }

        private fun bindChips(item: ProviderOutcome) {
            b.chipGroup.removeAllViews()
            // 用 LinkedHashSet 去重：来源「机房」与标记 datacenter 会重复
            val labels = linkedSetOf<String>()
            item.origin?.let { labels += it.label }
            item.flags.forEach { labels += flagLabel(it) }

            item.riskScore?.let { addChip(b.chipGroup, "风险 $it", riskColor(it)) }
            labels.forEach { addChip(b.chipGroup, it, null) }

            // 没有胶囊时隐藏，否则 ChipGroup 的 marginTop 会撑出空白
            b.chipGroup.visibility = if (b.chipGroup.childCount > 0) View.VISIBLE else View.GONE
        }

        private fun addChip(group: ChipGroup, text: String, tint: Int?) {
            val chip = Chip(
                group.context,
                null,
                com.google.android.material.R.attr.chipStyle,
            ).apply {
                this.text = text
                isCheckable = false
                isClickable = false
                setEnsureMinTouchTargetSize(false)
                // 显式压缩上下留白，避免 Chip 默认垂直内边距把它撑高
                val v = (resources.displayMetrics.density * 1).toInt()
                setPadding(paddingLeft, v, paddingRight, v)
            }
            if (tint != null) {
                chip.chipBackgroundColor = android.content.res.ColorStateList.valueOf(
                    android.graphics.Color.argb(
                        38,
                        android.graphics.Color.red(tint),
                        android.graphics.Color.green(tint),
                        android.graphics.Color.blue(tint),
                    )
                )
                chip.setTextColor(tint)
            }
            group.addView(chip)
        }
    }

    private fun riskColor(risk: Int): Int = when {
        risk >= 66 -> 0xFFD64B4B.toInt()
        risk >= 33 -> 0xFFD08A1E.toInt()
        else -> 0xFF2E9E5B.toInt()
    }

    private fun flagLabel(flag: String): String = when (flag) {
        ProviderFlag.VPN -> "VPN"
        ProviderFlag.PROXY -> "代理"
        ProviderFlag.TOR -> "Tor"
        ProviderFlag.DATACENTER -> "机房"
        ProviderFlag.ABUSER -> "滥用"
        ProviderFlag.MOBILE -> "移动网络"
        else -> flag
    }

    private fun detailText(item: ProviderOutcome, note: String, configLabel: String): String = when (item.status) {
        ProviderStatus.NEEDS_CONFIG ->
            "尚未配置 ${configLabel.ifBlank { "密钥" }}，点右侧重试或到设置中填写"
        ProviderStatus.ERROR -> item.error ?: "查询失败"
        ProviderStatus.SUCCESS -> {
            val base = listOfNotNull(item.country, item.city, item.company, item.asn)
                .distinct()
                .joinToString(" · ")
                .ifBlank { item.message.orEmpty() }
            val latency = item.latencyMs.takeIf { it in 1..120_000 }
            if (latency != null && base.isNotBlank()) "$base · ${latency} ms" else base
        }
        else -> note
    }

    /** 两条数据的差异比对规则。 */
    object Diff : DiffUtil.ItemCallback<ProviderOutcome>() {
        override fun areItemsTheSame(a: ProviderOutcome, b: ProviderOutcome) =
            a.providerId == b.providerId

        override fun areContentsTheSame(a: ProviderOutcome, b: ProviderOutcome) = a == b
    }

    private companion object {
        const val PAYLOAD_EXPAND = "expand"
    }
}
