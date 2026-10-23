package com.gh.daily_trace.domain.stats

import com.gh.daily_trace.data.model.CheckItem
import com.gh.daily_trace.data.model.CheckRecord
import com.gh.daily_trace.data.model.FrequencyRule
import com.gh.daily_trace.data.model.FrequencyType
import com.gh.daily_trace.data.model.Group
import com.gh.daily_trace.data.model.RecordStatus
import java.time.LocalDate
import java.time.YearMonth

/** 单日完成状态（统计口径） */
enum class DayOutcome {
    DONE,
    /** 部分完成且设置未将其计入完成 */
    PARTIAL,
    SKIPPED,
    MISSED,
    NOT_EXPECTED,
}

data class RateResult(val doneDays: Int, val expectedDays: Int) {
    /** null = 范围内无期望日 */
    val rate: Double? get() = if (expectedDays == 0) null else doneDays.toDouble() / expectedDays
}

data class StreakResult(val current: Int, val best: Int)

/**
 * 聚合总量（D28）：次数只算 DONE；量值合计含 PARTIAL（过程量计入投入，与今日页口径一致）。
 * PLANNED / SKIPPED 一律不计。
 */
data class ItemTotals(
    val doneCount: Int = 0,
    val partialCount: Int = 0,
    val valueSum: Double = 0.0,
) {
    val isEmpty: Boolean get() = doneCount == 0 && partialCount == 0 && valueSum == 0.0
}

data class PeriodStat(
    val label: String,
    val startDay: Long,
    val endDay: Long,
    val doneDays: Int,
    val expectedDays: Int,
) {
    val rate: Double get() = if (expectedDays == 0) 0.0 else doneDays.toDouble() / expectedDays
}

/** 配额语义的频率类型：无日级期望，不计入完成率分母（D16 / B16 统计口径） */
val QUOTA_TYPES = setOf(FrequencyType.TIMES_PER_MONTH, FrequencyType.TIMES_PER_WEEK)

/** 参与聚合统计的选项与规则（配额类型无日级期望，被期望日模型自然排除） */
data class ItemRule(val itemId: Long, val rule: FrequencyRule?)

/**
 * 分组统计（M10-2）：组内按期望日加权，不取各选项百分比的平均值。
 * 配额类型（TIMES_PER_MONTH / TIMES_PER_WEEK）不计入分母，仅以 [quotaItemCount] 标注；未分组 / 孤儿 groupId 的 color 为 null。
 */
data class GroupRate(
    val groupId: Long?,
    val name: String,
    val color: Int?,
    val itemCount: Int,
    val quotaItemCount: Int,
    val rate: RateResult,
)

/**
 * 统计口径（D16/D24，见 docs/项目状态与计划.md）：
 * - 期望日：DAILY / 无规则 = 每天；DAYS_OF_WEEK = 所选周几；
 *   TIMES_PER_MONTH / TIMES_PER_WEEK = 无日级期望（用月度 / 周度配额）；
 *   CUSTOM = 每隔 N 天（自锚点日起算，锚点之前不计期望）。
 * - 跳过：记录 status=SKIPPED，或 rule.allowSkip 且当天在 skipDates 中；跳过日不计入期望日，也不中断连续。
 * - 记录按 itemId 过滤（传入全局按日分组的记录也能正确处理各选项互不污染）。
 * - PARTIAL 是否算完成由 partialCountsAsDone 决定。
 * - 连续打卡只遍历期望日；「今天」尚未打卡不视为中断（宽限当天）。
 * - 完成率 = 范围内已完成期望日 / 期望日；范围内无期望日时 rate = 0.0（UI 可显示 —）。
 */
object StatsCalculator {

    fun isExpectedDay(epochDay: Long, rule: FrequencyRule?): Boolean {
        val dow = LocalDate.ofEpochDay(epochDay).dayOfWeek.value
        return when (rule?.type) {
            null, FrequencyType.DAILY -> true
            FrequencyType.DAYS_OF_WEEK -> dow in rule.daysOfWeek
            FrequencyType.TIMES_PER_MONTH -> false
            FrequencyType.TIMES_PER_WEEK -> false
            FrequencyType.CUSTOM -> {
                // 每隔 N 天（自锚点日起算）；缺参数（旧数据 / 异常导入）时退化为每天
                val n = rule.intervalDays
                val anchor = rule.anchorEpochDay
                if (n == null || n <= 0 || anchor == null) true
                else epochDay >= anchor && (epochDay - anchor) % n == 0L
            }
        }
    }

