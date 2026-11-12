package com.gh.daily_trace.ui.settings

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gh.daily_trace.backup.AutoBackup
import com.gh.daily_trace.backup.BackupManager
import com.gh.daily_trace.data.model.AppSettings
import com.gh.daily_trace.data.repository.DailyTraceRepository
import com.gh.daily_trace.diagnostics.CrashLogManager
import com.gh.daily_trace.diagnostics.DataHealth
import com.gh.daily_trace.diagnostics.DataRepairManager
import com.gh.daily_trace.domain.csv.CsvExporter
import com.gh.daily_trace.domain.reminder.ReminderPause
import com.gh.daily_trace.reminder.DailySummaryScheduler
import com.gh.daily_trace.reminder.ReminderScheduler
import com.gh.daily_trace.update.UpdateManager
import com.gh.daily_trace.update.UpdateStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 本机备份概况：份数与最近一次时间 */
data class BackupInfo(val count: Int = 0, val lastAt: Long? = null)

/** 本机崩溃日志概况 */
data class CrashInfo(val count: Int = 0, val lastAt: Long? = null)

/**
 * 设置页：读改写即时保存（单写者，乐观更新）。
 * 备份区块：导出（SAF 目标 URI）、导入（覆盖式，完成后重排提醒）、本机自动备份。
 */
