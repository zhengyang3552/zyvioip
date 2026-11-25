package com.zyvioip.app.model

/** Outcome of querying one provider. Failures never expose response bodies or keys. */
data class ProviderResult(
    val providerId: String,
    val status: ProviderStatus = ProviderStatus.PENDING,
    val signals: IpSignals = IpSignals(),
    val purity: Int? = null,
    val error: String? = null,
    val fetchedAtMillis: Long = System.currentTimeMillis(),
    val fromCache: Boolean = false,
) {
    val succeeded: Boolean get() = status == ProviderStatus.SUCCESS
}
