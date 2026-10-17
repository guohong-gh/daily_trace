package com.gh.daily_trace.backup

import android.content.Context
import com.gh.daily_trace.data.repository.DailyTraceRepository
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 本机自动备份：写入应用私有目录 files/backups/，按时间命名，保留最近 [KEEP_FILES] 份。
 * 触发点（应用启动、退到后台时检查一次）：数据只在应用内变化，因此"下次使用前保存上一轮变更"
 * 已能覆盖全部变更；免打扰后台任务框架（WorkManager）留给后续按需引入。
 * 是否到期 = 距最近一份备份 ≥ 设置间隔天数（开关关闭时不自动写）。
 */
class AutoBackup(
    context: Context,
    private val backupManager: BackupManager,
    private val repository: DailyTraceRepository,
) {

    private val dir = File(context.filesDir, "backups")

    fun listBackups(): List<File> =
        dir.listFiles { f -> f.isFile && f.name.startsWith(PREFIX) && f.name.endsWith(SUFFIX) }
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()

    fun lastBackupAt(): Long? = listBackups().firstOrNull()?.lastModified()

    /** 到期才写；manual=true（用户在设置页点「立即备份」）时忽略开关与间隔 */
    suspend fun runIfDue(now: Long = System.currentTimeMillis(), manual: Boolean = false): File? {
        if (!manual) {
            val settings = repository.getSettings()
            if (!settings.autoBackup) return null
            val intervalMs = settings.autoBackupIntervalDays.coerceAtLeast(1) * DAY_MS
            val last = lastBackupAt()
            if (last != null && now - last < intervalMs) return null
        }
        return writeBackup(now)
    }

    /** 立即写一份备份（不判断到期），返回写入的文件 */
    suspend fun writeBackup(now: Long = System.currentTimeMillis()): File {
        val json = backupManager.exportJson(now)
        if (!dir.exists()) dir.mkdirs()
        val name = PREFIX + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date(now)) + SUFFIX
        val file = File(dir, name)
        file.writeText(json)
        // 以逻辑时间作为文件时间，保证"最近备份"判定与间隔计算可预期
        file.setLastModified(now)
        prune()
        return file
    }

    /** 只保留最近 KEEP_FILES 份 */
    fun prune() {
        listBackups().drop(KEEP_FILES).forEach { it.delete() }
    }

    companion object {
        const val KEEP_FILES = 5
        private const val PREFIX = "daily_trace_"
        private const val SUFFIX = ".json"
        private const val DAY_MS = 24L * 60 * 60 * 1000
    }
}
