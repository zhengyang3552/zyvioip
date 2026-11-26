package com.zyvioip.app

import android.os.Bundle
import android.text.InputType
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.zyvioip.app.databinding.ActivitySettingsBinding
import com.zyvioip.app.provider.ProviderCatalog
import com.zyvioip.app.provider.ProviderConfig
import com.zyvioip.app.repository.HomeSettings
import com.zyvioip.app.ui.MainViewModel
import com.zyvioip.app.ui.SharedViewModel
import kotlinx.coroutines.launch

/** 设置：全局偏好 + 供应商凭据。 */
class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
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

    /** 每个供应商的输入控件，便于统一切换明文/遮挡。 */
    private data class KeyField(
        val providerId: String,
        val layout: TextInputLayout,
        val input: TextInputEditText,
        val isEmail: Boolean,
    )

    private val keyFields = mutableListOf<KeyField>()
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
        buildKeyFields()

        binding.switchReveal.setOnCheckedChangeListener { _, checked ->
            reveal = checked
            applyReveal()
        }

        binding.btnClearCache.setOnClickListener { viewModel.clearCache() }

        binding.switchAutoLookup.setOnCheckedChangeListener { _, v -> commit { it.copy(autoLookup = v) } }
        binding.switchShowFlags.setOnCheckedChangeListener { _, v -> commit { it.copy(showFlags = v) } }
        binding.switchNotifications.setOnCheckedChangeListener { _, v -> commit { it.copy(notifications = v) } }

        // 数字输入：只在失去焦点时落盘，避免边输入边写
        binding.inputTimeout.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                binding.inputTimeout.text?.toString()?.toLongOrNull()
                    ?.takeIf { it > 0 }
                    ?.let { sec -> commit { it.copy(timeoutMs = sec * 1000) } }
            }
        }
        binding.inputTtl.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                binding.inputTtl.text?.toString()?.toLongOrNull()
                    ?.takeIf { it >= 0 }
                    ?.let { min -> commit { it.copy(cacheTtlMinutes = min) } }
            }
        }

        observeState()
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
                        keyFields.forEach { f ->
                            val config = configs[f.providerId] ?: return@forEach
                            val current = f.input.text?.toString().orEmpty()
                            val want = if (f.isEmail) config.email else config.apiKey
                            // 只在未编辑时回填，避免覆盖用户正在输入的内容
                            if (current.isBlank() && want.isNotBlank()) f.input.setText(want)
                        }
                    }
                }
            }
        }
    }

    private fun buildKeyFields() {
        ProviderCatalog.all.filter { it.requiresKey || it.requiresEmail }.forEach { spec ->
            if (spec.requiresKey) {
                keyFields += addField(spec.id, "${spec.name} ${spec.configLabel.ifBlank { getString(R.string.config_key) }}", false)
            }
            if (spec.requiresEmail) {
                keyFields += addField(spec.id, "${spec.name} ${getString(R.string.config_email)}", true)
            }
        }
    }

    private fun addField(providerId: String, hint: String, isEmail: Boolean): KeyField {
        val ctx = this
        val row = android.widget.LinearLayout(ctx).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = resources.getDimensionPixelSize(R.dimen.space_sm) }
        }
        val layout = TextInputLayout(
            ctx, null,
            com.google.android.material.R.attr.textInputOutlinedStyle,
        ).apply {
            this.hint = hint
            layoutParams = android.widget.LinearLayout.LayoutParams(0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            endIconMode = if (isEmail) TextInputLayout.END_ICON_NONE else TextInputLayout.END_ICON_PASSWORD_TOGGLE
        }
        val input = TextInputEditText(ctx).apply {
            inputType = if (isEmail) InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
            else InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            maxLines = 1
        }
        layout.addView(input)

        val save = com.google.android.material.button.MaterialButton(ctx).apply {
            setText(R.string.action_save)
            isEnabled = false
        }
        save.setOnClickListener {
            saveField(providerId)
            save.isEnabled = false
        }
        input.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {
                save.isEnabled = true
            }
            override fun afterTextChanged(s: android.text.Editable?) = Unit
        })

        row.addView(layout)
        row.addView(save, android.widget.LinearLayout.LayoutParams(
            android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
            android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
        ).apply { marginStart = resources.getDimensionPixelSize(R.dimen.space_sm) })

        binding.keyContainer.addView(row)
        return KeyField(providerId, layout, input, isEmail)
    }

    private fun saveField(providerId: String) {
        val existing = viewModel.configs.value[providerId] ?: ProviderConfig(providerId)
        val apiKey = keyFields.firstOrNull { it.providerId == providerId && !it.isEmail }
            ?.input?.text?.toString()?.trim() ?: existing.apiKey
        val email = keyFields.firstOrNull { it.providerId == providerId && it.isEmail }
            ?.input?.text?.toString()?.trim() ?: existing.email
        viewModel.saveConfig(providerId, ProviderConfig(providerId, apiKey, email))
    }

    /** 统一应用「显示明文」开关：密钥与邮箱都要打码。 */
    private fun applyReveal() {
        keyFields.forEach { f ->
            f.input.inputType = when {
                f.isEmail && reveal -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
                f.isEmail -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                reveal -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                else -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            }
            // 保持光标位置
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
