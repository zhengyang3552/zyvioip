package com.zyvioip.app

import com.zyvioip.app.model.IpOrigin
import com.zyvioip.app.provider.IptoToProvider
import com.zyvioip.app.provider.ProviderFlag
import com.zyvioip.app.provider.ProviderStatus
import com.zyvioip.app.provider.ProxyCheckProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** ipto.to 与 proxycheck.io 的解析（响应体均取自真实接口）。 */
class NewProviderParseTest {

    // ipto.to：185.220.101.45 的真实响应
    private val iptoTor =
        """{"ip":"185.220.101.45","version":"v4","bogon":false,"asn":60729,"asn_name":"TORSERVERS-NET","asn_org":"Stiftung Erneuerbare Freiheit","asn_type":"hosting","country_code":"US","country_name":"United States","city":"Delaware","state":"Ohio","is_datacenter":true,"is_mobile":false,"is_residential":false,"is_bot":false,"is_proxy":false,"is_vpn":true,"is_tor":false,"is_abuser":true,"abuse_score":3,"risk_tier":"medium","threat_type":"abuser","connection_type":"datacenter","flags":{"hosting":true,"vpn":true,"tor":false,"abuser":true}}"""

    @Test fun iptoTo_parsesTorVpnAbuser() {
        val outcome = IptoToProvider().parseForTest(iptoTor, "185.220.101.45")
        assertEquals(ProviderStatus.SUCCESS, outcome.status)
        assertEquals("AS60729", outcome.asn)
        assertEquals("Stiftung Erneuerbare Freiheit", outcome.company)
        assertEquals("US", outcome.countryCode)
        assertEquals(3, outcome.riskScore)
        assertEquals(97, outcome.purity)
        assertTrue(outcome.flags.contains(ProviderFlag.VPN))
        assertTrue(outcome.flags.contains(ProviderFlag.DATACENTER))
        assertTrue(outcome.flags.contains(ProviderFlag.ABUSER))
        assertFalse("该 IP 的 is_tor 为 false", outcome.flags.contains(ProviderFlag.TOR))
        assertEquals(IpOrigin.DATACENTER, outcome.origin)
    }

    @Test fun iptoTo_missingAbuseScoreIsNotZero() {
        // 普通机房 IP 的响应里没有 abuse_score / risk_tier 字段
        val body =
            """{"ip":"172.245.145.53","bogon":false,"asn":36352,"asn_org":"HostPapa","country_code":"US","city":"Buffalo","is_datacenter":true,"is_mobile":false,"is_residential":false,"is_vpn":false,"is_tor":false,"is_abuser":false,"connection_type":"datacenter"}"""
        val outcome = IptoToProvider().parseForTest(body, "172.245.145.53")
        assertEquals(ProviderStatus.SUCCESS, outcome.status)
        // 字段缺失 = 没有评分，不能算成 0 分
        assertNull(outcome.riskScore)
        assertNull(outcome.purity)
        assertTrue(outcome.flags.contains(ProviderFlag.DATACENTER))
        assertEquals(IpOrigin.DATACENTER, outcome.origin)
    }

    @Test fun iptoTo_residentialMapsToNative() {
        val body =
            """{"ip":"1.2.3.4","bogon":false,"asn":1,"asn_org":"Home ISP","is_datacenter":false,"is_residential":true,"is_vpn":false,"is_tor":false}"""
        val outcome = IptoToProvider().parseForTest(body, "1.2.3.4")
        assertEquals(IpOrigin.NATIVE, outcome.origin)
        assertFalse(outcome.flags.contains(ProviderFlag.DATACENTER))
    }

    @Test fun iptoTo_invalidIpIsError() {
        val outcome = IptoToProvider().parseForTest("""{"error":"Invalid IP address"}""", "1.2.3.4")
        assertEquals(ProviderStatus.ERROR, outcome.status)
        assertEquals("Invalid IP address", outcome.error)
    }

    @Test fun iptoTo_bogonIsError() {
        val body = """{"ip":"192.168.1.1","bogon":true,"bogon_type":"private"}"""
        val outcome = IptoToProvider().parseForTest(body, "192.168.1.1")
        assertEquals(ProviderStatus.ERROR, outcome.status)
    }

    // proxycheck.io：数据挂在以 IP 为名的 key 下
    private val pcTor =
        """{"status":"ok","185.220.101.45":{"asn":"AS60729","range":"185.220.101.45/27","hostname":"tor-exit-45.for-privacy.net","provider":"Stiftung Erneuerbare Freiheit","organisation":"ForPrivacyNET","country":"Germany","isocode":"DE","region":"State of Berlin","city":"Berlin","devices":{"address":1,"subnet":152},"proxy":"yes","type":"TOR","risk":100}}"""

    @Test fun proxyCheck_parsesDynamicKeyAndStringProxy() {
        val outcome = ProxyCheckProvider().parseForTest(pcTor, "185.220.101.45")
        assertEquals(ProviderStatus.SUCCESS, outcome.status)
        // proxy 是字符串 "yes"，必须能正确识别
        assertTrue(outcome.flags.contains(ProviderFlag.TOR))
        assertEquals(100, outcome.riskScore)
        assertEquals(0, outcome.purity)
        assertEquals("AS60729", outcome.asn)
        assertEquals("ForPrivacyNET", outcome.company)
        assertEquals("DE", outcome.countryCode)
        assertTrue(outcome.message!!.contains("Tor"))
    }

    @Test fun proxyCheck_businessTypeMapsToBusinessOrigin() {
        val body =
            """{"status":"ok","202.96.128.86":{"asn":"AS4134","organisation":"Chinanet GD","isocode":"CN","proxy":"no","type":"Business","risk":0}}"""
        val outcome = ProxyCheckProvider().parseForTest(body, "202.96.128.86")
        assertEquals(ProviderStatus.SUCCESS, outcome.status)
        assertEquals(IpOrigin.BUSINESS, outcome.origin)
        assertEquals(0, outcome.riskScore)
        assertTrue(outcome.flags.isEmpty())
    }

    @Test fun proxyCheck_errorStatusIsReported() {
        val outcome = ProxyCheckProvider().parseForTest(
            """{"status":"error","message":"No valid IP Addresses supplied."}""",
            "1.2.3.4",
        )
        assertEquals(ProviderStatus.ERROR, outcome.status)
        assertEquals("No valid IP Addresses supplied.", outcome.error)
    }

    @Test fun proxyCheck_vpnTypeSetsVpnFlag() {
        val body =
            """{"status":"ok","1.2.3.4":{"asn":"AS1","proxy":"yes","type":"VPN","risk":66}}"""
        val outcome = ProxyCheckProvider().parseForTest(body, "1.2.3.4")
        assertTrue(outcome.flags.contains(ProviderFlag.VPN))
        assertFalse("VPN 不应同时标成 Tor", outcome.flags.contains(ProviderFlag.TOR))
        assertEquals(66, outcome.riskScore)
    }
}
