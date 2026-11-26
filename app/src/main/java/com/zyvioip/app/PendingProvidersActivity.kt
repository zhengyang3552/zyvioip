package com.zyvioip.app

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.zyvioip.app.databinding.ActivityPendingBinding
import com.zyvioip.app.databinding.ItemPendingProviderBinding
import com.zyvioip.app.provider.ProviderCatalog
import com.zyvioip.app.provider.ProviderConfig
import com.zyvioip.app.provider.ProviderSpec
import com.zyvioip.app.repository.HomeRepositoryApi
import com.zyvioip.app.ui.MainViewModel
import kotlinx.coroutines.launch

/** 添加供应商：列出未启用的源，按本地已保存的凭据判断是否可直接添加。 */
class PendingProvidersActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPendingBinding
    private lateinit var adapter: Adapter
    private val viewModel: MainViewModel by viewModels {
        MainViewModel.Factory((application as ZyvioIpApp).repository)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPendingBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(0, bars.top, 0, bars.bottom)
            insets
        }

        binding.toolbar.setNavigationOnClickListener { finish() }

        adapter = Adapter(
            onAdd = { spec ->
                viewModel.addProvider(spec.id)
                finish()
            },
            onConfigure = { spec ->
                startActivity(ProviderConfigActivity.intent(this, spec.id))
            },
        )
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.repository.providerStates.collect { render() }
                }
                launch {
                    viewModel.configs.collect { render() }
                }
            }
        }
    }

    private fun render() {
        val active = viewModel.repository.providerStates.value.map { it.providerId }.toSet()
        val pending = ProviderCatalog.all.filterNot { it.id in active }
        binding.tvAllAdded.visibility = if (pending.isEmpty()) View.VISIBLE else View.GONE
        binding.recyclerView.visibility = if (pending.isEmpty()) View.GONE else View.VISIBLE
        adapter.submitList(pending.map { Row(it, isConfigured(it, viewModel.configs.value[it.id])) })
    }

    private fun isConfigured(spec: ProviderSpec, config: ProviderConfig?): Boolean {
        val c = config ?: ProviderConfig(spec.id)
        val keyOk = !spec.requiresKey || c.apiKey.isNotBlank()
        val emailOk = !spec.requiresEmail || c.email.isNotBlank()
        return keyOk && emailOk
    }

    data class Row(val spec: ProviderSpec, val configured: Boolean, val adding: Boolean = false)

    private class Adapter(
        private val onAdd: (ProviderSpec) -> Unit,
        private val onConfigure: (ProviderSpec) -> Unit,
    ) : ListAdapter<Row, Adapter.VH>(Diff) {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val b = ItemPendingProviderBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return VH(b)
        }

        override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))

        inner class VH(private val b: ItemPendingProviderBinding) : RecyclerView.ViewHolder(b.root) {
            private var adding = false

            fun bind(row: Row) {
                b.tvName.text = row.spec.name
                b.tvNote.text = row.spec.note.ifBlank { "无需配置" }
                b.tvState.text = b.root.context.getString(
                    if (row.configured) R.string.pending_configured else R.string.pending_need_config
                )
                b.tvState.setTextColor(
                    b.root.context.getColor(
                        if (row.configured) R.color.status_success else R.color.status_error
                    )
                )
                b.btnAdd.isEnabled = row.configured && !adding
                b.btnAdd.text = b.root.context.getString(
                    if (adding) R.string.pending_adding else R.string.pending_add
                )
                b.btnConfigure.visibility =
                    if (row.spec.requiresKey || row.spec.requiresEmail) View.VISIBLE else View.GONE
                b.btnConfigure.setText(
                    if (row.configured) R.string.pending_reconfigure else R.string.pending_configure
                )

                b.btnAdd.setOnClickListener {
                    if (adding) return@setOnClickListener
                    adding = true
                    b.btnAdd.isEnabled = false
                    b.btnAdd.setText(R.string.pending_adding)
                    onAdd(row.spec)
                }
                b.btnConfigure.setOnClickListener { onConfigure(row.spec) }
            }
        }

        object Diff : DiffUtil.ItemCallback<Row>() {
            override fun areItemsTheSame(a: Row, b: Row) = a.spec.id == b.spec.id
            override fun areContentsTheSame(a: Row, b: Row) = a == b
        }
    }
}
