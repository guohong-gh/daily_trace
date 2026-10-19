package com.gh.daily_trace.domain.calendar

import com.gh.daily_trace.data.model.CheckItem
import com.gh.daily_trace.data.model.CheckRecord
import com.gh.daily_trace.data.model.DayNote
import com.gh.daily_trace.data.model.FilterView
import com.gh.daily_trace.data.model.FrequencyRule

/** 时间线单日：日期 + 当日摘要（条目/休息日/备注，复用 D30 口径） */
data class TimelineDay(
    val date: Long,
    val summary: DaySummary,
)

/**
 * 时间线日流（D36）：
 * - 只保留窗口 [oldest, newest] 内「有内容」的天（命中筛选的记录 / 休息日登记 / 日期备注），按日期倒序；
 * - 内容与筛选口径完全复用 [CalendarMarks]（D29/D30/D31：忽略 dayFilter、已清除选项兜底一致）；
 * - 空日不出现；窗口外记录不参与（与「加载更早」30 天分页一致）。
 */
object TimelineFeed {

    fun build(
        oldest: Long,
        newest: Long,
        records: List<CheckRecord>,
        notes: List<DayNote>,
        rules: List<FrequencyRule>,
        items: List<CheckItem>,
        filter: FilterView?,
    ): List<TimelineDay> {
        if (oldest > newest) return emptyList()
        return CalendarMarks.build(records, notes, rules, items, filter)
            .summaries
            .filterKeys { it in oldest..newest }
            .map { (date, summary) -> TimelineDay(date = date, summary = summary) }
            .sortedByDescending { it.date }
    }
}