    fun outcomeOf(
        itemId: Long,
        day: Long,
        rule: FrequencyRule?,
        recordsOfDay: List<CheckRecord>,
        partialCountsAsDone: Boolean,
    ): DayOutcome = outcomeOfRecords(
        itemId = itemId,
        day = day,
        rule = rule,
        mine = recordsOfDay.filter { it.itemId == itemId },
        partialCountsAsDone = partialCountsAsDone,
    )

    /** [mine] 已经按 itemId 过滤，避免索引查询路径重复扫描当天全部记录。 */
    private fun outcomeOfRecords(
        itemId: Long,
        day: Long,
        rule: FrequencyRule?,
        mine: List<CheckRecord>,
        partialCountsAsDone: Boolean,
    ): DayOutcome {
        val skipped = mine.any { it.status == RecordStatus.SKIPPED } ||
            (rule?.allowSkip == true && day in rule.skipDates)
        if (skipped) return DayOutcome.SKIPPED
        if (!isExpectedDay(day, rule)) return DayOutcome.NOT_EXPECTED
        return when {
            mine.any { it.status == RecordStatus.DONE } -> DayOutcome.DONE
            mine.any { it.status == RecordStatus.PARTIAL } ->
                if (partialCountsAsDone) DayOutcome.DONE else DayOutcome.PARTIAL
            else -> DayOutcome.MISSED
        }
    }

    fun rate(
        itemId: Long,
        rule: FrequencyRule?,
        recordsByDay: Map<Long, List<CheckRecord>>,
        startDay: Long,
        endDay: Long,
        partialCountsAsDone: Boolean,
    ): RateResult = rate(
        itemId = itemId,
        rule = rule,
        index = DailyRecordIndex.fromByDay(recordsByDay),
        startDay = startDay,
        endDay = endDay,
        partialCountsAsDone = partialCountsAsDone,
    )

    fun rate(
        itemId: Long,
        rule: FrequencyRule?,
        index: DailyRecordIndex,
        startDay: Long,
        endDay: Long,
        partialCountsAsDone: Boolean,
    ): RateResult {
        var done = 0
        var expected = 0
        var d = startDay
        while (d <= endDay) {
            val outcome = outcomeOfRecords(
                itemId = itemId,
                day = d,
                rule = rule,
                mine = index.recordsOn(d, itemId),
                partialCountsAsDone = partialCountsAsDone,
            )
            when (outcome) {
                DayOutcome.DONE -> { done++; expected++ }
                DayOutcome.PARTIAL, DayOutcome.MISSED -> expected++
                else -> Unit
            }
            d++
        }
        return RateResult(done, expected)
    }

    /**
     * 分组统计（D43）：仅在用选项（启用、未归档、未删除）；按分组 sort / id 顺序输出，
     * 有在用选项的分组才出现，未分组最后。分组完成率 = Σ 组内日级期望完成 / Σ 期望日；
     * 配额类型（TIMES_PER_MONTH / TIMES_PER_WEEK）无日级期望，不计分母，只统计数量。
     */
    fun groupRates(
        groups: List<Group>,
        items: List<CheckItem>,
        rulesByItem: Map<Long, FrequencyRule>,
        recordsByDay: Map<Long, List<CheckRecord>>,
        startDay: Long,
        endDay: Long,
        partialCountsAsDone: Boolean,
    ): List<GroupRate> = groupRates(
        groups = groups,
        items = items,
        rulesByItem = rulesByItem,
        index = DailyRecordIndex.fromByDay(recordsByDay),
        startDay = startDay,
        endDay = endDay,
        partialCountsAsDone = partialCountsAsDone,
    )

