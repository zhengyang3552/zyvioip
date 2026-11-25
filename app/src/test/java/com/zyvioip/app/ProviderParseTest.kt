package com.zyvioip.app

import com.zyvioip.app.provider.AbuseIpdbProvider
import com.zyvioip.app.provider.GetIpIntelProvider
import com.zyvioip.app.provider.IpapiIsAnonProvider
import com.zyvioip.app.provider.IpWhoIsProvider
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

    @Test fun ipapiIsAnon_parsesFlatFreeTierResponse() {
        val provider = IpapiIsAnonProvider()
        // 这是 api.ipapi.is 免费端点的真实响应结构（扁平，无 location/asn 嵌套）
        val body = """{"ip":"8.8.8.8","is_bogon":false,"company":"Google LLC","asn":"AS15169 Google LLC","city":"Mountain View","region":"California","country":"United States","lat":37.38605,"lon":-122.08385,"timezone":"America/Los_Angeles"}"""
        val outcome = provider.parseForTest(body, "8.8.8.8")
        assertEquals(ProviderStatus.SUCCESS, outcome.status)
        assertEquals("United States", outcome.country)
        assertEquals("US", outcome.countryCode)
        assertEquals("Mountain View", outcome.city)
        assertEquals("California", outcome.region)
        assertEquals("Google LLC", outcome.company)
        assertEquals("AS15169", outcome.asn)
        // 免费端点没有评分，不能凭空造分
        assertEquals(null, outcome.purity)
    }

    @Test fun ipapiIsAnon_bogonIsReportedAsError() {
        val provider = IpapiIsAnonProvider()
        val outcome = provider.parseForTest("""{"ip":"192.168.1.1","is_bogon":true}""", "192.168.1.1")
        assertEquals(ProviderStatus.ERROR, outcome.status)
    }

    @Test fun ipWhoIs_parsesSuccessAndFailure() {
        val provider = IpWhoIsProvider()
        val ok = provider.parseForTest(
            """{"ip":"8.8.8.8","success":true,"country":"United States","country_code":"US","region":"California","city":"San Jose","connection":{"asn":15169,"org":"Google LLC","isp":"Google LLC"}}""",
            "8.8.8.8",
        )
        assertEquals(ProviderStatus.SUCCESS, ok.status)
        assertEquals("US", ok.countryCode)
        assertEquals("San Jose", ok.city)
        assertEquals("Google LLC", ok.company)
        assertEquals("AS15169", ok.asn)

        val fail = provider.parseForTest("""{"success":false,"message":"invalid IP address"}""", "x")
        assertEquals(ProviderStatus.ERROR, fail.status)
        assertEquals("invalid IP address", fail.error)
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
