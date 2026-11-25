package com.zyvioip.app

import com.zyvioip.app.provider.IpApiComProvider
import com.zyvioip.app.provider.IpPureProvider
import com.zyvioip.app.provider.IptoToProvider
import com.zyvioip.app.provider.ProxyCheckProvider
import com.zyvioip.app.provider.flattenForDisplay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 卡片展开时展示“全部字段”所依赖的拍平逻辑。 */
class RawFieldsTest {

    @Test fun flatten_handlesNestedAndPrimitives() {
        val input = mapOf<String, Any?>(
            "ip" to "1.2.3.4",
            "asn" to 36352,
            "is_bogon" to false,
            "rate" to 1.5,
            "empty" to null,
            "blank" to "  ",
            "flags" to mapOf<String, Any?>("hosting" to true, "vpn" to false),
            "languages" to listOf("English", "Chinese"),
        )
        val flat = flattenForDisplay(input)

        assertEquals("1.2.3.4", flat["ip"])
        assertEquals("36352", flat["asn"])
        assertEquals("false", flat["is_bogon"])
        assertEquals("1.5", flat["rate"])
        // 嵌套对象用 "." 展开
        assertEquals("true", flat["flags.hosting"])
        assertEquals("false", flat["flags.vpn"])
        // 数组拼成一行
        assertEquals("English, Chinese", flat["languages"])
        // 空值 / 空白值不展示
        assertFalse(flat.containsKey("empty"))
        assertFalse(flat.containsKey("blank"))
    }

    @Test fun flatten_dropsNullOrEmptyCollections() {
        val flat = flattenForDisplay(
            mapOf(
                "a" to null,
                "b" to emptyList<String>(),
                "c" to emptyMap<String, Any?>(),
            )
        )
        assertTrue("空内容不应产生键", flat.isEmpty())
    }

    @Test fun iptoTo_exposesAllFields() {
        val body = """{"ip":"185.220.101.45","version":"v4","bogon":false,"asn":60729,"asn_org":"Stiftung","asn_type":"hosting","country_code":"US","city":"Delaware","state":"Ohio","is_datacenter":true,"is_vpn":true,"is_tor":false,"is_abuser":true,"abuse_score":3,"risk_tier":"medium","threat_type":"abuser","connection_type":"datacenter","flags":{"hosting":true,"vpn":true,"tor":false}}"""
        val outcome = IptoToProvider().parseForTest(body, "185.220.101.45")
        val raw = outcome.rawFields
        // 不仅解析出业务字段，原始字段也要完整保留
        assertEquals("v4", raw["version"])
        assertEquals("hosting", raw["asn_type"])
        assertEquals("medium", raw["risk_tier"])
        assertEquals("abuser", raw["threat_type"])
        assertEquals("datacenter", raw["connection_type"])
        assertEquals("true", raw["flags.hosting"])
        assertTrue(raw.size >= 15)
    }

    @Test fun proxyCheck_exposesFieldsFromDynamicKey() {
        val body = """{"status":"ok","185.220.101.45":{"asn":"AS60729","range":"185.220.101.45/27","hostname":"tor-exit-45.for-privacy.net","organisation":"ForPrivacyNET","country":"Germany","isocode":"DE","city":"Berlin","proxy":"yes","type":"TOR","risk":100}}"""
        val outcome = ProxyCheckProvider().parseForTest(body, "185.220.101.45")
        val raw = outcome.rawFields
        // 必须是 IP 那一层的字段，不能把 status 混进来
        assertEquals("tor-exit-45.for-privacy.net", raw["hostname"])
        assertEquals("185.220.101.45/27", raw["range"])
        assertEquals("yes", raw["proxy"])
        assertEquals("TOR", raw["type"])
        assertFalse("不应包含顶层 status", raw.containsKey("status"))
    }

    @Test fun ipPure_exposesFraudAndResidential() {
        val body = """{"ip":"1.2.3.4","asn":36352,"asOrganization":"RackNerd LLC","country":"United States","countryCode":"US","city":"Seattle","fraudScore":28,"isResidential":false,"isBroadcast":false}"""
        val outcome = IpPureProvider().parseForTest(body, "1.2.3.4")
        val raw = outcome.rawFields
        assertEquals("28", raw["fraudScore"])
        assertEquals("false", raw["isResidential"])
        assertEquals("false", raw["isBroadcast"])
        assertEquals("RackNerd LLC", raw["asOrganization"])
    }

    @Test fun ipApiCom_exposesFlagsAndReverse() {
        val body = """{"status":"success","country":"United States","city":"Buffalo","isp":"HostPapa","org":"RackNerd LLC","as":"AS36352 HostPapa","asname":"AS-COLOCROSSING","reverse":"172-245-145-53-host.colocrossing.com","hosting":true,"proxy":false,"mobile":false,"query":"1.2.3.4"}"""
        val outcome = IpApiComProvider().parseForTest(body, "1.2.3.4")
        val raw = outcome.rawFields
        assertEquals("AS-COLOCROSSING", raw["asname"])
        assertEquals("172-245-145-53-host.colocrossing.com", raw["reverse"])
        assertEquals("true", raw["hosting"])
        assertEquals("false", raw["proxy"])
    }
}
