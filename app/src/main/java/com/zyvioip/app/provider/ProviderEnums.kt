package com.zyvioip.app.provider

/** SKIPPED：该源对本次目标 IP 没有能力（例如只能查本机 IP），既不算成功也不算失败。 */
enum class ProviderStatus { PENDING, LOADING, SUCCESS, ERROR, NEEDS_CONFIG, SKIPPED }

enum class ProviderSignalKind { NONE, PURITY, FLAG, INFO }
