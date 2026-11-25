package com.zyvioip.app

import android.app.Application
import com.zyvioip.app.ui.HomeRepositoryApi

/** The core slice supplies [repository]; keep it injectable for tests. */
class ZyvioIpApp : Application() {
    lateinit var repository: HomeRepositoryApi
        private set
}
