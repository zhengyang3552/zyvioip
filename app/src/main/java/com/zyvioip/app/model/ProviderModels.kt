package com.zyvioip.app.model

data class ProviderSpec(
    val id: String,
    val name: String,
    val auth: ProviderAuth,
    val defaultActive: Boolean = false,
    val requiresKey: Boolean = false,
    val requiresEmail: Boolean = false,
    val scorable: Boolean = true,
    val note: String = "",
    val configLabel: String = "",
    val cacheable: Boolean = true,
    val cacheTtlMs: Long = 12L * 60 * 60 * 1000,
)

enum class ProviderAuth { NONE, API_KEY, EMAIL }

/** Compatibility alias so shared code can use either package spelling. */
typealias AuthKind = ProviderAuth
