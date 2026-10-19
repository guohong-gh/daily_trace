package com.gh.daily_trace.domain.checkin

import com.gh.daily_trace.data.model.CheckItem
import com.gh.daily_trace.data.model.CheckRecord
import com.gh.daily_trace.data.model.CheckType

/**
 * 批量打卡/补卡取值口径（默认值直记）：
 * - BOOLEAN / COUNT → 1.0（布尔 = 完成；计数 = +1）；
 * - DURATION / VALUE → 目标值（targetValue）→ 截至目标日期的最近一条记录值；
 *   两者皆无时返回 null，表示该选项暂不可批量（需先单笔记一次）。
 *
 * 实际写入仍复用 [CheckInPlanner] 的单笔逻辑（multiRecordRule / 计划 / 补卡一致）。
 */
object BatchCheckIn {

    fun valueFor(item: CheckItem, latestRecordUpTo: CheckRecord?): Double? = when (item.type) {
        CheckType.BOOLEAN, CheckType.COUNT -> 1.0
        CheckType.DURATION, CheckType.VALUE -> item.targetValue ?: latestRecordUpTo?.value
    }

    fun canBatch(item: CheckItem, latestRecordUpTo: CheckRecord?): Boolean =
        valueFor(item, latestRecordUpTo) != null
}

/** 批量记录结果：成功写入与跳过（已完成/不可批量/超出窗口）数量 */
data class BatchCheckInResult(val recorded: Int, val skipped: Int)
