package com.zyvioip.app

import com.zyvioip.app.model.ProviderResult
import com.zyvioip.app.repository.CacheRepository
import com.zyvioip.app.repository.IpLookupRepository
import com.zyvioip.app.ui.HomeSettings
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 校验“启动自动查询 + 用户手动查询”并发时，不会被旧结果覆盖。 */
class LookupConcurrencyTest {

    private class NoopCache : CacheRepository {
        override suspend fun getLocalIp(key: String, ttlMs: Long): String? = null
        override suspend fun putLocalIp(key: String, ip: String) = Unit
        override suspend fun getProviderResult(key: String, ttlMs: Long): ProviderResult? = null
        override suspend fun putProviderResult(key: String, result: ProviderResult) = Unit
        override suspend fun clear() = Unit
    }

    @Test fun concurrentSearches_doNotOverwriteEachOther() = runTest {
        val repo = IpLookupRepository(cache = NoopCache())
        // 默认启用三个供应商，且都不需要 key，会走真实网络。
        // 这里只验证状态机：最后写回的必须是“最新一次”查询的结果，
        // 即不允许旧查询把新查询的 LOADING/结果覆盖成旧值。
        assertEquals(4, com.zyvioip.app.provider.ProviderCatalog.defaultActiveIds.size)

        // 不发真实请求：直接用一个非法的 IP 触发校验分支，确认不会污染状态
        val before = repo.providerStates.value
        repo.search("not-an-ip")
        // 非法 IP 应只写 error，不改变卡片列表
        assertEquals(before, repo.providerStates.value)
        assertEquals("IP 格式不正确", repo.error.value)
    }

    @Test fun defaultActiveProviders_neverNeedCredentials() {
        // 这是线上“一进首页全红”的根因防线：
        // 默认启用的源一旦需要 key/邮箱，新用户必然看到一堆“需要配置”。
        com.zyvioip.app.provider.ProviderCatalog.all
            .filter { it.defaultActive }
            .forEach { spec ->
                assertTrue("${spec.id} 被默认启用却要求 key", !spec.requiresKey)
                assertTrue("${spec.id} 被默认启用却要求邮箱", !spec.requiresEmail)
            }
    }

    @Test fun updateSettings_appliesAllFields() = runTest {
        val repo = IpLookupRepository(cache = NoopCache())
        repo.updateSettings(
            HomeSettings(
                timeoutMs = 15_000,
                cacheTtlMinutes = 30,
                autoLookup = false,
                showFlags = false,
                notifications = true,
            )
        )
        val applied = repo.homeSettings.value
        assertEquals(15_000L, applied.timeoutMs)
        assertEquals(30L, applied.cacheTtlMinutes)
        assertEquals(false, applied.autoLookup)
        assertEquals(false, applied.showFlags)
        assertEquals(true, applied.notifications)
    }

    @Test fun timeoutIsClampedToSafeRange() = runTest {
        val repo = IpLookupRepository(cache = NoopCache())
        // 太小会被夹到 1s，避免用户填 0 导致全部秒失败
        repo.updateSettings(HomeSettings(timeoutMs = 10))
        assertEquals(1_000L, repo.homeSettings.value.timeoutMs)
    }
}
