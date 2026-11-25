package com.zyvioip.app

import com.zyvioip.app.model.IpOrigin
import com.zyvioip.app.model.OriginVerdict
import com.zyvioip.app.model.resolveOrigin
import com.zyvioip.app.provider.IpApiComProvider
import com.zyvioip.app.provider.IpPureProvider
import com.zyvioip.app.provider.ProviderFlag
import com.zyvioip.app.provider.ProviderStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** IP「来源属性」：住宅 / 原生 / 广播 / 机房。 */
class IpOriginTest {

    @Test fun emptyVerdictsGiveNull() {
        assertNull(resolveOrigin(emptyList()))
    }

    @Test fun broadcastOutranksEverythingElse() {
        val result = resolveOrigin(
            listOf(
                OriginVerdict(IpOrigin.DATACENTER, "ip-api.com"),
                OriginVerdict(IpOrigin.BROADCAST, "IPPure"),
            )
        )
        assertEquals(IpOrigin.BROADCAST, result!!.origin)
        assertEquals("IPPure", result.source)
    }

    @Test fun datacenterOutranksResidential() {
        val result = resolveOrigin(
            listOf(
                OriginVerdict(IpOrigin.RESIDENTIAL, "A"),
                OriginVerdict(IpOrigin.DATACENTER, "B"),
            )
        )
        assertEquals(IpOrigin.DATACENTER, result!!.origin)
    }

    @Test fun residentialIsKeptWhenItIsTheOnlyVerdict() {
        val result = resolveOrigin(listOf(OriginVerdict(IpOrigin.RESIDENTIAL, "IPPure")))
        assertEquals(IpOrigin.RESIDENTIAL, result!!.origin)
    }

    @Test fun ipPure_mapsResidentialToNativeIp() {
        val provider = IpPureProvider()
        val body = """{"ip":"1.2.3.4","asn":4134,"asOrganization":"Chinanet","country":"China","countryCode":"CN","fraudScore":3,"isResidential":true,"isBroadcast":false}"""
        val outcome = provider.parseForTest(body, "1.2.3.4")
        assertEquals(ProviderStatus.SUCCESS, outcome.status)
        // 住宅线路在“来源”维度里就是原生 IP
        assertEquals(IpOrigin.NATIVE, outcome.origin)
    }

    @Test fun ipPure_broadcastWinsOverResidential() {
        val provider = IpPureProvider()
        val body = """{"ip":"1.2.3.4","asn":1,"asOrganization":"X","country":"United States","fraudScore":10,"isResidential":true,"isBroadcast":true}"""
        val outcome = provider.parseForTest(body, "1.2.3.4")
        assertEquals(IpOrigin.BROADCAST, outcome.origin)
    }

    @Test fun ipPure_nonResidentialIsDatacenter() {
        val provider = IpPureProvider()
        val body = """{"ip":"1.2.3.4","asn":36352,"asOrganization":"RackNerd LLC","country":"United States","fraudScore":28,"isResidential":false,"isBroadcast":false}"""
        val outcome = provider.parseForTest(body, "1.2.3.4")
        assertEquals(IpOrigin.DATACENTER, outcome.origin)
    }

    @Test fun ipApiCom_hostingMapsToDatacenter() {
        val provider = IpApiComProvider()
        val body = """{"status":"success","country":"United States","org":"RackNerd LLC","as":"AS36352 HostPapa","hosting":true,"proxy":false,"query":"1.2.3.4"}"""
        val outcome = provider.parseForTest(body, "1.2.3.4")
        assertEquals(IpOrigin.DATACENTER, outcome.origin)
        assertTrue(outcome.flags.contains(ProviderFlag.DATACENTER))
    }

    @Test fun ipApiCom_residentialLineHasNoOriginClaim() {
        val provider = IpApiComProvider()
        // ip-api.com 无法区分住宅与企业，所以不给结论，而不是瞎猜
        val body = """{"status":"success","country":"China","isp":"China Telecom","hosting":false,"proxy":false,"mobile":false,"query":"1.2.3.4"}"""
        val outcome = provider.parseForTest(body, "1.2.3.4")
        assertNull(outcome.origin)
    }

    @Test fun ipApiCom_mobileMapsToMobile() {
        val provider = IpApiComProvider()
        val body = """{"status":"success","isp":"China Mobile","hosting":false,"proxy":false,"mobile":true,"query":"1.2.3.4"}"""
        val outcome = provider.parseForTest(body, "1.2.3.4")
        assertEquals(IpOrigin.MOBILE, outcome.origin)
    }
}