    fun groupRates(
        groups: List<Group>,
        items: List<CheckItem>,
        rulesByItem: Map<Long, FrequencyRule>,
        index: DailyRecordIndex,
        startDay: Long,
        endDay: Long,
        partialCountsAsDone: Boolean,
    ): List<GroupRate> {
        val orderedGroups = groups.sortedWith(compareBy({ it.sort }, { it.id }))
        val knownGroupIds = orderedGroups.mapTo(HashSet()) { it.id }
        val buckets = LinkedHashMap<Long?, MutableList<CheckItem>>()
        orderedGroups.forEach { buckets[it.id] = mutableListOf() }
        buckets[null] = mutableListOf()

        items.asSequence()
            .filter { it.deletedAt == null && it.enabled && !it.archived }
            .forEach { item ->
                val key = item.groupId?.takeIf { it in knownGroupIds }
                buckets.getValue(key).add(item)
            }

        fun build(groupId: Long?, name: String, color: Int?, bucket: List<CheckItem>): GroupRate {
            var done = 0
            var expected = 0
            var quota = 0
            bucket.forEach { item ->
                val rule = rulesByItem[item.id]
                if (rule?.type in QUOTA_TYPES) {
                    quota++
                } else {
                    val result = rate(item.id, rule, index, startDay, endDay, partialCountsAsDone)
                    done += result.doneDays
                    expected += result.expectedDays
                }
            }
            return GroupRate(groupId, name, color, bucket.size, quota, RateResult(done, expected))
        }

        return buildList {
            orderedGroups.forEach { group ->
                buckets[group.id]?.takeIf { it.isNotEmpty() }?.let { bucket ->
                    add(build(group.id, group.name, group.color, bucket))
                }
            }
            buckets[null]?.takeIf { it.isNotEmpty() }?.let { bucket ->
                add(build(null, "未分组", null, bucket))
            }
        }
    }

    private data class DayEntry(val day: Long, val outcome: DayOutcome)

    fun streaks(
        itemId: Long,
        rule: FrequencyRule?,
        recordsByDay: Map<Long, List<CheckRecord>>,
        today: Long,
        partialCountsAsDone: Boolean,
        sinceDay: Long,
    ): StreakResult = streaks(
        itemId = itemId,
        rule = rule,
        index = DailyRecordIndex.fromByDay(recordsByDay),
        today = today,
        partialCountsAsDone = partialCountsAsDone,
        sinceDay = sinceDay,
    )

    fun streaks(
        itemId: Long,
        rule: FrequencyRule?,
        index: DailyRecordIndex,
        today: Long,
        partialCountsAsDone: Boolean,
        sinceDay: Long,
    ): StreakResult {
        val entries = ArrayList<DayEntry>()
        var d = sinceDay
        while (d <= today) {
            val outcome = outcomeOfRecords(
                itemId = itemId,
                day = d,
                rule = rule,
                mine = index.recordsOn(d, itemId),
                partialCountsAsDone = partialCountsAsDone,
            )
            if (outcome == DayOutcome.DONE || outcome == DayOutcome.PARTIAL || outcome == DayOutcome.MISSED) {
                entries.add(DayEntry(d, outcome))
            }
            d++
        }

        // 当前连续：从今天回溯；今天未完成时宽限（跳过今天继续数）
        var current = 0
        var i = entries.size - 1
        if (i >= 0 && entries[i].day == today && entries[i].outcome != DayOutcome.DONE) {
            i--
        }
        while (i >= 0 && entries[i].outcome == DayOutcome.DONE) {
            current++
            i--
        }

        // 最佳连续：全序列扫描 DONE 最长连段
        var best = 0
        var run = 0
        for (e in entries) {
            if (e.outcome == DayOutcome.DONE) {
                run++
                if (run > best) best = run
            } else {
                run = 0
            }
        }
        return StreakResult(current, maxOf(best, current))
    }

    /**
     * 月度配额进度（TIMES_PER_MONTH）：当月有完成（DONE/PARTIAL）记录的天数，展示「本月 x/N 次」。
     * 周度配额进度（TIMES_PER_WEEK）：本自然周（按 weekStart 对齐）同样口径，展示「本周 x/N 次」。
     * 配口径统一：有完成记录的天数 = 1 次；跳过 / PLANNED 不计。
     */
    fun weeklyQuotaProgress(
        itemId: Long,
        index: DailyRecordIndex,
        today: Long,
        weekStart: Int = 1,
    ): Int {
        val ws = weekStart.coerceIn(1, 7)
        val dow = LocalDate.ofEpochDay(today).dayOfWeek.value
        val start = today - ((dow - ws + 7) % 7)
        return quotaProgress(itemId, index, start, start + 6)
    }

