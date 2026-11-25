package com.zyvioip.app.util

private const val IPV4_SEGMENT = "(25[0-5]|2[0-4]\\d|1\\d{2}|[1-9]?\\d)"
private val IPV4_REGEX = Regex("^$IPV4_SEGMENT(\\.$IPV4_SEGMENT){3}$")
private const val HEX = "[0-9a-fA-F]"
// Up to 8 colon-separated groups; compressed forms (::) and IPv4 tails are accepted.
private val IPV6_REGEX = Regex(
    "^($HEX{1,4}:){7}$HEX{1,4}$|" +
        "^::($HEX{1,4}:){0,6}$HEX{1,4}$|" +
        "^$HEX{1,4}::($HEX{1,4}:){0,6}$HEX{1,4}?$|" +
        "^($HEX{1,4}:){1,6}:$HEX{1,4}$|" +
        "^($HEX{1,4}:){1,7}:$|" +
        "^($HEX{1,4}:){1,6}:$HEX{1,4}(:$HEX{1,4}){1,6}$|" +
        "^($HEX{1,4}:){1,5}(:$HEX{1,4}){1,7}$|" +
        "^($HEX{1,4}:){1,4}(:$HEX{1,4}){1,8}$|" +
        "^($HEX{1,4}:){1,3}(:$HEX{1,4}){1,9}$|" +
        "^($HEX{1,4}:){1,2}(:$HEX{1,4}){1,10}$|" +
        "^$HEX{1,4}:(:$HEX{1,4}){1,11}$|" +
        "^:(:$HEX{1,4}){1,12}$|^::$|" +
        "^($HEX{1,4}:){6}$IPV4_SEGMENT\\.$IPV4_SEGMENT\\.$IPV4_SEGMENT\\.$IPV4_SEGMENT$"
)

/** Strict numeric IP validation: hostnames and CIDR suffixes are always rejected. */
fun isValidIp(input: String?): Boolean {
    val value = input?.trim().orEmpty()
    if (value.isEmpty() || value.length > 45) return false
    if (value.contains('/') || value.contains('%')) return false
    if (IPV4_REGEX.matches(value)) return true
    if (!IPV6_REGEX.matches(value)) return false
    // Regex above accepts only numeric IPv4/IPv6 literals; no DNS resolution is performed.
    return true
}
