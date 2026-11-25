package com.zyvioip.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColors = darkColorScheme(
    primary = Color(0xFF9BB8FF),
    onPrimary = Color(0xFF12203A),
    secondary = Color(0xFFC7C2D9),
    surface = Color(0xFF15171C),
    background = Color(0xFF0E1014),
)
private val LightColors = lightColorScheme(
    primary = Color(0xFF405EBD),
    onPrimary = Color.White,
    secondary = Color(0xFF625B71),
    surface = Color(0xFFF7F8FC),
    background = Color(0xFFF2F3F7),
)

@Composable
fun ZyvioIpTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colors = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> DarkColors
        else -> LightColors
    }
    // 状态栏/导航栏交给 enableEdgeToEdge() + insets 处理。
    // 原来在这里改 window.statusBarColor，Android 15 已废弃且不生效。
    MaterialTheme(colorScheme = colors, content = content)
}
