package com.gh.daily_trace.update

import android.util.Log
import com.gh.daily_trace.domain.update.VersionCompare
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext

/** 设置页「版本」区域展示的状态 */
sealed interface UpdateStatus {
    data object Idle : UpdateStatus
    data object Checking : UpdateStatus
    data object UpToDate : UpdateStatus
    data class NewVersion(val tag: String) : UpdateStatus
    data object Failed : UpdateStatus
}

/**
 * 更新检查的统一入口：冷启动静默检查 + 设置页手动检查。
 *
 * 自动弹窗按版本节流（[VersionCompare.shouldPrompt]）：同一版本只自动提示一次，
 * 远端出现更高版本时重新允许提示；手动检查不受节流限制。
 */
class UpdateManager(
    private val checker: UpdateChecker,
    private val store: UpdateStore,
    val currentVersion: String,
) {

    private val _status = MutableStateFlow(restoreStatus())
    val status: StateFlow<UpdateStatus> = _status

    private val _lastCheckAt = MutableStateFlow(store.lastCheckAt)
    val lastCheckAt: StateFlow<Long?> = _lastCheckAt

    private val _pendingVersion = MutableStateFlow<String?>(null)

    /** 非空时主界面弹出一次非强制更新提示 */
    val pendingVersion: StateFlow<String?> = _pendingVersion

    /** 冷启动调用：后台执行，失败静默 */
    suspend fun checkOnStartup() = check(prompt = true)

    /** 设置页手动检查：不受弹窗节流限制 */
    suspend fun checkManually() = check(prompt = false)

    fun dismiss() {
        _pendingVersion.value = null
    }

    private suspend fun check(prompt: Boolean) {
        _status.value = UpdateStatus.Checking
        val tag = withContext(Dispatchers.IO) { checker.fetchLatestTag() }
        val now = System.currentTimeMillis()
        _lastCheckAt.value = now
        store.lastCheckAt = now

        if (tag == null) {
            // 断网 / 超时 / 限流 / 解析异常：静默，只留日志
            Log.d(TAG, "版本检查失败，已静默处理")
            _status.value = UpdateStatus.Failed
            store.lastResult = RESULT_FAILED
            return
        }

        val newer = VersionCompare.isNewer(tag, currentVersion)
        _status.value = if (newer) UpdateStatus.NewVersion(tag) else UpdateStatus.UpToDate
        store.lastResult = if (newer) RESULT_NEW_PREFIX + tag else RESULT_LATEST

        if (prompt && newer && VersionCompare.shouldPrompt(tag, store.lastPromptedVersion)) {
            store.lastPromptedVersion = tag
            _pendingVersion.value = tag
        }
    }

    private fun restoreStatus(): UpdateStatus = when (val result = store.lastResult) {
        RESULT_LATEST -> UpdateStatus.UpToDate
        RESULT_FAILED -> UpdateStatus.Failed
        null -> UpdateStatus.Idle
        else -> if (result.startsWith(RESULT_NEW_PREFIX)) {
            UpdateStatus.NewVersion(result.removePrefix(RESULT_NEW_PREFIX))
        } else {
            UpdateStatus.Idle
        }
    }

    private companion object {
        const val TAG = "UpdateManager"
        const val RESULT_LATEST = "latest"
        const val RESULT_FAILED = "failed"
        const val RESULT_NEW_PREFIX = "new:"
    }
}
