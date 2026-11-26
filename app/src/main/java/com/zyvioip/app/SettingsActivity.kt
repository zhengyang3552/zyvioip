package com.zyvioip.app

import android.os.Bundle
import android.text.InputType
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.textfield.TextInputEditText
import com.zyvioip.app.databinding.ActivitySettingsBinding
import com.zyvioip.app.provider.ProviderConfig
import com.zyvioip.app.repository.HomeSettings
import com.zyvioip.app.ui.MainViewModel
import com.zyvioip.app.ui.SharedViewModel
import kotlinx.coroutines.launch

/**
 * 设置：全局偏好 + 供应商凭据。
 *
 * 凭据输入区是**固定 XML 布局**，不再运行时动态创建 TextInputLayout ——
 * 后者要 new 十几个控件并 addView，是「点设置卡一下」的来源之一。
 */
class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding

    private val viewModel: MainViewModel by lazy {
        SharedViewModel.get((application as ZyvioIpApp).repository)
    }

    /** providerId -> (输入框, 保存按钮, 是否邮箱) */
    private data class Field(
        val providerId: String,
        val input: TextInputEditText,
        val isEmail: Boolean,
    )

    private val fields = mutableListOf<Field>()
    private var currentSettings: HomeSettings? = null
    private var reveal = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(0, bars.top, 0, bars.bottom)
            insets
        }

        binding.toolbar.setNavigationOnClickListener { finish() }

        collectFields()

        binding.switchReveal.setOnCheckedChangeListener { _, checked ->
            reveal = checked
            applyReveal()
        }
        binding.btnClearCache.setOnClickListener { viewModel.clearCache() }

        binding.switchAutoLookup.setOnCheckedChangeListener { _, v -> commit { it.copy(autoLookup = v) } }
        binding.switchShowFlags.setOnCheckedChangeListener { _, v -> commit { it.copy(showFlags = v) } }
        binding.switchNotifications.setOnCheckedChangeListener { _, v -> commit { it.copy(notifications = v) } }

        binding.inputTimeout.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) binding.inputTimeout.text?.toString()?.toLongOrNull()
                ?.takeIf { it > 0 }?.let { sec -> commit { it.copy(timeoutMs = sec * 1000) } }
        }
        binding.inputTtl.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) binding.inputTtl.text?.toString()?.toLongOrNull()
                ?.takeIf { it >= 0 }?.let { min -> commit { it.copy(cacheTtlMinutes = min) } }
        }

        observeState()
    }

    /** 把 XML 里的固定控件登记为可保存的字段。 */
    private fun collectFields() {
        add("getipintel", binding.inputGetIpIntel, binding.btnSaveGetIpIntel, isEmail = true)
        add("ipdatafree", binding.inputIpDataCo, binding.btnSaveIpDataCo, isEmail = false)
        add("ipapisis", binding.inputIpapiIs, binding.btnSaveIpapiIs, isEmail = false)
        add("abuseipdb", binding.inputAbuseIpdb, binding.btnSaveAbuseIpdb, isEmail = false)
        add("ipqs", binding.inputIpqs, binding.btnSaveIpqs, isEmail = false)
        add("apivoid", binding.inputApiVoid, binding.btnSaveApiVoid, isEmail = false)
        add("ipdata", binding.inputIpData, binding.btnSaveIpData, isEmail = false)
        add("ipgeolocation", binding.inputIpGeo, binding.btnSaveIpGeo, isEmail = false)
        add("whoisxml", binding.inputWhoisXml, binding.btnSaveWhoisXml, isEmail = false)
    }

    private fun add(
        providerId: String,
        input: TextInputEditText,
        button: com.google.android.material.button.MaterialButton,
        isEmail: Boolean,
    ) {
        fields += Field(providerId, input, isEmail)
        button.setOnClickListener { save(providerId) }
        input.addTextChangedListener(simpleWatcher { button.isEnabled = true })
    }

    private fun simpleWatcher(onChanged: () -> Unit) = object : android.text.TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
        override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = onChanged()
        override fun afterTextChanged(s: android.text.Editable?) = Unit
    }

    private fun save(providerId: String) {
        val apiKey = fields.firstOrNull { it.providerId == providerId && !it.isEmail }
            ?.input?.text?.toString()?.trim().orEmpty()
        val email = fields.firstOrNull { it.providerId == providerId && it.isEmail }
            ?.input?.text?.toString()?.trim().orEmpty()
        viewModel.saveConfig(providerId, ProviderConfig(providerId, apiKey, email))
    }

    private fun observeState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.repository.homeSettings.collect { s ->
                        currentSettings = s
                        binding.inputTimeout.setText((s.timeoutMs / 1000).toString())
                        binding.inputTtl.setText(s.cacheTtlMinutes.toString())
                        binding.switchAutoLookup.isChecked = s.autoLookup
                        binding.switchShowFlags.isChecked = s.showFlags
                        binding.switchNotifications.isChecked = s.notifications
                        applyReveal()
                    }
                }
                launch {
                    viewModel.configs.collect { configs ->
                        fields.forEach { f ->
                            val c = configs[f.providerId] ?: return@forEach
                            val want = if (f.isEmail) c.email else c.apiKey
                            if (f.input.text.isNullOrBlank() && want.isNotBlank()) {
                                f.input.setText(want)
                            }
                        }
                    }
                }
            }
        }
    }

    /** 密钥与邮箱统一受「显示明文」开关控制。 */
    private fun applyReveal() {
        fields.forEach { f ->
            f.input.inputType = when {
                f.isEmail && reveal -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
                f.isEmail -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                reveal -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                else -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            }
            f.input.setSelection(f.input.text?.length ?: 0)
        }
        binding.tvRevealState.setText(
            if (reveal) R.string.settings_reveal_on else R.string.settings_reveal_off
        )
    }

    private fun commit(transform: (HomeSettings) -> HomeSettings) {
        val current = currentSettings ?: return
        viewModel.updateSettings(transform(current))
    }
}
