package com.zyvioip.app

import com.zyvioip.app.provider.AbuseIpdbProvider
import com.zyvioip.app.provider.GetIpIntelProvider
import com.zyvioip.app.provider.IpapiIsAnonProvider
import com.zyvioip.app.provider.IpdataFreeProvider
import com.zyvioip.app.provider.ProviderConfig
import com.zyvioip.app.provider.ProviderFlag
import com.zyvioip.app.provider.ProviderStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 覆盖历史上解析写错、导致标记/分数失真的几个供应商。 */
class ProviderParseTest {

    @Test fun getIpIntel_negativeResultIsErrorNotPerfectPurity() {
        val provider = GetIpIntelProvider()
        listOf("-1", "-2", "-3", "-4").forEach { code ->
            val body = """{"status":"error","result":$code}"""
            val outcome = provider.parseForTest(body, "1.2.3.4")
            assertEquals("错误码 $code 必须判为失败", ProviderStatus.ERROR, outcome.status)
            // 旧实现会把 -1 算成 purity=100（最干净），这里必须不再输出评分
            assertEquals("错误码 $code 不能产生分数", null, outcome.purity)
        }
    }

    @Test fun getIpIntel_validProbabilityMapsToPurity() {
        val provider = GetIpIntelProvider()
        val clean = provider.parseForTest("""{"status":"success","result":0.0}""", "1.2.3.4")
        assertEquals(ProviderStatus.SUCCESS, clean.status)
        assertEquals(100, clean.purity)

        val dirty = provider.parseForTest("""{"status":"success","result":0.9}""", "1.2.3.4")
        assertEquals(10, dirty.purity)
        assertTrue(dirty.flags.contains(ProviderFlag.PROXY))
    }

    @Test fun abuseIpdb_usesUsageTypeForDatacenterFlag() {
        val provider = AbuseIpdbProvider(
            config = ProviderConfig("abuseipdb", apiKey = "dummy"),
        )
        // isp 是字符串字段，旧实现拿它当布尔量判断，数据中心标记永远不会出现
        val hosting = provider.parseForTest(
            """{"data":{"ipAddress":"1.2.3.4","abuseConfidenceScore":0,"isp":"Some Cloud","usageType":"Data Center/Web Hosting/Transit"}}""",
            "1.2.3.4",
        )
        assertEquals(ProviderStatus.SUCCESS, hosting.status)
        assertTrue("机房 IP 应带 datacenter 标记", hosting.flags.contains(ProviderFlag.DATACENTER))

        val residential = provider.parseForTest(
            """{"data":{"ipAddress":"1.2.3.4","abuseConfidenceScore":0,"isp":"China Telecom","usageType":"Fixed Line ISP"}}""",
            "1.2.3.4",
        )
        assertFalse(residential.flags.contains(ProviderFlag.DATACENTER))
    }

    @Test fun abuseIpdb_scoreBecomesPurity() {
        val provider = AbuseIpdbProvider(config = ProviderConfig("abuseipdb", apiKey = "dummy"))
        val bad = provider.parseForTest(
            """{"data":{"ipAddress":"1.2.3.4","abuseConfidenceScore":80,"usageType":"Fixed Line ISP"}}""",
            "1.2.3.4",
        )
        assertEquals(20, bad.purity)
        assertTrue(bad.flags.contains(ProviderFlag.ABUSER))
    }

    @Test fun ipapiIsAnon_asnTypeStringMarksDatacenter() {
        val provider = IpapiIsAnonProvider()
        val hosting = provider.parseForTest(
            """{"ip":"1.2.3.4","is_datacenter":false,"asn":{"asn":12345,"name":"Cloud","type":"hosting"}}""",
            "1.2.3.4",
        )
        assertTrue("asn.type=hosting 应判定为机房", hosting.flags.contains(ProviderFlag.DATACENTER))

        val isp = provider.parseForTest(
            """{"ip":"1.2.3.4","asn":{"asn":12345,"name":"Telecom","type":"isp"}}""",
            "1.2.3.4",
        )
        assertFalse(isp.flags.contains(ProviderFlag.DATACENTER))
    }

    @Test fun ipapiIsAnon_vpnAndProxyAreDistinctFlags() {
        val provider = IpapiIsAnonProvider()
        val proxy = provider.parseForTest(
            """{"ip":"1.2.3.4","is_proxy":true,"is_vpn":false}""",
            "1.2.3.4",
        )
        assertTrue(proxy.flags.contains(ProviderFlag.PROXY))
        assertFalse("代理不应该被同时标成 VPN", proxy.flags.contains(ProviderFlag.VPN))
    }

    @Test fun ipdataFree_errorBodyIsReportedAsError() {
        val provider = IpdataFreeProvider()
        val outcome = provider.parseForTest("""{"message":"quota exceeded"}""", "1.2.3.4")
        assertEquals(ProviderStatus.ERROR, outcome.status)
        assertEquals("quota exceeded", outcome.error)
    }

    @Test fun ipdataFree_readsGeoFields() {
        val provider = IpdataFreeProvider()
        val outcome = provider.parseForTest(
            """{"ip":"1.2.3.4","country_name":"Japan","country_code":"JP","city":"Tokyo","organisation":"ISP"}""",
            "1.2.3.4",
        )
        assertEquals(ProviderStatus.SUCCESS, outcome.status)
        assertEquals("Japan", outcome.country)
        assertEquals("JP", outcome.countryCode)
        assertEquals("Tokyo", outcome.city)
        assertEquals("ISP", outcome.company)
    }
}
