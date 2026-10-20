package com.gh.daily_trace.ui.settings

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gh.daily_trace.backup.AutoBackup
import com.gh.daily_trace.backup.BackupManager
import com.gh.daily_trace.data.model.AppSettings
import com.gh.daily_trace.data.repository.DailyTraceRepository
import com.gh.daily_trace.domain.reminder.ReminderPause
import com.gh.daily_trace.reminder.ReminderScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 本机备份概况：份数与最近一次时间 */
data class BackupInfo(val count: Int = 0, val lastAt: Long? = null)

/**
 * 设置页：读改写即时保存（单写者，乐观更新）。
 * 备份区块：导出（SAF 目标 URI）、导入（覆盖式，完成后重排提醒）、本机自动备份。
 */
class SettingsViewModel(
    private val repository: DailyTraceRepository,
    private val backupManager: BackupManager,
    private val autoBackup: AutoBackup,
    private val reminderScheduler: ReminderScheduler,
) : ViewModel() {

    private val _settings = MutableStateFlow(AppSettings())
    val settings: StateFlow<AppSettings> = _settings

    private val _backupInfo = MutableStateFlow(BackupInfo())
    val backupInfo: StateFlow<BackupInfo> = _backupInfo

    /** 一次性提示（Snackbar）；展示后调用 [consumeMessage] */
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    init {
        viewModelScope.launch {
            val loaded = repository.getSettings()
            // 已到期的暂停值顺手清掉，避免设置页展示旧状态
            if (loaded.pausedUntil != null && !ReminderPause.isPaused(loaded.pausedUntil, System.currentTimeMillis())) {
                val cleaned = loaded.copy(pausedUntil = null)
                repository.saveSettings(cleaned)
                _settings.value = cleaned
            } else {
                _settings.value = loaded
            }
        }
        refreshBackupInfo()
    }

    fun update(transform: (AppSettings) -> AppSettings) {
        val next = transform(_settings.value)
        _settings.value = next
        viewModelScope.launch { repository.saveSettings(next) }
    }

    /** 暂停提醒至指定时刻（epoch millis）：先落库再撤闹钟，保证恢复时读到最新状态 */
    fun pauseUntil(untilMillis: Long) {
        if (!ReminderPause.isValidUntil(untilMillis, System.currentTimeMillis())) {
            _message.value = "暂停截止时间需晚于当前时间"
            return
        }
        val next = _settings.value.copy(pausedUntil = untilMillis)
        _settings.value = next
        viewModelScope.launch {
            repository.saveSettings(next)
            runCatching { withContext(Dispatchers.IO) { reminderScheduler.pauseUntil(untilMillis) } }
            _message.value = "已暂停提醒"
        }
    }

    /** 立即恢复提醒：先清库再重排，避免重排时仍读到暂停状态 */
    fun resumeReminders() {
        val next = _settings.value.copy(pausedUntil = null)
        _settings.value = next
        viewModelScope.launch {
            repository.saveSettings(next)
            runCatching { withContext(Dispatchers.IO) { reminderScheduler.resume() } }
            _message.value = "提醒已恢复"
        }
    }

    fun refreshBackupInfo() {
        viewModelScope.launch(Dispatchers.IO) {
            val files = autoBackup.listBackups()
            _backupInfo.value = BackupInfo(count = files.size, lastAt = files.firstOrNull()?.lastModified())
        }
    }

    /** 手动立即备份到应用私有目录（忽略开关与间隔） */
    fun backupNow() {
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { autoBackup.runIfDue(manual = true) } }
                .onSuccess {
                    _message.value = "已备份到本机"
                    refreshBackupInfo()
                }
                .onFailure { _message.value = "备份失败：${it.message ?: "未知错误"}" }
        }
    }

    /** 导出到 SAF 选定的文件；失败给出提示 */
    fun exportTo(uri: Uri, resolver: ContentResolver) {
        viewModelScope.launch {
            runCatching {
                val json = withContext(Dispatchers.IO) { backupManager.exportJson() }
                withContext(Dispatchers.IO) {
                    val out = resolver.openOutputStream(uri) ?: error("无法写入所选文件")
                    out.use { it.write(json.toByteArray()) }
                }
            }
                .onSuccess { _message.value = "导出完成" }
                .onFailure { _message.value = "导出失败：${it.message ?: "未知错误"}" }
        }
    }

    /** 覆盖式导入；成功后重排提醒（数据整体被替换） */
    fun importFrom(uri: Uri, resolver: ContentResolver) {
        viewModelScope.launch {
            val result = runCatching {
                val text = withContext(Dispatchers.IO) {
                    val input = resolver.openInputStream(uri) ?: error("无法读取所选文件")
                    input.use { it.readBytes().decodeToString() }
                }
                withContext(Dispatchers.IO) { backupManager.importJson(text) }
            }
            result
                .onSuccess { c ->
                    _settings.value = repository.getSettings()
                    withContext(Dispatchers.IO) { runCatching { reminderScheduler.rescheduleAll() } }
                    refreshBackupInfo()
                    _message.value =
                        "导入完成：选项 ${c.items}、记录 ${c.records}、备注 ${c.dayNotes}、分组 ${c.groups}、提醒 ${c.reminders}"
                }
                .onFailure { _message.value = "导入失败：${it.message ?: "备份数据不正确"}" }
        }
    }

    fun consumeMessage() {
        _message.value = null
    }
}
