package com.zyvioip.app.repository

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * 监听网络变化（Wi-Fi <-> 移动网络、断网重连）。
 *
 * 用途：切网络后出口 IP 通常会变，需要重新探测本机 IP 并重查。
 * 只发出「可用网络发生变化」信号，具体是否真的换了 IP 交给上层比对，
 * 避免无谓的重复请求。
 */
class NetworkWatcher(private val context: Context) {

    private val manager: ConnectivityManager? =
        context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

    /** 每次网络可用性/承载类型变化时发出一个值。 */
    fun changes(): Flow<Unit> = callbackFlow {
        val cm = manager
        if (cm == null) {
            // 拿不到系统服务时退化成“只发一次”，不要抛异常
            trySend(Unit)
            awaitClose { }
            return@callbackFlow
        }

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                trySend(Unit)
            }

            override fun onLost(network: Network) {
                // 断网时不发信号，等重新可用
            }

            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                // Wi-Fi 与移动网络切换时 caps 会变
                trySend(Unit)
            }
        }

        runCatching { cm.registerDefaultNetworkCallback(callback) }
        awaitClose { runCatching { cm.unregisterNetworkCallback(callback) } }
    }.distinctUntilChanged()
}