    fun weeklyQuotaProgress(
        itemId: Long,
        recordsByDay: Map<Long, List<CheckRecord>>,
        today: Long,
        weekStart: Int = 1,
    ): Int = weeklyQuotaProgress(
        itemId = itemId,
        index = DailyRecordIndex.fromByDay(recordsByDay),
        today = today,
        weekStart = weekStart,
    )

    fun monthlyQuotaProgress(
        itemId: Long,
        recordsByDay: Map<Long, List<CheckRecord>>,
        today: Long,
    ): Int = monthlyQuotaProgress(
        itemId = itemId,
        index = DailyRecordIndex.fromByDay(recordsByDay),
        today = today,
    )

    fun monthlyQuotaProgress(
        itemId: Long,
        index: DailyRecordIndex,
        today: Long,
    ): Int {
        val ym = YearMonth.from(LocalDate.ofEpochDay(today))
        val start = ym.atDay(1).toEpochDay()
        val end = ym.atEndOfMonth().toEpochDay()
        return quotaProgress(itemId, index, start, end)
    }

    /** 配额通用口径：[start]..[end] 内有完成（DONE/PARTIAL）记录的天数 */
    private fun quotaProgress(itemId: Long, index: DailyRecordIndex, start: Long, end: Long): Int {
        var days = 0
        var d = start
        while (d <= end) {
            val hasMark = index.recordsOn(d, itemId)
                .any { it.status == RecordStatus.DONE || it.status == RecordStatus.PARTIAL }
            if (hasMark) days++
            d++
        }
        return days
    }

    /**
     * 全时段聚合总量（D28）：次数只算 DONE，PARTIAL 单独计数；
     * 量值合计含 DONE + PARTIAL（过程量计入投入，与今日页 totalValue 口径一致）；PLANNED / SKIPPED 一律不计。
     * 传入全局记录也安全（内部按 itemId 过滤）。
     */
    fun totals(itemId: Long, records: List<CheckRecord>): ItemTotals {
        var done = 0
        var partial = 0
        var sum = 0.0
        for (record in records) {
            if (record.itemId != itemId) continue
            when (record.status) {
                RecordStatus.DONE -> {
                    done++
                    sum += record.value ?: 0.0
                }
                RecordStatus.PARTIAL -> {
                    partial++
                    sum += record.value ?: 0.0
                }
                RecordStatus.SKIPPED, RecordStatus.PLANNED -> Unit
            }
        }
        return ItemTotals(doneCount = done, partialCount = partial, valueSum = sum)
    }

    /**
     * 全时段聚合总量的一次遍历版本，供统计页在大量选项时避免 O(选项数 × 记录数)。
     * 口径与 [totals] 完全一致；没有 DONE / PARTIAL 记录的选项不会出现在结果中。
     */
    fun totalsByItem(records: List<CheckRecord>): Map<Long, ItemTotals> {
        val acc = HashMap<Long, DoubleArray>()
        for (record in records) {
            when (record.status) {
                RecordStatus.DONE -> {
                    val values = acc.getOrPut(record.itemId) { DoubleArray(3) }
                    values[0]++
                    values[2] += record.value ?: 0.0
                }
                RecordStatus.PARTIAL -> {
                    val values = acc.getOrPut(record.itemId) { DoubleArray(3) }
                    values[1]++
                    values[2] += record.value ?: 0.0
                }
                RecordStatus.SKIPPED, RecordStatus.PLANNED -> Unit
            }
        }
        return acc.mapValues { (_, values) ->
            ItemTotals(
                doneCount = values[0].toInt(),
                partialCount = values[1].toInt(),
                valueSum = values[2],
            )
        }
    }

