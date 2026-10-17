package com.gh.daily_trace.backup

import androidx.room.withTransaction
import com.gh.daily_trace.data.DailyTraceDatabase

/** 导入结果计数（用于界面提示） */
data class ImportCounts(
    val items: Int,
    val records: Int,
    val dayNotes: Int,
    val groups: Int,
    val reminders: Int,
    val filterViews: Int,
    val frequencyRules: Int,
)

/**
 * 备份导入导出（数据库层）。
 * - 导出：读取全部表（选项含回收站）编码为 JSON 字符串。
 * - 导入：先解析校验（失败抛 [BackupFormatException]，库内数据不受影响），
 *   再在同一事务内清空各表并写入，任一失败整体回滚。
 */
class BackupManager(private val database: DailyTraceDatabase) {

    suspend fun exportJson(now: Long = System.currentTimeMillis()): String {
        val data = BackupData(
            exportedAt = now,
            items = database.checkItemDao().getAllIncludingTrashed(),
            records = database.checkRecordDao().getAll(),
            dayNotes = database.dayNoteDao().getAll(),
            groups = database.groupDao().getAll(),
            reminders = database.reminderDao().getAll(),
            filterViews = database.filterViewDao().getAll(),
            frequencyRules = database.frequencyRuleDao().getAll(),
            settings = database.appSettingsDao().get(),
        )
        return BackupCodec.encode(data)
    }

    /** 全量替换导入；返回各表计数。数据非法时抛 [BackupFormatException] */
    suspend fun importJson(json: String): ImportCounts {
        val data = BackupCodec.parse(json)
        database.withTransaction {
            database.checkRecordDao().deleteAll()
            database.checkItemDao().deleteAll()
            database.dayNoteDao().deleteAll()
            database.groupDao().deleteAll()
            database.reminderDao().deleteAll()
            database.filterViewDao().deleteAll()
            database.frequencyRuleDao().deleteAll()

            database.checkItemDao().insertAll(data.items)
            database.checkRecordDao().insertAll(data.records)
            database.dayNoteDao().insertAll(data.dayNotes)
            database.groupDao().insertAll(data.groups)
            database.reminderDao().insertAll(data.reminders)
            database.filterViewDao().insertAll(data.filterViews)
            database.frequencyRuleDao().insertAll(data.frequencyRules)
            if (data.settings != null) {
                database.appSettingsDao().upsert(data.settings)
            }
        }
        return ImportCounts(
            items = data.items.size,
            records = data.records.size,
            dayNotes = data.dayNotes.size,
            groups = data.groups.size,
            reminders = data.reminders.size,
            filterViews = data.filterViews.size,
            frequencyRules = data.frequencyRules.size,
        )
    }
}
