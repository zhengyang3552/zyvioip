package com.zyvioip.app.util

/** Convert an ISO 3166-1 alpha-2 country code into its regional-indicator flag emoji. */
fun countryFlag(code: String?): String {
    val normalized = code?.trim().orEmpty()
    if (normalized.length != 2) return ""
    val upper = normalized.uppercase()
    if (!upper.all { it in 'A'..'Z' }) return ""
    val first = 0x1F1E6 + (upper[0] - 'A')
    val second = 0x1F1E6 + (upper[1] - 'A')
    return String(Character.toChars(first)) + String(Character.toChars(second))
}