    private fun aggregateDay(
        items: List<ItemRule>,
        recordsByDay: Map<Long, List<CheckRecord>>,
        day: Long,
        partialCountsAsDone: Boolean,
        acc: IntArray, // [done, expected]
    ) = aggregateDay(items, day, partialCountsAsDone, acc) { itemId ->
        recordsByDay[day].orEmpty().filter { it.itemId == itemId }
    }

    private inline fun aggregateDay(
        items: List<ItemRule>,
        day: Long,
        partialCountsAsDone: Boolean,
        acc: IntArray, // [done, expected]
        recordsFor: (itemId: Long) -> List<CheckRecord>,
    ) {
        for (item in items) {
            when (outcomeOfRecords(item.itemId, day, item.rule, recordsFor(item.itemId), partialCountsAsDone)) {
                DayOutcome.DONE -> { acc[0]++; acc[1]++ }
                DayOutcome.PARTIAL, DayOutcome.MISSED -> acc[1]++
                else -> Unit
            }
        }
    }

    /** 周趋势：最近 [weeks] 个自然周（按 weekStart 对齐；最后一周截至今天） */
    fun weeklyTrend(
        items: List<ItemRule>,
        recordsByDay: Map<Long, List<CheckRecord>>,
        today: Long,
        weekStart: Int,
        weeks: Int,
        partialCountsAsDone: Boolean,
    ): List<PeriodStat> = weeklyTrend(
        items = items,
        index = DailyRecordIndex.fromByDay(recordsByDay),
        today = today,
        weekStart = weekStart,
        weeks = weeks,
        partialCountsAsDone = partialCountsAsDone,
    )

    fun weeklyTrend(
        items: List<ItemRule>,
        index: DailyRecordIndex,
        today: Long,
        weekStart: Int,
        weeks: Int,
        partialCountsAsDone: Boolean,
    ): List<PeriodStat> {
        val ws = weekStart.coerceIn(1, 7)
        val dow = LocalDate.ofEpochDay(today).dayOfWeek.value
        val currentWeekStart = today - ((dow - ws + 7) % 7)
        return (weeks - 1 downTo 0).map { back ->
            val start = currentWeekStart - back * 7L
            val end = minOf(start + 6, today)
            val acc = IntArray(2)
            var d = start
            while (d <= end) {
                aggregateDay(items, d, partialCountsAsDone, acc) { itemId ->
                    index.recordsOn(d, itemId)
                }
                d++
            }
            PeriodStat(
                label = monthDayLabel(start),
                startDay = start,
                endDay = end,
                doneDays = acc[0],
                expectedDays = acc[1],
            )
        }
    }

    /** 月趋势：最近 [months] 个自然月（最后一个月截至今天） */
    fun monthlyTrend(
        items: List<ItemRule>,
        recordsByDay: Map<Long, List<CheckRecord>>,
        today: Long,
        months: Int,
        partialCountsAsDone: Boolean,
    ): List<PeriodStat> = monthlyTrend(
        items = items,
        index = DailyRecordIndex.fromByDay(recordsByDay),
        today = today,
        months = months,
        partialCountsAsDone = partialCountsAsDone,
    )

    fun monthlyTrend(
        items: List<ItemRule>,
        index: DailyRecordIndex,
        today: Long,
        months: Int,
        partialCountsAsDone: Boolean,
    ): List<PeriodStat> {
        val ymToday = YearMonth.from(LocalDate.ofEpochDay(today))
        return (months - 1 downTo 0).map { back ->
            val ym = ymToday.minusMonths(back.toLong())
            val start = ym.atDay(1).toEpochDay()
            val end = minOf(ym.atEndOfMonth().toEpochDay(), today)
            val acc = IntArray(2)
            var d = start
            while (d <= end) {
                aggregateDay(items, d, partialCountsAsDone, acc) { itemId ->
                    index.recordsOn(d, itemId)
                }
                d++
            }
            PeriodStat(
                label = if (ym.year == ymToday.year) "${ym.monthValue}月" else "${ym.year}年${ym.monthValue}月",
                startDay = start,
                endDay = end,
                doneDays = acc[0],
                expectedDays = acc[1],
            )
        }
    }

    private fun monthDayLabel(epochDay: Long): String {
        val date = LocalDate.ofEpochDay(epochDay)
        return "${date.monthValue}/${date.dayOfMonth}"
    }
}
