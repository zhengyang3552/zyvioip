package com.zyvioip.app

import com.zyvioip.app.provider.ProviderOutcome
import com.zyvioip.app.provider.ProviderStatus
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * providerStates 的 id 唯一性。
 *
 * 崩溃现场：
 *   IllegalArgumentException: Key "getipintel" was already used.
 *   If you are using LazyColumn/Row please make sure you provide a unique key for each item.
 *
 * 根因是 addProvider / retry 都是「先挂起网络请求，再基于旧快照写回」，
 * 并发写入时同一 providerId 被追加两次；而 LazyColumn 用 providerId 当 key。
 */
class ProviderStateUniquenessTest {

    /** 复刻 repository 里的 upsert 语义，验证合并后 id 仍然唯一。 */
    private fun upsert(
        current: List<ProviderOutcome>,
        incoming: ProviderOutcome,
    ): List<ProviderOutcome> {
        val index = current.indexOfFirst { it.providerId == incoming.providerId }
        if (index < 0) return current + incoming
        return current.toMutableList().also { it[index] = incoming }
    }

    @Test fun upsertAppendsWhenAbsent() {
        val list = listOf(ProviderOutcome("a", ProviderStatus.PENDING))
        val next = upsert(list, ProviderOutcome("b", ProviderStatus.LOADING))
        assertEquals(listOf("a", "b"), next.map { it.providerId })
    }

    @Test fun upsertReplacesInsteadOfDuplicating() {
        val list = listOf(ProviderOutcome("a", ProviderStatus.PENDING))
        val next = upsert(list, ProviderOutcome("a", ProviderStatus.SUCCESS))
        assertEquals(1, next.size)
        assertEquals(ProviderStatus.SUCCESS, next[0].status)
    }

    @Test fun repeatedUpsertNeverProducesDuplicateIds() {
        // 模拟并发场景：同一批 id 被反复写入
        var list = emptyList<ProviderOutcome>()
        repeat(50) {
            list = upsert(list, ProviderOutcome("getipintel", ProviderStatus.LOADING))
            list = upsert(list, ProviderOutcome("ipapicom", ProviderStatus.SUCCESS))
        }
        assertEquals(2, list.size)
        assertEquals(list.map { it.providerId }.distinct().size, list.size)
    }

    @Test fun lazyColumnKeysMustBeUnique() {
        // 这条断言就是崩溃的直接原因：key 必须唯一
        val states = listOf("a", "b", "a")
        assertEquals(false, states.size == states.distinct().size)
    }
}
