package com.gh.daily_trace.domain.calendar

import com.gh.daily_trace.data.model.CheckItem
import com.gh.daily_trace.data.model.CheckRecord
import com.gh.daily_trace.data.model.FilterView
import com.gh.daily_trace.data.model.RecordStatus
import com.gh.daily_trace.domain.time.DayClock
import java.time.DayOfWeek
import java.time.LocalDate

/** 热力图单格：日期 + 是否在当年内 + 当日完成（DONE 去重）选项数 + 强度档（0..4，4 = 4 项及以上） */
data class HeatCell(
    val date: Long,
    val inYear: Boolean,
    val doneCount: Int,
    val level: Int,
)

/** 热力图一行 = 一周（7 格）；[monthStart] = 该行首次出现的月份号（无则 null，供左侧月份栏） */
data class HeatRow(
    val weekStart: Long,
    val cells: List<HeatCell>,
    val monthStart: Int?,
)

/** 年汇总（仅当年、筛选内）：[doneCount] = 去重「选项 × 天」完成项次；[activeDays] = 有完成的天数 */
data class YearSummary(val doneCount: Int, val activeDays: Int)

data class YearGrid(
    val year: Int,
    val rows: List<HeatRow>,
    val summary: YearSummary,
)

/**
 * 年热力图（D35）：
 * - 行 = 年首周 ~ 年尾周（53/54 行，周起始按设置），列 = 周内 7 天；年外格 `inYear=false` 仅供占位；
 * - 强度 = 当日「完成（DONE）」去重选项数分档 0/1/2/3/4+（沿用 D28：PARTIAL/PLANNED/SKIPPED 不计）；
 * - 筛选沿用 D31（`ItemScope`，只取选项/分组范围、忽略 dayFilter）。
 */
object YearHeatmap {

    /** 强度封顶档（4 = 4 项及以上） */
    const val MAX_LEVEL = 4

    fun build(
        year: Int,
        weekStart: DayOfWeek,
        records: List<CheckRecord>,
        items: List<CheckItem>,
        filter: FilterView?,
    ): YearGrid {
        val yearStart = LocalDate.of(year, 1, 1).toEpochDay()
        val yearEnd = LocalDate.of(year, 12, 31).toEpochDay()
        val itemById = items.associateBy { it.id }

        // 当日完成选项去重（同项同日多条合一）
        val doneItemsByDay = records
            .filter { it.status == RecordStatus.DONE && it.date in yearStart..yearEnd }
            .filter { ItemScope.passes(it.itemId, itemById, filter) }
            .groupBy { it.date }
            .mapValues { (_, dayRecords) -> dayRecords.map { it.itemId }.toSet().size }

        val firstWeekStart = DayClock.weekStartOf(yearStart, weekStart)
        val lastWeekStart = DayClock.weekStartOf(yearEnd, weekStart)
        val weekCount = ((lastWeekStart - firstWeekStart) / 7).toInt() + 1

        var lastMonth = 0
        val rows = (0 until weekCount).map { index ->
            val weekStartDay = firstWeekStart + index * 7L
            val cells = (0 until 7).map { offset ->
                val date = weekStartDay + offset
                val inYear = date in yearStart..yearEnd
                val doneCount = if (inYear) doneItemsByDay[date] ?: 0 else 0
                HeatCell(
                    date = date,
                    inYear = inYear,
                    doneCount = doneCount,
                    level = if (inYear) doneCount.coerceAtMost(MAX_LEVEL) else 0,
                )
            }
            val month = cells.firstOrNull { it.inYear }
                ?.let { LocalDate.ofEpochDay(it.date).monthValue }
            val monthStart = month?.takeIf { it != lastMonth }
            if (monthStart != null) lastMonth = monthStart
            HeatRow(weekStart = weekStartDay, cells = cells, monthStart = monthStart)
        }

        return YearGrid(
            year = year,
            rows = rows,
            summary = YearSummary(
                doneCount = doneItemsByDay.values.sum(),
                activeDays = doneItemsByDay.size,
            ),
        )
    }
}
