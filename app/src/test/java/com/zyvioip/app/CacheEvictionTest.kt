package com.zyvioip.app

import com.zyvioip.app.model.ProviderResult
import com.zyvioip.app.model.ProviderStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 缓存淘汰策略（纯逻辑复刻）。
 *
 * 背景：缓存条目此前只增不减，查过的 IP 越多 DataStore 文件越大，
 * preload 时全量 JSON 解码越来越慢 —— 表现为“进页面就卡”。
 */
class CacheEvictionTest {

    private data class Entry(val key: String, val fetchedAt: Long)

    /** 复刻 evictIfNeeded 的取舍规则：按时间保留最近 N 条。 */
    private fun evict(entries: List<Entry>, max: Int, keepKey: String): List<Entry> {
        if (entries.size <= max) return entries
        val excess = entries.size - max
        val removed = entries
            .filter { it.key != keepKey }
            .sortedBy { it.fetchedAt }
            .take(excess)
            .map { it.key }
            .toSet()
        return entries.filterNot { it.key in removed }
    }

    @Test fun keepsAllWhenUnderLimit() {
        val list = (1..10).map { Entry("k$it", it.toLong()) }
        assertEquals(10, evict(list, 60, "k10").size)
    }

    @Test fun evictsOldestFirst() {
        val list = (1..70).map { Entry("k$it", it.toLong()) }
        val result = evict(list, 60, "k70")
        assertEquals(60, result.size)
        // 最旧的 10 条被清掉（k1..k10）
        assertTrue(result.none { it.key in (1..10).map { n -> "k$n" } })
        // 最新的仍在
        assertTrue(result.any { it.key == "k70" })
    }

    @Test fun neverEvictsTheJustWrittenEntry() {
        val list = (1..70).map { Entry("k$it", it.toLong()) }
        // 刚写入的 k1（时间戳最旧）也要保住
        val result = evict(list, 60, "k1")
        assertTrue("刚写入的条目不应被淘汰", result.any { it.key == "k1" })
    }

    @Test fun fetchedAtOrderIsRespected() {
        // 时间戳乱序时，按 fetchedAt 而非插入顺序淘汰
        val list = listOf(
            Entry("new", 1000),
            Entry("old", 1),
            Entry("mid", 500),
        )
        val result = evict(list, 2, "new")
        assertTrue(result.any { it.key == "new" })
        assertTrue(result.any { it.key == "mid" })
        assertTrue("最旧的应被淘汰", result.none { it.key == "old" })
    }

    @Test fun providerResultCarriesFetchedAt() {
        // 淘汰依赖 fetchedAtMillis，确认它存在且默认为当前时间
        val r = ProviderResult("x", ProviderStatus.SUCCESS)
        assertTrue(r.fetchedAtMillis > 0)
    }
}
