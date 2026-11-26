package com.zyvioip.app.ui

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import com.zyvioip.app.repository.HomeRepositoryApi

/**
 * 进程级共享的 ViewModel 容器。
 *
 * 问题：MainActivity / SettingsActivity / PendingProvidersActivity 各自用
 * `by viewModels` 会创建**独立实例**，而 MainViewModel 的 init 会执行
 * initialize()（本机 IP 探测 + 全源查询）—— 于是每次进设置页都重跑一遍，
 * 表现就是「点设置卡一下才跳转」。
 *
 * 这里用应用级 ViewModelStore 持有唯一实例，所有 Activity 复用同一份状态。
 */
object SharedViewModel : ViewModelStoreOwner {

    override val viewModelStore = ViewModelStore()

    private var instance: MainViewModel? = null

    @Synchronized
    fun get(repository: HomeRepositoryApi): MainViewModel {
        instance?.let { return it }
        val vm = ViewModelProvider(this, MainViewModel.Factory(repository))
            .get(MainViewModel::class.java)
        instance = vm
        return vm
    }
}