class SettingsViewModel(
    private val repository: DailyTraceRepository,
    private val backupManager: BackupManager,
    private val autoBackup: AutoBackup,
    private val dataRepair: DataRepairManager,
    private val crashLogs: CrashLogManager,
    private val reminderScheduler: ReminderScheduler,
    private val dailySummaryScheduler: DailySummaryScheduler,
    private val updateManager: UpdateManager,
) : ViewModel() {

    private val _settings = MutableStateFlow(AppSettings())
    val settings: StateFlow<AppSettings> = _settings

    private val _backupInfo = MutableStateFlow(BackupInfo())
    val backupInfo: StateFlow<BackupInfo> = _backupInfo

    private val _crashInfo = MutableStateFlow(CrashInfo())
    val crashInfo: StateFlow<CrashInfo> = _crashInfo

    private val _dataHealth = MutableStateFlow<DataHealth?>(null)
    val dataHealth: StateFlow<DataHealth?> = _dataHealth

    private val _checkingData = MutableStateFlow(false)
    val checkingData: StateFlow<Boolean> = _checkingData

    /** 一次性提示（Snackbar）；展示后调用 [consumeMessage] */
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    /** 版本检查状态（冷启动与手动检查共用同一状态） */
    val updateStatus: StateFlow<UpdateStatus> = updateManager.status
    val lastUpdateCheckAt: StateFlow<Long?> = updateManager.lastCheckAt
    val currentVersion: String = updateManager.currentVersion

    /** 设置页手动检查：不受自动弹窗节流限制 */
    fun checkUpdates() {
        viewModelScope.launch { updateManager.checkManually() }
    }

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
        refreshCrashInfo()
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

    /** 每日汇总开关：先落库，再按最新设置重建 WorkManager 周期任务 */
    fun setDailySummaryEnabled(enabled: Boolean) {
        val next = _settings.value.copy(dailySummaryEnabled = enabled)
        _settings.value = next
        viewModelScope.launch {
            repository.saveSettings(next)
            runCatching { withContext(Dispatchers.IO) { dailySummaryScheduler.sync(next) } }
                .onFailure { _message.value = "每日汇总调度失败：${it.message ?: "未知错误"}" }
        }
    }

    /** 每日汇总时刻（当地分钟数）：开关开启时立即按新时刻重排 */
    fun setDailySummaryTime(minutes: Int) {
        val next = _settings.value.copy(dailySummaryTime = minutes.coerceIn(0, 24 * 60 - 1))
        _settings.value = next
        viewModelScope.launch {
            repository.saveSettings(next)
            runCatching { withContext(Dispatchers.IO) { dailySummaryScheduler.sync(next) } }
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

    /** 导出全部打卡记录为 CSV；CSV 供分析，不承担完整恢复职责 */
    fun exportCsvTo(uri: Uri, resolver: ContentResolver) {
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val settings = repository.getSettings()
                    val records = repository.getAllRecords()
                    val csv = CsvExporter.buildRecordsCsv(records, settings.timezone)
                    val out = resolver.openOutputStream(uri) ?: error("无法写入所选文件")
                    out.use { it.write(csv.toByteArray()) }
                    records.size
                }
            }
                .onSuccess { count -> _message.value = "CSV 导出完成（$count 条记录）" }
                .onFailure { _message.value = "CSV 导出失败：${it.message ?: "未知错误"}" }
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
                    val importedSettings = repository.getSettings()
                    _settings.value = importedSettings
                    withContext(Dispatchers.IO) {
                        runCatching { reminderScheduler.rescheduleAll() }
                        runCatching { dailySummaryScheduler.sync(importedSettings) }
                    }
                    refreshBackupInfo()
                    _message.value =
                        "导入完成：选项 ${c.items}、记录 ${c.records}、备注 ${c.dayNotes}、分组 ${c.groups}、提醒 ${c.reminders}"
                }
                .onFailure { _message.value = "导入失败：${it.message ?: "备份数据不正确"}" }
        }
    }

    fun refreshCrashInfo() {
        viewModelScope.launch(Dispatchers.IO) {
            val files = crashLogs.list()
            _crashInfo.value = CrashInfo(count = files.size, lastAt = files.firstOrNull()?.lastModified())
        }
    }

    /** 检查数据一致性；只读扫描，由用户在结果弹窗里决定是否修复 */
    fun checkData() {
        if (_checkingData.value) return
        _checkingData.value = true
        viewModelScope.launch {
            val result = runCatching { dataRepair.scan() }
            _checkingData.value = false
            result
                .onSuccess { report ->
                    _dataHealth.value = report
                    if (!report.integrityOk) {
                        _message.value = "数据库完整性检查未通过，建议从最近本机备份恢复"
                    } else if (report.totalIssues == 0) {
                        _message.value = "数据检查通过：未发现异常"
                    }
                }
                .onFailure { _message.value = "数据检查失败：${it.message ?: "未知错误"}" }
        }
    }

    fun dismissDataHealth() {
        _dataHealth.value = null
    }

    /** 修复可安全处理的引用 / 设置不一致；不删除打卡历史 */
    fun repairData() {
        if (_checkingData.value) return
        _checkingData.value = true
        viewModelScope.launch {
            val result = runCatching { dataRepair.repair() }
            _checkingData.value = false
            result
                .onSuccess { r ->
                    _dataHealth.value = null
                    val repairedSettings = repository.getSettings()
                    _settings.value = repairedSettings
                    withContext(Dispatchers.IO) {
                        runCatching { reminderScheduler.rescheduleAll() }
                        runCatching { dailySummaryScheduler.sync(repairedSettings) }
                    }
                    val settingsNote = when {
                        r.settingsReset -> "、重建设置"
                        r.settingsNormalized -> "、修正设置"
                        else -> ""
                    }
                    _message.value =
                        "修复完成：规则 ${r.deletedRules}、提醒 ${r.deletedReminders}、分组引用 ${r.clearedGroupRefs}、" +
                            "空备注 ${r.deletedNotes}、筛选 ${r.cleanedFilters}$settingsNote" +
                            if (r.orphanRecordsKept > 0) "；保留历史记录 ${r.orphanRecordsKept} 条" else ""
                }
                .onFailure { _message.value = "数据修复失败：${it.message ?: "未知错误"}" }
        }
    }

    /** 用最近一份本机自动备份覆盖恢复；没有备份时提示 */
    fun restoreLatestBackup() {
        viewModelScope.launch {
            val result = runCatching { dataRepair.restoreLatestBackup() }
            result
                .onSuccess { counts ->
                    if (counts == null) {
                        _message.value = "暂无可恢复的本机备份"
                        return@onSuccess
                    }
                    _dataHealth.value = null
                    val restoredSettings = repository.getSettings()
                    _settings.value = restoredSettings
                    withContext(Dispatchers.IO) {
                        runCatching { reminderScheduler.rescheduleAll() }
                        runCatching { dailySummaryScheduler.sync(restoredSettings) }
                    }
                    refreshBackupInfo()
                    _message.value =
                        "已从最近备份恢复：选项 ${counts.items}、记录 ${counts.records}、备注 ${counts.dayNotes}、分组 ${counts.groups}"
                }
                .onFailure { _message.value = "恢复失败：${it.message ?: "备份数据不正确"}" }
        }
    }

    /** 导出全部崩溃日志到 SAF 选定的文本文件 */
    fun exportCrashLogsTo(uri: Uri, resolver: ContentResolver) {
        viewModelScope.launch {
            val result = runCatching {
                val text = withContext(Dispatchers.IO) { crashLogs.exportText() }
                if (text.isBlank()) return@runCatching 0
                withContext(Dispatchers.IO) {
                    val out = resolver.openOutputStream(uri) ?: error("无法写入所选文件")
                    out.use { it.write(text.toByteArray(Charsets.UTF_8)) }
                }
                crashLogs.list().size
            }
            result
                .onSuccess { count ->
                    _message.value = if (count == 0) "暂无崩溃日志" else "崩溃日志导出完成（$count 份）"
                }
                .onFailure { _message.value = "崩溃日志导出失败：${it.message ?: "未知错误"}" }
        }
    }

    fun clearCrashLogs() {
        viewModelScope.launch {
            val deleted = withContext(Dispatchers.IO) { crashLogs.clear() }
            refreshCrashInfo()
            _message.value = "已清除 $deleted 份崩溃日志"
        }
    }

    fun consumeMessage() {
        _message.value = null
    }
}
