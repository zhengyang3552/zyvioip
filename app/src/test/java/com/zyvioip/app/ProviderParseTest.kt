package com.zyvioip.app

import com.zyvioip.app.provider.AbuseIpdbProvider
import com.zyvioip.app.provider.GetIpIntelProvider
import com.zyvioip.app.provider.IpapiIsAnonProvider
import com.zyvioip.app.provider.IpPureProvider
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

    @Test fun getIpIntel_zeroMeansNoProxyEvidence_notPerfectScore() {
        val provider = GetIpIntelProvider()
        val clean = provider.parseForTest("""{"status":"success","result":0.0}""", "1.2.3.4")
        assertEquals(ProviderStatus.SUCCESS, clean.status)
        // 0 表示“没有代理证据”，风险分应为 0；纯净度才是 100。
        // 用户反馈“给的数字直接是 0”，就是这里：现在界面统一显示风险分，0 就是 0，语义一致。
        assertEquals(0, clean.riskScore)
        assertEquals(100, clean.purity)
        assertTrue(clean.message!!.contains("未发现代理"))
    }

    @Test fun getIpIntel_highProbabilityMapsToHighRisk() {
        val provider = GetIpIntelProvider()
        val dirty = provider.parseForTest("""{"status":"success","result":0.9}""", "1.2.3.4")
        assertEquals(90, dirty.riskScore)
        assertEquals(10, dirty.purity)
        assertTrue(dirty.flags.contains(ProviderFlag.PROXY))
        assertTrue("高度疑似代理/VPN" in dirty.message!!)
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

    @Test fun ipPure_parsesFraudScoreAndResidential() {
        val provider = IpPureProvider()
        val body = """{"ip":"1.2.3.4","asn":36352,"asOrganization":"RackNerd LLC","country":"United States","countryCode":"US","region":"Washington","regionCode":"WA","city":"Seattle","timezone":"America/Los_Angeles","fraudScore":28,"isResidential":false,"isBroadcast":false}"""
        val outcome = provider.parseForTest(body, "1.2.3.4")
        assertEquals(ProviderStatus.SUCCESS, outcome.status)
        assertEquals("AS36352", outcome.asn)
        assertEquals("RackNerd LLC", outcome.company)
        assertEquals(28, outcome.riskScore)
        assertEquals(72, outcome.purity)
        assertEquals(false, outcome.isResidential)
        assertEquals("US", outcome.countryCode)
        // 非住宅 -> 机房标记
        assertTrue(outcome.flags.contains(ProviderFlag.DATACENTER))
    }

    @Test fun ipPure_residentialHasNoDatacenterFlag() {
        val provider = IpPureProvider()
        val body = """{"ip":"9.9.9.9","asn":4134,"asOrganization":"Chinanet","country":"China","countryCode":"CN","city":"Shanghai","fraudScore":5,"isResidential":true,"isBroadcast":false}"""
        val outcome = provider.parseForTest(body, "9.9.9.9")
        assertEquals(true, outcome.isResidential)
        assertFalse(outcome.flags.contains(ProviderFlag.DATACENTER))
    }

    @Test fun ipPure_skipsOtherIpBecauseApiCannotQueryArbitraryAddresses() {
        val provider = IpPureProvider()
        // 接口只会返回调用方 IP；若与目标不一致必须标记跳过，
        // 绝不能把本机数据冒充成目标 IP 的结果。
        val body = """{"ip":"1.2.3.4","asn":1,"country":"United States","fraudScore":10,"isResidential":false}"""
        val outcome = provider.parseForTest(body, "8.8.8.8")
        assertEquals(ProviderStatus.SKIPPED, outcome.status)
        assertTrue(outcome.message!!.contains("只能查询本机 IP"))
        // 跳过时不能输出任何评分，避免污染平均值
        assertEquals(null, outcome.riskScore)
    }

    @Test fun ipApiCom_readsOrgInsteadOfIsp() {
        val provider = com.zyvioip.app.provider.IpApiComProvider()
        val body = """{"status":"success","country":"United States","countryCode":"US","city":"Buffalo","isp":"HostPapa","org":"RackNerd LLC","as":"AS36352 HostPapa","hosting":true,"query":"1.2.3.4"}"""
        val outcome = provider.parseForTest(body, "1.2.3.4")
        // org 才是真实运营方；isp 只是线路商（RackNerd 的 isp 显示 HostPapa）
        assertEquals("RackNerd LLC", outcome.company)
        assertTrue(outcome.flags.contains(ProviderFlag.DATACENTER))
    }

    @Test fun ipApiCom_asnIsNumberOnly() {
        val provider = com.zyvioip.app.provider.IpApiComProvider()
        val body = """{"status":"success","isp":"HostPapa","org":"RackNerd LLC","as":"AS36352 HostPapa","hosting":true,"query":"1.2.3.4"}"""
        val outcome = provider.parseForTest(body, "1.2.3.4")
        // 界面要显示 "AS36352"，不是 "AS36352 HostPapa"
        assertEquals("AS36352", outcome.asn)
    }

    @Test fun ipApiCom_detectsIdcByOrganizationName() {
        val provider = com.zyvioip.app.provider.IpApiComProvider()
        // 真实案例：220.181.38.148，hosting 字段是 false，
        // 但 org 明确写着 IDC —— 不能因此判成住宅 IP
        val body = """{"status":"success","isp":"IDC, China Telecommunications Corporation","org":"","asname":"CHINANET-IDC-BJ-AP","hosting":false,"proxy":false,"query":"220.181.38.148"}"""
        val outcome = provider.parseForTest(body, "220.181.38.148")
        assertTrue("org 含 IDC 应判为机房", outcome.flags.contains(ProviderFlag.DATACENTER))
        assertEquals(false, outcome.isResidential)
    }

    @Test fun ipApiCom_residentialLineIsNotDatacenter() {
        val provider = com.zyvioip.app.provider.IpApiComProvider()
        val body = """{"status":"success","isp":"China Telecom","org":"China Telecom","asname":"CHINANET-GD","hosting":false,"proxy":false,"query":"202.96.128.86"}"""
        val outcome = provider.parseForTest(body, "202.96.128.86")
        assertFalse(outcome.flags.contains(ProviderFlag.DATACENTER))
        assertEquals(true, outcome.isResidential)
    }
}
