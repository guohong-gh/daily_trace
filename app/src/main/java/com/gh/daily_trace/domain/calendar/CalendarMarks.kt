package com.gh.daily_trace.domain.calendar

import com.gh.daily_trace.data.model.CheckItem
import com.gh.daily_trace.data.model.CheckRecord
import com.gh.daily_trace.data.model.CheckType
import com.gh.daily_trace.data.model.DayNote
import com.gh.daily_trace.data.model.FilterView
import com.gh.daily_trace.data.model.FrequencyRule
import com.gh.daily_trace.data.model.RecordStatus

/** 单日标记：逐选项圆点色（快照）+ 溢出数（「+N」）+ 备注标记 */
data class DayMarks(
    val dotColors: List<Int>,
    val overflow: Int,
    val hasNote: Boolean,
)

/** 长按摘要中的单条记录（一律用快照字段，D11） */
data class SummaryEntry(
    val itemId: Long,
    val name: String,
    val color: Int,
    val icon: String,
    /** 当前选项类型；选项已清除时为 null（值按原样展示） */
    val type: CheckType?,
    val value: Double?,
    val unit: String,
    val status: RecordStatus,
)

/** 单日摘要（长按浮层数据：记录条目 + 休息日 + 日期备注） */
data class DaySummary(
    val entries: List<SummaryEntry>,
    val skipNames: List<String>,
    val noteText: String?,
) {
    val isEmpty: Boolean get() = entries.isEmpty() && skipNames.isEmpty() && noteText == null
}

data class CalendarMarksResult(
    val marks: Map<Long, DayMarks> = emptyMap(),
    val summaries: Map<Long, DaySummary> = emptyMap(),
)

/**
 * 日历标记与长按摘要计算（D29/D31）：
 * - 圆点 = 当天 DONE / PARTIAL 记录的去重快照色（同项同日合一，取最新一条的颜色），
 *   按选项管理顺序排列，最多 [MAX_DOTS] 个，其余计入 overflow 展示「+N」；
 * - PLANNED / SKIPPED 不产生圆点（跳过日不单独标记），但会出现在长按摘要里；
 * - 备注标记为日级信息，不受筛选影响；
 * - 筛选只联动「选项/分组范围」（FilterMatcher.itemMatches，忽略 dayFilter），过滤圆点、摘要条目与休息日名单。
 */
object CalendarMarks {

    const val MAX_DOTS = 4

    fun build(
        records: List<CheckRecord>,
        notes: List<DayNote>,
        rules: List<FrequencyRule>,
        items: List<CheckItem>,
        filter: FilterView?,
    ): CalendarMarksResult {
        val itemById = items.associateBy { it.id }
        val orderById = items.withIndex().associate { (index, item) -> item.id to index }

        val recordsByDay = records.groupBy { it.date }
        val noteByDay = notes.associateBy { it.date }

        // 摘要条目：按选项管理顺序，其内按记录产生时间
        val entriesByDay = recordsByDay
            .mapValues { (_, dayRecords) ->
                dayRecords
                    .filter { ItemScope.passes(it.itemId, itemById, filter) }
                    .sortedWith(
                        compareBy(
                            { orderById[it.itemId] ?: Int.MAX_VALUE },
                            { it.createdAt },
                            { it.id },
                        )
                    )
                    .map { record ->
                        SummaryEntry(
                            itemId = record.itemId,
                            name = record.itemNameSnapshot,
                            color = record.itemColorSnapshot,
                            icon = record.itemIconSnapshot,
                            type = itemById[record.itemId]?.type,
                            value = record.value,
                            unit = record.unit,
                            status = record.status,
                        )
                    }
            }
            .filterValues { it.isNotEmpty() }

        // 休息日登记：allowSkip 且当天在 skipDates；名称取当前选项名，按选项管理顺序
        val skipNamesByDay = LinkedHashMap<Long, MutableList<String>>()
        rules.asSequence()
            .filter { it.allowSkip }
            .sortedBy { orderById[it.itemId] ?: Int.MAX_VALUE }
            .forEach { rule ->
                val name = itemById[rule.itemId]?.name
                    ?.takeIf { ItemScope.passes(rule.itemId, itemById, filter) } ?: return@forEach
                rule.skipDates.forEach { day ->
                    skipNamesByDay.getOrPut(day) { mutableListOf() }.add(name)
                }
            }

        // 圆点：去重（同项同日合一），色取该选项当天最新一条记录的快照色
        val dotsByDay = recordsByDay
            .mapValues { (_, dayRecords) ->
                val colorByItem = LinkedHashMap<Long, Int>()
                dayRecords
                    .filter {
                        (it.status == RecordStatus.DONE || it.status == RecordStatus.PARTIAL) &&
                            ItemScope.passes(it.itemId, itemById, filter)
                    }
                    .sortedWith(compareBy({ it.createdAt }, { it.id }))
                    .forEach { colorByItem[it.itemId] = it.itemColorSnapshot }
                colorByItem.keys
                    .sortedBy { orderById[it] ?: Int.MAX_VALUE }
                    .map { colorByItem.getValue(it) }
            }
            .filterValues { it.isNotEmpty() }

        val summaryDays = LinkedHashSet<Long>()
        summaryDays += entriesByDay.keys
        summaryDays += skipNamesByDay.keys
        summaryDays += noteByDay.keys

        val marks = LinkedHashMap<Long, DayMarks>()
        for (day in summaryDays) {
            val dots = dotsByDay[day].orEmpty()
            val hasNote = noteByDay[day]?.note?.isNotBlank() == true
            if (dots.isNotEmpty() || hasNote) {
                marks[day] = DayMarks(
                    dotColors = dots.take(MAX_DOTS),
                    overflow = (dots.size - MAX_DOTS).coerceAtLeast(0),
                    hasNote = hasNote,
                )
            }
        }

        val summaries = summaryDays
            .associateWith { day ->
                DaySummary(
                    entries = entriesByDay[day].orEmpty(),
                    skipNames = skipNamesByDay[day].orEmpty(),
                    noteText = noteByDay[day]?.note?.takeIf { it.isNotBlank() },
                )
            }
            .filterValues { !it.isEmpty }

        return CalendarMarksResult(marks = marks, summaries = summaries)
    }
}
