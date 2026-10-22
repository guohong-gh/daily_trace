package com.gh.daily_trace.diagnostics

import androidx.room.withTransaction
import com.gh.daily_trace.backup.AutoBackup
import com.gh.daily_trace.backup.BackupManager
import com.gh.daily_trace.backup.ImportCounts
import com.gh.daily_trace.data.DailyTraceDatabase
import com.gh.daily_trace.data.model.AppSettings
import com.gh.daily_trace.data.model.CheckItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.ZoneId

/**
 * 数据检查结果（M10-7）。
 *
 * [orphanRecords] 是选项已被彻底删除后留下的历史记录：保留只读展示，不自动删除。
 * 其余项都是可安全修复的引用 / 设置一致性问题。
 */
data class DataHealth(
    val items: Int = 0,
    val activeItems: Int = 0,
    val records: Int = 0,
    val notes: Int = 0,
    val groups: Int = 0,
    val rules: Int = 0,
    val reminders: Int = 0,
    val filterViews: Int = 0,
    val orphanRecords: Int = 0,
    val orphanRules: Int = 0,
    val orphanReminders: Int = 0,
    val invalidGroupRefs: Int = 0,
    val emptyNotes: Int = 0,
    val staleFilterViews: Int = 0,
    val settingsMissing: Boolean = false,
    val settingsIssues: Int = 0,
    val quickCheck: String = "ok",
) {
    val integrityOk: Boolean get() = quickCheck == "ok"

    val fixableIssues: Int
        get() = orphanRules + orphanReminders + invalidGroupRefs + emptyNotes +
            staleFilterViews + settingsIssues + if (settingsMissing) 1 else 0

    val totalIssues: Int get() = fixableIssues + orphanRecords
}

data class DataRepairResult(
    val deletedRules: Int = 0,
    val deletedReminders: Int = 0,
    val clearedGroupRefs: Int = 0,
    val deletedNotes: Int = 0,
    val cleanedFilters: Int = 0,
    val settingsReset: Boolean = false,
    val settingsNormalized: Boolean = false,
    val orphanRecordsKept: Int = 0,
)

/**
 * 本地数据修复（M10-7）：
 * - 只修可由数据自身推导的不一致：孤立规则 / 提醒、无效分组引用、空日期备注、
 *   筛选视图中的失效 id、缺失或越界的设置。
 * - 不删除打卡历史；选项彻底删除后的孤立记录保留，仅统计给用户看。
 * - 提供从最近一份本机自动备份恢复的入口，作为大面积数据异常时的兜底。
 * - 不改变 schema、CSV / 备份格式。
 */
