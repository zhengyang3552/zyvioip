package com.zyvioip.app

import com.zyvioip.app.model.IpSignals
import com.zyvioip.app.model.ProviderResult
import com.zyvioip.app.model.ProviderStatus
import com.zyvioip.app.model.mergeSignals
import com.zyvioip.app.repository.LocalIpRepository
import com.zyvioip.app.repository.MemoryCacheRepository
import com.zyvioip.app.repository.ProviderCatalog
import com.zyvioip.app.repository.aggregatePurity
import com.zyvioip.app.util.averagePurity
import com.zyvioip.app.util.classifyIpType
import com.zyvioip.app.util.countryFlag
import com.zyvioip.app.util.isValidIp
import com.zyvioip.app.util.parseFlags
import com.zyvioip.app.util.riskToPurity
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Spec section 14: JVM unit tests for all pure core logic. */
class CoreLogicTest {

    @Test fun validator_acceptsIpv4AndIpv6_rejectsHostsAndCidr() {
        assertTrue(isValidIp("1.1.1.1"))
        assertTrue(isValidIp("0.0.0.0"))
        assertTrue(isValidIp("255.255.255.255"))
        assertTrue(isValidIp("2001:4860:4860::8888"))
        assertTrue(isValidIp("::1"))
        assertFalse(isValidIp("256.1.1.1"))
        assertFalse(isValidIp("1.2.3"))
        assertFalse(isValidIp("example.com"))
        assertFalse(isValidIp("1.1.1.1/24"))
        assertFalse(isValidIp(""))
        assertFalse(isValidIp(null))
        assertFalse(isValidIp("fe80::1%eth0"))
    }

    @Test fun normalizer_convertsRiskScalesAndClamps() {
        assertEquals(90, riskToPurity(10))
        assertEquals(95, riskToPurity(0.05))
        assertEquals(100, riskToPurity(-1))
        assertEquals(0, riskToPurity(150))
        assertNull(riskToPurity(null))
    }

    @Test fun average_onlySuccessWithNonNullPurity() {
        assertEquals(80, averagePurity(listOf(90, 70, null, 80)))
        assertNull(averagePurity(listOf(null)))
        val results = listOf(
            ProviderResult("a", ProviderStatus.SUCCESS, purity = 90),
            ProviderResult("b", ProviderStatus.ERROR, purity = 0),
            ProviderResult("c", ProviderStatus.SUCCESS),
            ProviderResult("d", ProviderStatus.SUCCESS, purity = 70),
        )
        assertEquals(80, aggregatePurity(results))
    }

    @Test fun classifier_followsPriority() {
        assertTrue(classifyIpType(IpSignals(isTor = true, isVpn = true, isProxy = true)).name == "TOR")
        // VPN 优先级高于代理：同时命中时归为 VPN
        assertEquals("VPN", classifyIpType(IpSignals(isProxy = true, isVpn = true)).name)
        assertTrue(classifyIpType(IpSignals(isVpn = true, isDatacenter = true)).name == "VPN")
        assertTrue(classifyIpType(IpSignals(isDatacenter = true, isMobile = true)).name == "DATACENTER")
        assertTrue(classifyIpType(IpSignals(isMobile = true)).name == "MOBILE")
        assertTrue(classifyIpType(IpSignals()).name == "UNKNOWN")
        assertEquals("PROXY", classifyIpType(IpSignals(flags = setOf("proxy"))).name)
    }

    @Test fun classifier_prefersDatacenterOverProxy() {
        // 8.8.8.8 这类 Google 机房 IP，ip-api.com 同时给 hosting=true 和 proxy=true。
        // 若代理优先就会把“机房”误判成“代理”，所以机房必须排在代理之前。
        assertEquals(
            "DATACENTER",
            classifyIpType(IpSignals(isDatacenter = true, isProxy = true)).name,
        )
        assertEquals(
            "DATACENTER",
            classifyIpType(IpSignals(flags = setOf("datacenter", "proxy"))).name,
        )
        // VPN 仍高于机房
        assertEquals(
            "VPN",
            classifyIpType(IpSignals(isVpn = true, isDatacenter = true, isProxy = true)).name,
        )
    }

    @Test fun classifier_residentialOnlyWhenNoRiskSignal() {
        assertEquals("RESIDENTIAL", classifyIpType(IpSignals(isResidential = true)).name)
        // 一旦有风险信号，住宅不成立
        assertEquals(
            "DATACENTER",
            classifyIpType(IpSignals(isResidential = true, isDatacenter = true)).name,
        )
    }

