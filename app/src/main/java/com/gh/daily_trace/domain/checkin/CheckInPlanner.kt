package com.gh.daily_trace.domain.checkin

import com.gh.daily_trace.data.model.CheckItem
import com.gh.daily_trace.data.model.CheckRecord
import com.gh.daily_trace.data.model.CheckType
import com.gh.daily_trace.data.model.MultiRecordRule
import com.gh.daily_trace.data.model.RecordSource
import com.gh.daily_trace.data.model.RecordStatus
import com.gh.daily_trace.data.repository.DailyTraceRepository

/**
 * 打卡写入计划：纯数据描述（不触碰数据库），规则可穷举单测。
 * 执行由 [applyPlan] 落库；先更新、再删除、最后插入。
 */
data class CheckInPlan(
    val inserts: List<CheckRecord> = emptyList(),
    val updates: List<CheckRecord> = emptyList(),
    val deletes: List<Long> = emptyList(),
) {
    val isEmpty: Boolean get() = inserts.isEmpty() && updates.isEmpty() && deletes.isEmpty()
}

/**
 * 打卡语义（与产品假设一致，见 docs/status-and-plan.md）：
 * - toggleBoolean：布尔型快速打卡；今天已有记录 → 全部取消；否则新增一条。
 * - addValue：
 *   - BOOLEAN：新增（无记录）或更新最新一条备注；
 *   - KEEP_MULTIPLE：始终追加新记录；
 *   - MERGE：更新最新一条；输入值非空则覆盖，备注非空则覆盖；
 *   - ACCUMULATE：最新一条累加输入值（输入为空则保留原值）。
 */
object CheckInPlanner {

    fun toggleBoolean(
        item: CheckItem,
        date: Long,
        existing: List<CheckRecord>,
        timezone: String,
        isBackfill: Boolean,
        now: Long,
    ): CheckInPlan =
        if (existing.isNotEmpty()) {
            CheckInPlan(deletes = existing.map { it.id })
        } else {
            CheckInPlan(inserts = listOf(newRecord(item, date, 1.0, "", timezone, isBackfill, now)))
        }

    fun addValue(
        item: CheckItem,
        date: Long,
        existing: List<CheckRecord>,
        inputValue: Double?,
        note: String,
        timezone: String,
        isBackfill: Boolean,
        now: Long,
    ): CheckInPlan {
        val latest = latestOf(existing)

        if (item.type == CheckType.BOOLEAN) {
            return if (latest == null) {
                CheckInPlan(inserts = listOf(newRecord(item, date, 1.0, note, timezone, isBackfill, now)))
            } else {
                CheckInPlan(updates = listOf(latest.copy(note = note, updatedAt = now)))
            }
        }

        return when (item.multiRecordRule) {
            MultiRecordRule.KEEP_MULTIPLE ->
                CheckInPlan(inserts = listOf(newRecord(item, date, inputValue, note, timezone, isBackfill, now)))

            MultiRecordRule.MERGE ->
                if (latest == null) {
                    CheckInPlan(inserts = listOf(newRecord(item, date, inputValue, note, timezone, isBackfill, now)))
                } else {
                    CheckInPlan(
                        updates = listOf(
                            latest.copy(
                                value = inputValue ?: latest.value,
                                note = note.ifBlank { latest.note },
                                updatedAt = now,
                            )
                        )
                    )
                }

            MultiRecordRule.ACCUMULATE ->
                if (latest == null) {
                    CheckInPlan(inserts = listOf(newRecord(item, date, inputValue, note, timezone, isBackfill, now)))
                } else {
                    val newValue = if (inputValue != null) (latest.value ?: 0.0) + inputValue else latest.value
                    CheckInPlan(
                        updates = listOf(
                            latest.copy(
                                value = newValue,
                                note = note.ifBlank { latest.note },
                                updatedAt = now,
                            )
                        )
                    )
                }
        }
    }

    /** 删除单条记录 */
    fun deleteRecord(recordId: Long): CheckInPlan = CheckInPlan(deletes = listOf(recordId))

    /** 撤销最近一笔（按 createdAt、id 取最新） */
    fun deleteLatest(existing: List<CheckRecord>): CheckInPlan =
        latestOf(existing)?.let { CheckInPlan(deletes = listOf(it.id)) } ?: CheckInPlan()

    private fun latestOf(records: List<CheckRecord>): CheckRecord? =
        records.maxWithOrNull(compareBy({ it.createdAt }, { it.id }))

    private fun newRecord(
        item: CheckItem,
        date: Long,
        value: Double?,
        note: String,
        timezone: String,
        isBackfill: Boolean,
        now: Long,
    ): CheckRecord = CheckRecord(
        itemId = item.id,
        date = date,
        value = value,
        unit = item.unit,
        note = note,
        status = RecordStatus.DONE,
        source = RecordSource.MANUAL,
        isBackfill = isBackfill,
        timezone = timezone,
        createdAt = now,
        updatedAt = now,
        itemNameSnapshot = item.name,
        itemColorSnapshot = item.color,
        itemIconSnapshot = item.icon,
    )
}

/** 将计划落库 */
suspend fun applyPlan(repository: DailyTraceRepository, plan: CheckInPlan) {
    if (plan.isEmpty) return
    plan.updates.forEach { repository.updateRecord(it) }
    if (plan.deletes.isNotEmpty()) repository.deleteRecords(plan.deletes)
    plan.inserts.forEach { repository.insertRecord(it) }
}
