package com.zyvioip.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import com.zyvioip.app.ui.HomeRoute
import com.zyvioip.app.ui.theme.ZyvioIpTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ZyvioIpTheme {
                MaterialTheme {
                    HomeRoute()
                }
            }
        }
    }
}
