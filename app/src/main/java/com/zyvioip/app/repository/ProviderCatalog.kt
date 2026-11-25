package com.zyvioip.app.repository

object ProviderCatalog {
    val all: List<com.zyvioip.app.provider.ProviderSpec> get() = com.zyvioip.app.provider.ProviderCatalog.all
    val defaultActiveIds: Set<String> get() = com.zyvioip.app.provider.ProviderCatalog.defaultActiveIds
    fun byId(id: String) = com.zyvioip.app.provider.ProviderCatalog.byId(id)
}

fun aggregatePurity(results: List<com.zyvioip.app.model.ProviderResult>): Int? =
    com.zyvioip.app.util.averagePurity(results.filter { it.succeeded }.map { it.purity })
