package com.gh.daily_trace.ui.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gh.daily_trace.data.model.AppSettings
import com.gh.daily_trace.data.model.CheckItem
import com.gh.daily_trace.data.model.CheckRecord
import com.gh.daily_trace.data.model.DayNote
import com.gh.daily_trace.data.model.FilterView
import com.gh.daily_trace.data.model.FrequencyRule
import com.gh.daily_trace.data.repository.DailyTraceRepository
import com.gh.daily_trace.domain.calendar.CalendarMarks
import com.gh.daily_trace.domain.calendar.DayMarks
import com.gh.daily_trace.domain.calendar.DaySummary
import com.gh.daily_trace.domain.time.DayClock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

/** 日历可翻月范围（D32）：1970-01 ~ 2100-12，边界停住不回绕 */
val CALENDAR_MIN_MONTH: YearMonth = YearMonth.of(1970, 1)
val CALENDAR_MAX_MONTH: YearMonth = YearMonth.of(2100, 12)

/** 翻月总页数（含首尾） */
val CALENDAR_MONTH_COUNT: Int = monthToPage(CALENDAR_MAX_MONTH) + 1

/** 月份 → pager 页索引（[CALENDAR_MIN_MONTH] 为第 0 页） */
fun monthToPage(month: YearMonth): Int = monthIndex(month) - monthIndex(CALENDAR_MIN_MONTH)

/** pager 页索引 → 月份 */
fun pageToMonth(page: Int): YearMonth {
    val index = monthIndex(CALENDAR_MIN_MONTH) + page
    return YearMonth.of(index / 12, index % 12 + 1)
}

private fun monthIndex(month: YearMonth): Int = month.year * 12 + (month.monthValue - 1)

data class CalendarUiState(
    val title: String = "",
    /** 当前月（与滑动 pager 双向同步） */
    val month: YearMonth = YearMonth.now(),
    /** 周起始（逐月网格渲染用，见 DayClock.monthGridRows） */
    val weekStart: DayOfWeek = DayOfWeek.MONDAY,
    val weekHeaders: List<String> = emptyList(),
    /** 逐日标记：逐选项快照色圆点 + 「+N」溢出 + 备注 */
    val marks: Map<Long, DayMarks> = emptyMap(),
    /** 逐日摘要（长按浮层数据） */
    val summaries: Map<Long, DaySummary> = emptyMap(),
    val today: Long = 0L,
    /** 已应用的筛选视图名称；null = 未筛选（只联动选项/分组范围，D31） */
    val filterName: String? = null,
)

/**
 * 日历月视图：逐选项彩色标记 + 长按摘要，点击进入日期详情。
 * 左右滑动翻月（1970-01 ~ 2100-12，D32）、点标题弹年月选择器；记录窗口为当前月 ±1。
 * 标记联动今日页已应用的筛选视图（选项/分组范围；忽略日筛选），数据变化实时刷新。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CalendarViewModel(private val repository: DailyTraceRepository) : ViewModel() {

    private data class Core(
        val items: List<CheckItem>,
        val rules: List<FrequencyRule>,
        val settings: AppSettings,
        val filter: FilterView?,
    )

    private val month = MutableStateFlow(YearMonth.now())
    private val today = MutableStateFlow(DayClock.from(AppSettings()).today())
    private val filterId = MutableStateFlow(-1L)

    private val activeFilter: Flow<FilterView?> = combine(filterId, repository.filterViews) { id, views ->
        views.firstOrNull { it.id == id }
    }

    private val core: Flow<Core> = combine(
        repository.items,
        repository.frequencyRules,
        repository.settings,
        activeFilter,
    ) { items, rules, settings, filter -> Core(items, rules, settings, filter) }

    val uiState: StateFlow<CalendarUiState> = combine(
        month,
        // 预取「当前月 ±1」，保证左右滑动时相邻月有标记可渲染（D32）
        month.flatMapLatest { ym ->
            repository.observeRecordsBetween(
                ym.minusMonths(1).atDay(1).toEpochDay(),
                ym.plusMonths(1).atEndOfMonth().toEpochDay(),
            )
        },
        month.flatMapLatest { ym ->
            repository.observeDayNotesBetween(
                ym.minusMonths(1).atDay(1).toEpochDay(),
                ym.plusMonths(1).atEndOfMonth().toEpochDay(),
            )
        },
        core,
        today,
    ) { ym, records, notes, coreValue, todayDay ->
        buildState(ym, records, notes, coreValue, todayDay)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CalendarUiState())

    init {
        // 首帧可能落后于真实设置（时区/起始小时），异步校正到今天所在月
        viewModelScope.launch {
            val settings = repository.getSettings()
            val todayDay = DayClock.from(settings).today()
            today.value = todayDay
            month.value = YearMonth.from(LocalDate.ofEpochDay(todayDay))
        }
    }

    fun prevMonth() {
        setMonth(month.value.minusMonths(1))
    }

    fun nextMonth() {
        setMonth(month.value.plusMonths(1))
    }

    fun backToToday() {
        setMonth(YearMonth.from(LocalDate.ofEpochDay(today.value)))
    }

    /** 滑动翻月 / 年月选择器跳转；越界钳制到 1970-01 ~ 2100-12（D32） */
    fun setMonth(value: YearMonth) {
        month.value = value.coerceIn(CALENDAR_MIN_MONTH, CALENDAR_MAX_MONTH)
    }

    /** 由导航层同步今日页已应用的筛选视图；-1 表示清除筛选 */
    fun setFilter(id: Long) {
        filterId.value = id
    }

    /** 回到前台后刷新（跨逻辑日边界） */
    fun refreshToday() {
        viewModelScope.launch {
            val settings = repository.getSettings()
            today.value = DayClock.from(settings).today()
        }
    }

    private fun buildState(
        ym: YearMonth,
        records: List<CheckRecord>,
        notes: List<DayNote>,
        core: Core,
        todayDay: Long,
    ): CalendarUiState {
        val clock = DayClock.from(core.settings)
        val built = CalendarMarks.build(
            records = records,
            notes = notes,
            rules = core.rules,
            items = core.items,
            filter = core.filter,
        )
        val gridStart = clock.weekStartOf(ym.atDay(1).toEpochDay())
        val weekHeaders = (0 until 7).map { clock.format(gridStart + it, "EEEEE") }
        return CalendarUiState(
            title = "${ym.year}年${ym.monthValue}月",
            month = ym,
            weekStart = clock.weekStart,
            weekHeaders = weekHeaders,
            marks = built.marks,
            summaries = built.summaries,
            today = todayDay,
            filterName = core.filter?.name?.ifBlank { "未命名视图" },
        )
    }
}