    @Test fun flagEmoji_andFlagParsing() {
        assertEquals("\uD83C\uDDFA\uD83C\uDDF8", countryFlag("US"))
        assertEquals("\uD83C\uDDE8\uD83C\uDDF3", countryFlag("CN"))
        assertEquals("", countryFlag("USA"))
        assertEquals("", countryFlag(null))
        assertEquals(setOf("vpn", "proxy", "tor"), parseFlags("VPN, Proxy,tor"))
        assertTrue(parseFlags(null).isEmpty())
    }

    @Test fun catalog_providerCountAndDefaultActiveAreConsistent() {
        // 数量断言是有意保留的：防止合并时误删 provider。
        // 新增/移除源时请同步更新这里。
        assertEquals(15, ProviderCatalog.all.size)
        // GetIPIntel 强制要求联系邮箱（否则返回 -6），ipdata.co 无 key 必然 401，
        // 两者都不再默认启用；默认启用的 6 个源全部免密钥。
        val expectedActive = setOf(
            "ipapicom", "ipwhois", "ippure", "ipapisanon", "iptoto", "proxycheck",
        )
        assertEquals(expectedActive, ProviderCatalog.defaultActiveIds)
        assertEquals(com.zyvioip.app.provider.ProviderAuth.EMAIL, ProviderCatalog.byId("getipintel")!!.auth)
        // ipdata.co 现在必须配置 key
        assertTrue(ProviderCatalog.byId("ipdatafree")!!.requiresKey)
        assertFalse(ProviderCatalog.byId("ipdatafree")!!.defaultActive)
        // 新源免 key 且默认启用
        assertFalse(ProviderCatalog.byId("ipwhois")!!.requiresKey)
        assertTrue(ProviderCatalog.byId("ipwhois")!!.defaultActive)
        // 免费 ipapi.is 端点不提供评分
        assertFalse(ProviderCatalog.byId("ipapisanon")!!.scorable)
        // 默认启用的源里，每个都必须是“无 key 也能跑”的
        ProviderCatalog.all.filter { it.defaultActive }.forEach { spec ->
            assertFalse("默认启用的 ${spec.id} 不应该要求 key", spec.requiresKey)
            assertFalse("默认启用的 ${spec.id} 不应该要求邮箱", spec.requiresEmail)
        }
        assertEquals("联系邮箱", ProviderCatalog.byId("getipintel")!!.configLabel)
        assertNull(ProviderCatalog.byId("NOPE"))
    }

    @Test fun mergeSignals_keepsNonNullAndOrsBooleans() = runTest {
        val a = IpSignals(ip = "1.1.1.1", country = "US", isVpn = false, city = null)
        val b = IpSignals(city = "Paris", isVpn = true, country = null)
        val merged = mergeSignals(a, b)
        assertEquals("1.1.1.1", merged.ip)
        assertEquals("US", merged.country)
        assertEquals("Paris", merged.city)
        assertTrue(merged.isVpn!!)
    }

    @Test fun cache_ttlBypassAndRemoveReAdd() = runTest {
        val cache = MemoryCacheRepository()
        cache.putLocalIp("device", "8.8.8.8")
        assertEquals("8.8.8.8", cache.getLocalIp("device", 5_000))
        assertNull(cache.getLocalIp("device", -1))
        val result = ProviderResult("IP_API_COM", ProviderStatus.SUCCESS, purity = 90)
        cache.putProviderResult("key", result)
        assertTrue(cache.getProviderResult("key", 12 * 60 * 60 * 1000)!!.fromCache)
        assertNull(cache.getProviderResult("key", -1))
        assertEquals(90, cache.peekProviderResult("key")!!.purity)
        cache.clear()
        assertNull(cache.peekProviderResult("key"))
        cache.putProviderResult("key", result)
        assertTrue(cache.getProviderResult("key", 1_000)!!.fromCache)
    }

    @Test fun localIpExtractor_handlesAllFallbackShapes() {
        val repo = LocalIpRepository()
        assertEquals("1.2.3.4", repo.extractIp("{\"ip\":\"1.2.3.4\"}"))
        assertEquals("2001:4860::1", repo.extractIp("{\"ipAddress\":\"2001:4860::1\"}"))
        assertEquals("9.9.9.9", repo.extractIp("9.9.9.9"))
        assertNull(repo.extractIp("not an ip"))
    }
}
