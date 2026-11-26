package com.zyvioip.app

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.zyvioip.app.databinding.ActivityProviderConfigBinding
import com.zyvioip.app.provider.ProviderCatalog
import com.zyvioip.app.provider.ProviderConfig
import com.zyvioip.app.ui.MainViewModel
import com.zyvioip.app.ui.SharedViewModel
import kotlinx.coroutines.launch

/** 单个供应商的配置表单。 */
class ProviderConfigActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProviderConfigBinding
    /**
     * 与 MainActivity 共用同一个 ViewModel 实例。
     *
     * 之前用默认作用域（Activity），每次进入设置页都会新建一个 ViewModel，
     * 其 init 会重新执行 initialize()：本机 IP 探测 + 全源查询，
     * 所以「点设置会卡一下再跳转」。
     * 改为 application 作用域后复用主界面已加载好的状态。
     */
    /** 与主界面共用同一实例，避免每次进入都重新 initialize()。 */
    private val viewModel: MainViewModel by lazy {
        SharedViewModel.get((application as ZyvioIpApp).repository)
    }
    private val providerId: String by lazy { intent.getStringExtra(EXTRA_ID).orEmpty() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProviderConfigBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(0, bars.top, 0, bars.bottom)
            insets
        }

        val spec = ProviderCatalog.byId(providerId)
        binding.toolbar.title = spec?.name ?: providerId
        binding.toolbar.setNavigationOnClickListener { finish() }

        binding.layoutKey.visibility = if (spec?.requiresKey == true) View.VISIBLE else View.GONE
        binding.layoutEmail.visibility = if (spec?.requiresEmail == true) View.VISIBLE else View.GONE
        binding.tvNoConfig.visibility =
            if (spec?.requiresKey != true && spec?.requiresEmail != true) View.VISIBLE else View.GONE
        binding.layoutKey.hint = spec?.configLabel?.ifBlank { getString(R.string.config_key) }
            ?: getString(R.string.config_key)
        binding.layoutEmail.hint = getString(R.string.config_email)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.configs.collect { configs ->
                    val config = configs[providerId] ?: ProviderConfig(providerId)
                    if (binding.inputKey.text.isNullOrBlank()) binding.inputKey.setText(config.apiKey)
                    if (binding.inputEmail.text.isNullOrBlank()) binding.inputEmail.setText(config.email)
                }
            }
        }

        binding.btnSave.setOnClickListener {
            viewModel.saveConfig(
                providerId,
                ProviderConfig(
                    providerId = providerId,
                    apiKey = binding.inputKey.text?.toString()?.trim().orEmpty(),
                    email = binding.inputEmail.text?.toString()?.trim().orEmpty(),
                ),
            )
            finish()
        }
    }

    companion object {
        private const val EXTRA_ID = "provider_id"
        fun intent(context: Context, providerId: String): Intent =
            Intent(context, ProviderConfigActivity::class.java).putExtra(EXTRA_ID, providerId)
    }
}
