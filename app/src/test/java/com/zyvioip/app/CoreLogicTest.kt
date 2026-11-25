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
        assertTrue(classifyIpType(IpSignals(isProxy = true, isVpn = true)).name == "PROXY")
        assertTrue(classifyIpType(IpSignals(isVpn = true, isDatacenter = true)).name == "VPN")
        assertTrue(classifyIpType(IpSignals(isDatacenter = true, isMobile = true)).name == "DATACENTER")
        assertTrue(classifyIpType(IpSignals(isMobile = true)).name == "MOBILE")
        assertTrue(classifyIpType(IpSignals()).name == "UNKNOWN")
        assertEquals("PROXY", classifyIpType(IpSignals(flags = setOf("proxy"))).name)
    }

    @Test fun flagEmoji_andFlagParsing() {
        assertEquals("\uD83C\uDDFA\uD83C\uDDF8", countryFlag("US"))
        assertEquals("\uD83C\uDDE8\uD83C\uDDF3", countryFlag("CN"))
        assertEquals("", countryFlag("USA"))
        assertEquals("", countryFlag(null))
        assertEquals(setOf("vpn", "proxy", "tor"), parseFlags("VPN, Proxy,tor"))
        assertTrue(parseFlags(null).isEmpty())
    }

    @Test fun catalog_hasElevenProvidersAndFourDefaultActive() {
        assertEquals(11, ProviderCatalog.all.size)
        val expectedActive = setOf("getipintel", "ipapicom", "ipdatafree", "ipapisanon")
        assertEquals(expectedActive, ProviderCatalog.defaultActiveIds)
        assertEquals(com.zyvioip.app.provider.ProviderAuth.EMAIL, ProviderCatalog.byId("getipintel")!!.auth)
        assertFalse(ProviderCatalog.byId("ipdatafree")!!.requiresKey)
        assertTrue(ProviderCatalog.byId("ipdatafree")!!.defaultActive)
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
