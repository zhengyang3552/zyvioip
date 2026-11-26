package com.zyvioip.app

import android.app.Application
import com.zyvioip.app.repository.DataStoreCacheRepository
import com.zyvioip.app.repository.IpLookupRepository
import com.zyvioip.app.repository.SettingsRepository

/**
 * Application container for the repository.
 *
 * The repository is created lazily from the application context, so it is available even when
 * Android restores an Activity before any explicit initialization code has run. Tests may replace
 * it with a fake.
 */
class ZyvioIpApp : Application() {
    private val defaultRepository: HomeRepositoryApi by lazy {
        IpLookupRepository(
            cache = DataStoreCacheRepository(this),
            settingsRepository = SettingsRepository(this),
        )
    }

    @Volatile
    private var injectedRepository: HomeRepositoryApi? = null

    var repository: HomeRepositoryApi
        get() = injectedRepository ?: defaultRepository
        set(value) {
            injectedRepository = value
        }
}
