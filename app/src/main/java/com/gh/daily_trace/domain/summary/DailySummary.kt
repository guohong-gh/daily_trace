package com.gh.daily_trace.domain.summary

import com.gh.daily_trace.data.model.CheckItem
import com.gh.daily_trace.data.model.CheckRecord
import com.gh.daily_trace.data.model.RecordStatus
import java.time.Instant
import java.time.ZoneId

/**
 * 当日打卡汇总（M9-3）。
 * - 分母 = 在册选项（启用、未归档、未删除）；
 * - 分子 = 当天有 DONE 记录的去重选项数（一个选项多条只算一次）。
 */
data class DailySummary(val done: Int, val total: Int) {
    val pending: Int get() = (total - done).coerceAtLeast(0)
    val allDone: Boolean get() = total > 0 && pending == 0
}

fun buildDailySummary(items: List<CheckItem>, records: List<CheckRecord>): DailySummary {
    val activeIds = items.asSequence()
        .filter { it.deletedAt == null && !it.archived && it.enabled }
        .map { it.id }
        .toSet()
    val doneIds = records.asSequence()
        .filter { it.status == RecordStatus.DONE && it.itemId in activeIds }
        .map { it.itemId }
        .toSet()
    return DailySummary(done = doneIds.size, total = activeIds.size)
}

/** 汇总通知文案（纯函数，便于测试）。 */
fun summaryMessage(summary: DailySummary): String = when {
    summary.total == 0 -> "今天还没有可打卡的选项"
    summary.allDone -> "今天 ${summary.total} 项已全部完成，太棒了 🎉"
    else -> "今天已打卡 ${summary.done} / ${summary.total} 项，还有 ${summary.pending} 项未完成"
}

/**
 * 下一次汇总时刻（epoch millis）：今天 [timeMinutes] 未过则今天，否则明天。
 * 时刻按 [zone]（即 AppSettings.timezone）解释，与提醒时刻口径一致。
 */
fun nextSummaryMillis(nowMillis: Long, timeMinutes: Int, zone: ZoneId): Long {
    val now = Instant.ofEpochMilli(nowMillis).atZone(zone)
    val minuteOfDay = timeMinutes.coerceIn(0, 24 * 60 - 1)
    var target = now.toLocalDate().atTime(minuteOfDay / 60, minuteOfDay % 60).atZone(zone)
    if (!target.toInstant().isAfter(now.toInstant())) {
        target = target.plusDays(1)
    }
    return target.toInstant().toEpochMilli()
}
