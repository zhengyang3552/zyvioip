package com.zyvioip.app.repository

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.debounce

/**
 * 监听「网络承载方式」变化（Wi-Fi <-> 移动网络 / 断网重连）。
 *
 * 修复两个问题：
 *
 * 1. **旧实现对 Unit 用 distinctUntilChanged，等于最多只发一次**
 *    因为每次发的都是同一个 Unit，第二次就被判为“与上次相同”而丢弃。
 *    改为发出承载类型标识（wifi/cellular/ethernet/unknown），
 *    只有真正换了网络时才可能不同。
 *
 * 2. **onCapabilitiesChanged 触发过于频繁**
 *    信号强度、带宽估算、链路参数变化都会回调，移动网络下可能每秒数次。
 *    旧代码每次都 trySend，上层收到就执行一次完整的本机 IP 探测 + 全源查询，
 *    表现就是“按秒卡一下”。
 *    现在只在该回调里比较承载类型，并且对整条流做 debounce，
 *    避免频繁的重复查询。
 */
class NetworkWatcher(private val context: Context) {

    private val manager: ConnectivityManager? =
        context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

    /**
     * 承载方式变化时发出新的标识。取值稳定，便于上层去重。
     * debounce 用于吸收切换过程中的连续抖动。
     */
    fun changes(): Flow<String> = callbackFlow {
        val cm = manager
        if (cm == null) {
            awaitClose { }
            return@callbackFlow
        }

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                trySend(transportOf(cm, network))
            }

            override fun onLost(network: Network) {
                // 断网时不发信号，等重新可用
            }

            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                // 只有承载方式真的变了才值得上报；
                // 信号/带宽这类高频变化交由下面的 debounce 吸收。
                trySend(transportOf(caps))
            }
        }

        runCatching { cm.registerDefaultNetworkCallback(callback) }
        awaitClose { runCatching { cm.unregisterNetworkCallback(callback) } }
    }
        // 只有承载类型变化才通知上层（wifi -> cellular 之类）
        .debounce(DEBOUNCE_MS)

    private fun transportOf(cm: ConnectivityManager, network: Network): String =
        cm.getNetworkCapabilities(network)?.let { transportOf(it) } ?: UNKNOWN

    private fun transportOf(caps: NetworkCapabilities): String = when {
        caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "wifi"
        caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "cellular"
        caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ethernet"
        caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "vpn"
        else -> UNKNOWN
    }

    private companion object {
        const val UNKNOWN = "unknown"

        /** 吸收切换过程中的连续回调，避免短时间内触发多次全量查询。 */
        const val DEBOUNCE_MS = 1_500L
    }
}
