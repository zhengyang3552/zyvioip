package com.zyvioip.app

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
import com.zyvioip.app.databinding.ActivityMainBinding
import com.zyvioip.app.model.AggregatedResult
import com.zyvioip.app.provider.ProviderCatalog
import com.zyvioip.app.provider.ProviderOutcome
import com.zyvioip.app.repository.NetworkWatcher
import com.zyvioip.app.repository.flags
import com.zyvioip.app.ui.MainViewModel
import com.zyvioip.app.ui.ProviderAdapter
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var adapter: ProviderAdapter

    private val viewModel: MainViewModel by viewModels {
        MainViewModel.Factory((application as ZyvioIpApp).repository)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        applyInsets()
        setupRecyclerView()
        setupActions(savedInstanceState)
        observeState()
        observeNetwork()
    }

    private fun applyInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(0, bars.top, 0, bars.bottom)
            insets
        }
    }

    private fun setupRecyclerView() {
        adapter = ProviderAdapter(
            onRetry = { id -> viewModel.retry(id) },
            onRemove = { id -> removeWithUndo(id) },
        )
        binding.recyclerView.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = this@MainActivity.adapter
            setHasFixedSize(false)
            itemAnimator = DefaultItemAnimator().apply {
                // 关闭「内容变化」动画。每次查询后 providerStates 都是新 List，
                // 若保留 change 动画会让整行闪烁 —— 与 ZYNotificationHistory 同样的处理。
                supportsChangeAnimations = false
            }
        }

        val touch = object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.END) {
            override fun onMove(
                rv: RecyclerView, vh: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder,
            ) = false

            override fun onSwiped(vh: RecyclerView.ViewHolder, direction: Int) {
                val pos = vh.bindingAdapterPosition
                if (pos == RecyclerView.NO_POSITION) return
                val item = adapter.currentList.getOrNull(pos) ?: return
                // 先恢复视图（ListAdapter 会因数据变化重建），再执行删除
                adapter.notifyItemChanged(pos)
                removeWithUndo(item.providerId)
            }
        }
        ItemTouchHelper(touch).attachToRecyclerView(binding.recyclerView)
    }

    private fun setupActions(savedInstanceState: Bundle?) {
        binding.btnSearch.setOnClickListener { doSearch() }
        binding.inputIp.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                doSearch()
                true
            } else {
                false
            }
        }
        binding.btnRefreshIp.setOnClickListener { viewModel.refreshLocalIp(forceQuery = true) }
        binding.tvLocalIp.setOnClickListener {
            val ip = it.tag as? String ?: return@setOnClickListener
            binding.inputIp.setText(ip)
            viewModel.search(ip)
        }
        binding.btnAddProvider.setOnClickListener {
            startActivity(Intent(this, PendingProvidersActivity::class.java))
        }
        binding.fabSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
    }

    private fun doSearch() {
        val value = binding.inputIp.text?.toString()?.trim().orEmpty()
        if (value.isEmpty()) return
        binding.inputIp.clearFocus()
        viewModel.search(value)
    }

    private fun removeWithUndo(providerId: String) {
        val name = ProviderCatalog.byId(providerId)?.name ?: providerId
        adapter.forgetExpanded(providerId)
        viewModel.removeProvider(providerId)
        Snackbar.make(
            binding.root,
            getString(R.string.msg_removed, name),
            Snackbar.LENGTH_SHORT,
        ).setAction(R.string.action_undo) {
            viewModel.addProvider(providerId)
        }.show()
    }

    /**
     * 统一的状态收集入口。
     *
     * 每个流各自 render，互不牵连；列表更新走 submitList，由 DiffUtil 决定重绘范围。
     */
    private fun observeState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.repository.providerStates.collect { renderList(it) }
                }
                launch {
                    viewModel.repository.aggregate.collect { renderSummary(it) }
                }
                launch {
                    viewModel.repository.localIp.collect { renderLocalIp(it) }
                }
                launch {
                    viewModel.repository.loading.collect { renderLoading(it) }
                }
                launch {
                    viewModel.refreshingIp.collect { renderRefreshing(it) }
                }
                launch {
                    viewModel.repository.error.collect { showError(it) }
                }
                launch {
                    viewModel.startupError.collect { showError(it) }
                }
            }
        }
    }

    private fun observeNetwork() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                NetworkWatcher(applicationContext).changes()
                    .distinctUntilChanged()
                    .collect { viewModel.onNetworkChanged() }
            }
        }
    }

    private fun renderList(list: List<ProviderOutcome>) {
        adapter.submitList(list)
        val empty = list.isEmpty()
        binding.tvHint.visibility = if (empty) View.GONE else View.VISIBLE
        binding.recyclerView.visibility = if (empty) View.GONE else View.VISIBLE
    }

    private fun renderLoading(loading: Boolean) {
        binding.loadingRow.visibility = if (loading) View.VISIBLE else View.GONE
        binding.btnSearch.isEnabled = !loading
    }

    private fun renderRefreshing(refreshing: Boolean) {
        binding.progressIp.visibility = if (refreshing) View.VISIBLE else View.GONE
        binding.btnRefreshIp.visibility = if (refreshing) View.GONE else View.VISIBLE
    }

    private fun renderLocalIp(ip: String?) {
        if (ip.isNullOrBlank()) {
            binding.tvLocalIp.visibility = View.GONE
            return
        }
        binding.tvLocalIp.visibility = View.VISIBLE
        binding.tvLocalIp.text = getString(R.string.label_local_ip, ip)
        binding.tvLocalIp.tag = ip
    }

    private fun renderSummary(result: AggregatedResult?) {
        val card = binding.summary.summaryCard
        if (result == null) {
            card.visibility = View.GONE
            return
        }
        card.visibility = View.VISIBLE
        val s = binding.summary

        s.tvIp.text = result.ip
        s.tvFlag.text = countryFlagFromCode(result.signals.countryCode)
        s.tvRiskScore.text = result.riskScore?.toString() ?: "--"
        s.tvRiskScore.setTextColor(riskColor(result.riskScore))

        val place = listOfNotNull(result.signals.country, result.signals.city)
            .joinToString(" · ")
            .ifBlank { "位置未知" }
        s.tvPlace.text = "$place · ${result.type.label}"

        // 来源与标记：全部用胶囊
        s.chipGroup.removeAllViews()
        result.origin?.let { addPill(s.chipGroup, it.origin.label, emphasized = true) }
        result.originVerdicts.map { it.source }.distinct().forEach {
            addPill(s.chipGroup, it, emphasized = false)
        }
        result.flags.forEach { addPill(s.chipGroup, flagLabel(it), emphasized = false) }

        val hint = result.origin?.origin?.hint
        s.tvOriginHint.visibility = if (hint.isNullOrBlank()) View.GONE else View.VISIBLE
        s.tvOriginHint.text = hint

        val riskDetail = result.signals.riskScores.entries
            .joinToString("   ") { "${it.key} ${it.value}" }
        s.tvRiskDetail.visibility = if (riskDetail.isBlank()) View.GONE else View.VISIBLE
        s.tvRiskDetail.text = riskDetail

        val succeeded = result.providerStates.count { it.outcome.succeeded }
        val failed = result.providerStates.count {
            it.outcome.status == com.zyvioip.app.provider.ProviderStatus.ERROR ||
                it.outcome.status == com.zyvioip.app.provider.ProviderStatus.NEEDS_CONFIG
        }
        val skipped = result.providerStates.count {
            it.outcome.status == com.zyvioip.app.provider.ProviderStatus.SKIPPED
        }
        s.tvSummaryLine.text = buildString {
            append("成功 $succeeded")
            if (failed > 0) append(" / 未成功 $failed")
            if (skipped > 0) append(" / 跳过 $skipped")
            if (result.scoredProviderCount > 0) append(" · ${result.scoredProviderCount} 个源参与评分")
        }
        s.tvNoScore.visibility = if (result.riskScore == null) View.VISIBLE else View.GONE
    }

    private fun addPill(group: com.google.android.material.chip.ChipGroup, text: String, emphasized: Boolean) {
        val chip = com.google.android.material.chip.Chip(group.context).apply {
            this.text = text
            isCheckable = false
            isClickable = false
            chipMinHeight = group.resources.getDimension(R.dimen.chip_height)
            setEnsureMinTouchTargetSize(false)
        }
        if (emphasized) {
            // 用主题属性而不是具体色值，深浅色自动适配
            val attrs = intArrayOf(com.google.android.material.R.attr.colorPrimaryContainer)
            val ta = group.context.obtainStyledAttributes(attrs)
            val color = ta.getColor(0, 0xFF3F5FBF.toInt())
            ta.recycle()
            chip.chipBackgroundColor = android.content.res.ColorStateList.valueOf(color)
        }
        group.addView(chip)
    }

    private fun showError(message: String?) {
        if (message.isNullOrBlank()) return
        Snackbar.make(binding.root, message, Snackbar.LENGTH_SHORT).show()
    }

    private fun riskColor(risk: Int?): Int = when {
        risk == null -> getColor(R.color.status_neutral)
        risk >= 66 -> getColor(R.color.status_error)
        risk >= 33 -> getColor(R.color.status_warn)
        else -> getColor(R.color.status_success)
    }

    private fun flagLabel(flag: String): String = when (flag) {
        com.zyvioip.app.provider.ProviderFlag.VPN -> "VPN"
        com.zyvioip.app.provider.ProviderFlag.PROXY -> "代理"
        com.zyvioip.app.provider.ProviderFlag.TOR -> "Tor"
        com.zyvioip.app.provider.ProviderFlag.DATACENTER -> "机房"
        com.zyvioip.app.provider.ProviderFlag.ABUSER -> "滥用"
        com.zyvioip.app.provider.ProviderFlag.MOBILE -> "移动网络"
        else -> flag
    }

    private fun countryFlagFromCode(code: String?): String {
        if (code == null || code.length != 2) return "🌐"
        val upper = code.uppercase()
        if (!upper.all { it in 'A'..'Z' }) return "🌐"
        return buildString {
            append(Character.toChars(0x1F1E6 + (upper[0] - 'A')))
            append(Character.toChars(0x1F1E6 + (upper[1] - 'A')))
        }
    }
}
