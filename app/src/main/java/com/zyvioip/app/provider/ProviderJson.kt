package com.zyvioip.app.provider

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject

/** JSON helpers that accept strings, numbers, booleans and nested objects without throwing. */
internal fun parseObject(json: kotlinx.serialization.json.Json, body: String): Map<String, Any?> =
    runCatching { json.parseToJsonElement(body).jsonObject.toAnyMap() }.getOrNull() ?: emptyMap()

private fun JsonObject.toAnyMap(): Map<String, Any?> = entries.associate { (key, value) -> key to value.toAny() }

private fun JsonElement.toAny(): Any? = when (this) {
    is JsonObject -> toAnyMap()
    is JsonPrimitive -> {
        when {
            isString -> content
            booleanOrNull != null -> booleanOrNull
            doubleOrNull != null -> doubleOrNull
            else -> contentOrNull
        }
    }
    else -> null
}

/**
 * 把任意 JSON Map 拍平成 "键.子键 = 值" 的扁平列表，用于卡片展开时展示全部字段。
 *
 * 规则：
 * - 嵌套对象用 "." 连接（如 flags.hosting）
 * - 空值、空集合、空对象跳过（显示一堆空行没有意义）
 * - 布尔值转成 true/false，数字保留原样
 * - 最多递归 3 层，防止异常响应导致深层嵌套
 */
internal fun flattenForDisplay(
    map: Map<String, Any?>,
    prefix: String = "",
    depth: Int = 0,
): Map<String, String> {
    if (depth > 3) return emptyMap()
    val out = linkedMapOf<String, String>()
    for ((key, value) in map) {
        val name = if (prefix.isEmpty()) key else "$prefix.$key"
        when (value) {
            null -> Unit
            is Map<*, *> -> {
                @Suppress("UNCHECKED_CAST")
                out += flattenForDisplay(value as Map<String, Any?>, name, depth + 1)
            }
            is Iterable<*> -> {
                val text = value.filterNotNull().joinToString(", ")
                    .ifBlank { "" }
                if (text.isNotBlank()) out[name] = text
            }
            is Boolean -> out[name] = if (value) "true" else "false"
            is Number -> out[name] = if (value.toDouble() % 1.0 == 0.0) {
                value.toLong().toString()
            } else {
                value.toString()
            }
            else -> {
                val text = value.toString()
                if (text.isNotBlank()) out[name] = text
            }
        }
    }
    return out
}