class DataRepairManager(
    private val database: DailyTraceDatabase,
    private val backupManager: BackupManager,
    private val autoBackup: AutoBackup,
    private val now: () -> Long = System::currentTimeMillis,
) {

    suspend fun scan(): DataHealth = withContext(Dispatchers.IO) {
        val items = database.checkItemDao().getAllIncludingTrashed()
        val records = database.checkRecordDao().getAll()
        val notes = database.dayNoteDao().getAll()
        val groups = database.groupDao().getAll()
        val rules = database.frequencyRuleDao().getAll()
        val reminders = database.reminderDao().getAll()
        val filterViews = database.filterViewDao().getAll()
        val settings = database.appSettingsDao().get()

        val itemIds = items.mapTo(HashSet()) { it.id }
        val groupIds = groups.mapTo(HashSet()) { it.id }
        val settingsNormalized = settings?.let { normalize(it, now()) }

        DataHealth(
            items = items.size,
            activeItems = items.count { it.deletedAt == null && it.enabled && !it.archived },
            records = records.size,
            notes = notes.size,
            groups = groups.size,
            rules = rules.size,
            reminders = reminders.size,
            filterViews = filterViews.size,
            orphanRecords = records.count { it.itemId !in itemIds },
            orphanRules = rules.count { it.itemId !in itemIds },
            orphanReminders = reminders.count { it.itemId !in itemIds },
            invalidGroupRefs = items.count { it.groupId != null && it.groupId !in groupIds },
            emptyNotes = notes.count { it.note.isBlank() },
            staleFilterViews = filterViews.count { view ->
                view.itemIds.any { it !in itemIds } || view.groupIds.any { it !in groupIds }
            },
            settingsMissing = settings == null,
            settingsIssues = if (settings != null && settingsNormalized != settings) 1 else 0,
            quickCheck = quickCheck(),
        )
    }

    suspend fun repair(): DataRepairResult = withContext(Dispatchers.IO) {
        database.withTransaction {
            val items = database.checkItemDao().getAllIncludingTrashed()
            val notes = database.dayNoteDao().getAll()
            val groups = database.groupDao().getAll()
            val rules = database.frequencyRuleDao().getAll()
            val reminders = database.reminderDao().getAll()
            val filterViews = database.filterViewDao().getAll()
            val settings = database.appSettingsDao().get()
            val itemIds = items.mapTo(HashSet()) { it.id }
            val groupIds = groups.mapTo(HashSet()) { it.id }
            val timestamp = now()

            val orphanRuleIds = rules.filter { it.itemId !in itemIds }.map { it.id }
            if (orphanRuleIds.isNotEmpty()) {
                database.frequencyRuleDao().deleteByIds(orphanRuleIds)
            }
            val orphanReminderIds = reminders.filter { it.itemId !in itemIds }.map { it.id }
            if (orphanReminderIds.isNotEmpty()) {
                database.reminderDao().deleteByIds(orphanReminderIds)
            }

            var clearedGroupRefs = 0
            items.filter { it.groupId != null && it.groupId !in groupIds }.forEach { item ->
                database.checkItemDao().update(item.copy(groupId = null, updatedAt = timestamp))
                clearedGroupRefs++
            }

            var deletedNotes = 0
            notes.filter { it.note.isBlank() }.forEach { note ->
                database.dayNoteDao().delete(note.date)
                deletedNotes++
            }

            var cleanedFilters = 0
            filterViews.forEach { view ->
                val cleaned = view.copy(
                    itemIds = view.itemIds.filter { it in itemIds },
                    groupIds = view.groupIds.filter { it in groupIds },
                )
                if (cleaned != view) {
                    database.filterViewDao().update(cleaned)
                    cleanedFilters++
                }
            }

            val normalized = settings?.let { normalize(it, timestamp) }
            val settingsReset = settings == null
            if (settingsReset) {
                database.appSettingsDao().upsert(AppSettings())
            } else if (normalized != null && normalized != settings) {
                database.appSettingsDao().upsert(normalized)
            }

            DataRepairResult(
                deletedRules = orphanRuleIds.size,
                deletedReminders = orphanReminderIds.size,
                clearedGroupRefs = clearedGroupRefs,
                deletedNotes = deletedNotes,
                cleanedFilters = cleanedFilters,
                settingsReset = settingsReset,
                settingsNormalized = normalized != null && normalized != settings,
                orphanRecordsKept = database.checkRecordDao().getAll()
                    .count { it.itemId !in itemIds },
            )
        }
    }

    /** 最近一份本机备份；没有备份时返回 null */
    suspend fun restoreLatestBackup(): ImportCounts? = withContext(Dispatchers.IO) {
        val file = autoBackup.listBackups().firstOrNull() ?: return@withContext null
        backupManager.importJson(file.readText(Charsets.UTF_8))
    }

    private fun normalize(settings: AppSettings, now: Long): AppSettings {
        val default = AppSettings()
        val timezone = settings.timezone.takeIf { zone ->
            zone.isNotBlank() && runCatching { ZoneId.of(zone) }.isSuccess
        } ?: default.timezone
        return settings.copy(
            weekStart = settings.weekStart.coerceIn(1, 7),
            startOfDay = settings.startOfDay.coerceIn(0, 23),
            timezone = timezone,
            maxBackfillDays = settings.maxBackfillDays.coerceAtLeast(0),
            autoBackupIntervalDays = settings.autoBackupIntervalDays.coerceAtLeast(1),
            dailySummaryTime = settings.dailySummaryTime.coerceIn(0, 24 * 60 - 1),
            pausedUntil = settings.pausedUntil?.takeIf { it > now },
        )
    }

    private fun quickCheck(): String = runCatching {
        database.openHelper.writableDatabase.query("PRAGMA quick_check").use { cursor ->
            val values = buildList {
                while (cursor.moveToNext()) add(cursor.getString(0))
            }
            values.joinToString("; ").ifBlank { "ok" }
        }
    }.getOrElse { "检查失败：${it.message ?: it.javaClass.simpleName}" }
}
