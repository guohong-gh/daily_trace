package com.gh.daily_trace.domain.calendar

import com.gh.daily_trace.data.model.CheckItem
import com.gh.daily_trace.data.model.CheckRecord
import com.gh.daily_trace.data.model.FilterView
import com.gh.daily_trace.data.model.FrequencyRule
import com.gh.daily_trace.data.model.RecordStatus
import com.gh.daily_trace.domain.filter.FilterMatcher

/** 周视图格子状态（同项同日合一；优先级：完成 > 部分 > 计划 > 跳过） */
enum class WeekCellStatus { DONE, PARTIAL, PLANNED, SKIPPED, NONE }

/** 周视图单格：日期 + 状态 */
data class WeekCell(val date: Long, val status: WeekCellStatus)

/** 周视图一行：一个选项 + 周内 7 天状态 */
data class WeekRow(
    val itemId: Long,
    val name: String,
    val color: Int,
    val icon: String,
    val cells: List<WeekCell>,
)

/**
 * 周视图矩阵（D34）：行 = 未删除且命中筛选的选项（管理顺序），列 = 传入的 7 天。
 * - 状态优先级：DONE > PARTIAL > PLANNED > SKIPPED（记录存在时以记录状态为准，跳过不覆盖记录）；
 * - 跳过 = 规则 allowSkip 且当天在 skipDates；
 * - 筛选沿用 D31 口径（`FilterMatcher.itemMatches`，即选项/分组范围），回收站选项不作为行。
 */
object WeekMatrix {

    fun build(
        days: List<Long>,
        records: List<CheckRecord>,
        rules: List<FrequencyRule>,
        items: List<CheckItem>,
        filter: FilterView?,
    ): List<WeekRow> {
        val ruleByItem = rules.associateBy { it.itemId }
        val recordsByItemDay = records.groupBy { it.itemId to it.date }
        return items
            .filter { it.deletedAt == null }
            .filter { filter == null || FilterMatcher.itemMatches(it, filter) }
            .map { item ->
                val rule = ruleByItem[item.id]
                val cells = days.map { day ->
                    val dayRecords = recordsByItemDay[item.id to day].orEmpty()
                    val status = when {
                        dayRecords.any { it.status == RecordStatus.DONE } -> WeekCellStatus.DONE
                        dayRecords.any { it.status == RecordStatus.PARTIAL } -> WeekCellStatus.PARTIAL
                        dayRecords.any { it.status == RecordStatus.PLANNED } -> WeekCellStatus.PLANNED
                        rule != null && rule.allowSkip && day in rule.skipDates -> WeekCellStatus.SKIPPED
                        else -> WeekCellStatus.NONE
                    }
                    WeekCell(date = day, status = status)
                }
                WeekRow(
                    itemId = item.id,
                    name = item.name,
                    color = item.color,
                    icon = item.icon,
                    cells = cells,
                )
            }
    }
}
