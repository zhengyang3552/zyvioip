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
