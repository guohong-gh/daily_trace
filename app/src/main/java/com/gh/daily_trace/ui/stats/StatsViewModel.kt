package com.gh.daily_trace.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gh.daily_trace.data.model.AppSettings
import com.gh.daily_trace.data.model.CheckItem
import com.gh.daily_trace.data.model.CheckRecord
import com.gh.daily_trace.data.model.FrequencyRule
import com.gh.daily_trace.data.model.FrequencyType
import com.gh.daily_trace.data.model.Group
import com.gh.daily_trace.data.repository.DailyTraceRepository
import com.gh.daily_trace.domain.stats.DailyRecordIndex
import com.gh.daily_trace.domain.stats.DayOutcome
import com.gh.daily_trace.domain.stats.GroupRate
import com.gh.daily_trace.domain.stats.ItemRule
import com.gh.daily_trace.domain.stats.ItemTotals
import com.gh.daily_trace.domain.stats.PeriodStat
import com.gh.daily_trace.domain.stats.RateResult
import com.gh.daily_trace.domain.stats.StatsCalculator
import com.gh.daily_trace.domain.stats.StreakResult
import com.gh.daily_trace.domain.time.DayClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.YearMonth

enum class StatRange(val label: String) {
    THIS_WEEK("本周"),
    THIS_MONTH("本月"),
    LAST_30("近30天"),
}

enum class TrendMode(val label: String) {
    WEEK("周"),
    MONTH("月"),
}

data class ItemStat(
    val item: CheckItem,
    val rate: RateResult,
    val streak: StreakResult,
    /** 全时段聚合总量（D28：次数只算 DONE、量值含 PARTIAL） */
    val totals: ItemTotals = ItemTotals(),
    /** TIMES_PER_MONTH：本月已完成天数与目标次数 */
    val isQuota: Boolean = false,
    val quotaDone: Int = 0,
    val quotaTarget: Int = 0,
)

data class StatsUi(
    val range: StatRange = StatRange.THIS_WEEK,
    val trendMode: TrendMode = TrendMode.WEEK,
    val todayDone: Int = 0,
    val todayTotal: Int = 0,
    val groupStats: List<GroupRate> = emptyList(),
    val itemStats: List<ItemStat> = emptyList(),
    val trend: List<PeriodStat> = emptyList(),
    val loaded: Boolean = false,
)

/** 统计页：范围切换（本周/本月/近30天）+ 每项完成率与连续 + 周/月趋势 */
class StatsViewModel(private val repository: DailyTraceRepository) : ViewModel() {

    private val range = MutableStateFlow(StatRange.THIS_WEEK)
    private val trendMode = MutableStateFlow(TrendMode.WEEK)
    private val tick = MutableStateFlow(0)

    private data class Core(
        val items: List<CheckItem>,
        val records: List<CheckRecord>,
        val settings: AppSettings,
        val rules: List<FrequencyRule>,
        val groups: List<Group>,
    )

    private data class Base(
        val items: List<CheckItem>,
        val records: List<CheckRecord>,
        val settings: AppSettings,
        val rules: List<FrequencyRule>,
        val groups: List<Group>,
        val range: StatRange,
    )

    private val core = combine(
        repository.activeItems,
        repository.allRecords,
        repository.settings,
        repository.frequencyRules,
        repository.groups,
    ) { items, records, settings, rules, groups ->
        Core(items, records, settings, rules, groups)
    }

    private val base = combine(core, range) { c, rangeValue ->
        Base(c.items, c.records, c.settings, c.rules, c.groups, rangeValue)
    }

    private val withTrend = combine(base, trendMode) { b, mode -> b to mode }

    val uiState: StateFlow<StatsUi> = combine(withTrend, tick) { (b, mode), _ ->
        compute(b.items, b.records, b.settings, b.rules, b.groups, b.range, mode)
    }
        // 500 选项 / 数万记录时聚合是 CPU 密集操作，不能占用主线程。
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatsUi())

    fun setRange(value: StatRange) {
        range.value = value
    }

    fun setTrendMode(value: TrendMode) {
        trendMode.value = value
    }

    /** 回前台刷新（跨逻辑日边界） */
    fun refresh() {
        tick.value += 1
    }

    private fun compute(
        items: List<CheckItem>,
        records: List<CheckRecord>,
        settings: AppSettings,
        rules: List<FrequencyRule>,
        groups: List<Group>,
        rangeValue: StatRange,
        mode: TrendMode,
    ): StatsUi {
        val today = DayClock.from(settings).today()
        val index = DailyRecordIndex.from(records)
        val rulesByItem = rules.associateBy { it.itemId }
        val partial = settings.partialCountsAsDone
        // 索引一次遍历即可得到每项最早日期；全时段总量同样只扫一遍记录。
        val firstDayByItem = index.firstDayByItem
        val totalsByItem = StatsCalculator.totalsByItem(records)

        val (start, end) = rangeDays(rangeValue, settings, today)

        val itemStats = items.map { item ->
            val rule = rulesByItem[item.id]
            val isQuota = rule?.type == FrequencyType.TIMES_PER_MONTH
            ItemStat(
                item = item,
                rate = StatsCalculator.rate(item.id, rule, index, start, end, partial),
                streak = StatsCalculator.streaks(
                    itemId = item.id,
                    rule = rule,
                    index = index,
                    today = today,
                    partialCountsAsDone = partial,
                    sinceDay = firstDayByItem[item.id] ?: today,
                ),
                totals = totalsByItem[item.id] ?: ItemTotals(),
                isQuota = isQuota,
                quotaDone = if (isQuota) StatsCalculator.monthlyQuotaProgress(item.id, index, today) else 0,
                quotaTarget = rule?.timesPerMonth ?: 0,
            )
        }

        val groupStats = StatsCalculator.groupRates(
            groups = groups,
            items = items,
            rulesByItem = rulesByItem,
            index = index,
            startDay = start,
            endDay = end,
            partialCountsAsDone = partial,
        )

        val itemRules = items.map { ItemRule(it.id, rulesByItem[it.id]) }
        val trend = when (mode) {
            TrendMode.WEEK -> StatsCalculator.weeklyTrend(
                items = itemRules,
                index = index,
                today = today,
                weekStart = settings.weekStart,
                weeks = 8,
                partialCountsAsDone = partial,
            )
            TrendMode.MONTH -> StatsCalculator.monthlyTrend(
                items = itemRules,
                index = index,
                today = today,
                months = 6,
                partialCountsAsDone = partial,
            )
        }

        val todayDone = items.count {
            StatsCalculator.outcomeOf(
                itemId = it.id,
                day = today,
                rule = rulesByItem[it.id],
                recordsOfDay = index.recordsOn(today, it.id),
                partialCountsAsDone = partial,
            ) == DayOutcome.DONE
        }

        return StatsUi(
            range = rangeValue,
            trendMode = mode,
            todayDone = todayDone,
            todayTotal = items.size,
            groupStats = groupStats,
            itemStats = itemStats,
            trend = trend,
            loaded = true,
        )
    }

    private fun rangeDays(range: StatRange, settings: AppSettings, today: Long): Pair<Long, Long> =
        when (range) {
            StatRange.THIS_WEEK -> {
                val ws = settings.weekStart.coerceIn(1, 7)
                val dow = LocalDate.ofEpochDay(today).dayOfWeek.value
                (today - ((dow - ws + 7) % 7)) to today
            }
            StatRange.THIS_MONTH -> {
                val ym = YearMonth.from(LocalDate.ofEpochDay(today))
                ym.atDay(1).toEpochDay() to today
            }
            StatRange.LAST_30 -> (today - 29) to today
        }

}
