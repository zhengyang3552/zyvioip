package com.zyvioip.app.provider

import okhttp3.HttpUrl
import okhttp3.Request
import java.net.URLEncoder

internal fun url(base: String, block: HttpUrl.Builder.() -> Unit = {}): HttpUrl =
    HttpUrl.Builder().scheme("https").host(base).apply(block).build()

internal fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")

internal fun get(url: HttpUrl): Request = Request.Builder().url(url).get().build()

internal fun textField(map: Map<String, Any?>, vararg names: String): String? {
    for (name in names) {
        val value = map[name] ?: continue
        if (value is String && value.isNotBlank()) return value
    }
    return null
}

internal fun boolField(map: Map<String, Any?>, vararg names: String): Boolean? {
    for (name in names) {
        when (val value = map[name] ?: continue) {
            is Boolean -> return value
            is Number -> if (value.toInt() == 0 || value.toInt() == 1) return value.toInt() == 1
            is String -> when (value.lowercase()) {
                "true", "yes", "1" -> return true
                "false", "no", "0" -> return false
            }
        }
    }
    return null
}

internal fun numberField(map: Map<String, Any?>, vararg names: String): Number? {
    for (name in names) {
        when (val value = map[name] ?: continue) {
            is Number -> return value
            is String -> value.toDoubleOrNull()?.let { return it }
        }
    }
    return null
}

internal fun nested(map: Map<String, Any?>, vararg path: String): Map<String, Any?> {
    var current = map
    for (key in path) {
        current = current[key] as? Map<String, Any?> ?: return emptyMap()
    }
    return current
}
