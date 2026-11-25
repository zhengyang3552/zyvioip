package com.zyvioip.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.zyvioip.app.ui.HomeRoute
import com.zyvioip.app.ui.theme.ZyvioIpTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            // 不要再额外包一层 MaterialTheme()：
            // 外层默认 MaterialTheme 会用默认配色覆盖掉 ZyvioIpTheme 里的品牌色，
            // 导致动态取色 / 自定义深浅色实际不生效。
            ZyvioIpTheme {
                HomeRoute()
            }
        }
    }
}
