package com.gh.daily_trace.domain.stats

import com.gh.daily_trace.data.model.CheckRecord

/**
 * 打卡记录的日用索引：按日期、以及「日期 + 选项」分组。
 *
 * 统计页会在 500 选项 / 数万记录下反复查询同一天的某个选项；
 * 若每次都从当天全部记录中过滤，复杂度会退化为 O(选项数 × 记录数)。
 * 这里一次遍历建索引，让逐项统计只做 O(选项数 × 天数) 次哈希查找。
 */
class DailyRecordIndex private constructor(
    /** 某天全部记录，供整日聚合使用 */
    val byDay: Map<Long, List<CheckRecord>>,
    /** 某天某选项的记录，供逐项统计使用 */
    private val byDayItem: Map<Long, Map<Long, List<CheckRecord>>>,
    /** 每个选项的最早记录日，没有记录的选项不出现 */
    val firstDayByItem: Map<Long, Long>,
) {

    fun recordsOn(day: Long): List<CheckRecord> = byDay[day].orEmpty()

    fun recordsOn(day: Long, itemId: Long): List<CheckRecord> =
        byDayItem[day]?.get(itemId).orEmpty()

    companion object {

        fun from(records: List<CheckRecord>): DailyRecordIndex {
            val byDay = LinkedHashMap<Long, MutableList<CheckRecord>>()
            val byDayItem = HashMap<Long, MutableMap<Long, MutableList<CheckRecord>>>()
            val firstDayByItem = HashMap<Long, Long>()
            for (record in records) {
                byDay.getOrPut(record.date) { ArrayList() }.add(record)
                byDayItem
                    .getOrPut(record.date) { HashMap() }
                    .getOrPut(record.itemId) { ArrayList() }
                    .add(record)
                firstDayByItem.merge(record.itemId, record.date, ::minOf)
            }
            return DailyRecordIndex(byDay, byDayItem, firstDayByItem)
        }

        /** 兼容仍以「按日分组」形式持有的调用方（测试与旧接口）。 */
        fun fromByDay(recordsByDay: Map<Long, List<CheckRecord>>): DailyRecordIndex {
            val byDayItem = HashMap<Long, MutableMap<Long, MutableList<CheckRecord>>>()
            val firstDayByItem = HashMap<Long, Long>()
            for ((day, records) in recordsByDay) {
                val dayIndex = byDayItem.getOrPut(day) { HashMap() }
                for (record in records) {
                    dayIndex.getOrPut(record.itemId) { ArrayList() }.add(record)
                    firstDayByItem.merge(record.itemId, day, ::minOf)
                }
            }
            val sortedByDay = recordsByDay.toSortedMap()
            return DailyRecordIndex(sortedByDay, byDayItem, firstDayByItem)
        }
    }
}
