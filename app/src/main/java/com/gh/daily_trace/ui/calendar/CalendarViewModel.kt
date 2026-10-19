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
import com.gh.daily_trace.domain.calendar.WeekMatrix
import com.gh.daily_trace.domain.calendar.WeekRow
import com.gh.daily_trace.domain.calendar.YearGrid
import com.gh.daily_trace.domain.calendar.YearHeatmap
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

/** 日历页视图模式（M8：月/周/年，时间线后续批次接入） */
enum class CalendarViewMode { MONTH, WEEK, YEAR }

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

/** 周视图可翻范围（D32 同口径）：1970-01-01 所在周 ~ 2100-12-31 所在周的周起始日 */
fun calendarWeekBounds(weekStart: DayOfWeek): LongRange {
    val first = DayClock.weekStartOf(LocalDate.of(1970, 1, 1).toEpochDay(), weekStart)
    val last = DayClock.weekStartOf(LocalDate.of(2100, 12, 31).toEpochDay(), weekStart)
    return first..last
}

/** 周起始日 → pager 页索引（[calendarWeekBounds] 首个周为第 0 页） */
fun weekToPage(weekAnchor: Long, weekStart: DayOfWeek): Int =
    ((weekAnchor - calendarWeekBounds(weekStart).first) / 7).toInt()

/** pager 页索引 → 周起始日 */
fun pageToWeekStart(page: Int, weekStart: DayOfWeek): Long =
    calendarWeekBounds(weekStart).first + page * 7L

/** 周视图总页数（含首尾） */
fun weekPageCount(weekStart: DayOfWeek): Int {
    val bounds = calendarWeekBounds(weekStart)
    return ((bounds.last - bounds.first) / 7).toInt() + 1
}

/** 年视图可翻范围（D32 同口径）：1970 ~ 2100，边界停住不回绕 */
val CALENDAR_MIN_YEAR: Int = CALENDAR_MIN_MONTH.year
val CALENDAR_MAX_YEAR: Int = CALENDAR_MAX_MONTH.year

/** 年视图总页数（含首尾） */
val CALENDAR_YEAR_COUNT: Int = CALENDAR_MAX_YEAR - CALENDAR_MIN_YEAR + 1

/** 年份 → pager 页索引（[CALENDAR_MIN_YEAR] 为第 0 页） */
fun yearToPage(year: Int): Int = year - CALENDAR_MIN_YEAR

/** pager 页索引 → 年份 */
fun pageToYear(page: Int): Int = CALENDAR_MIN_YEAR + page

data class CalendarUiState(
    val viewMode: CalendarViewMode = CalendarViewMode.MONTH,
    val title: String = "",
    /** 当前月（与滑动 pager 双向同步） */
    val month: YearMonth = YearMonth.now(),
    /** 周起始（逐月/逐周网格渲染用，见 DayClock.monthGridRows） */
    val weekStart: DayOfWeek = DayOfWeek.MONDAY,
    val weekHeaders: List<String> = emptyList(),
    /** 逐日标记：逐选项快照色圆点 + 「+N」溢出 + 备注（月视图） */
    val marks: Map<Long, DayMarks> = emptyMap(),
    /** 逐日摘要（长按浮层数据） */
    val summaries: Map<Long, DaySummary> = emptyMap(),
    val today: Long = 0L,
    /** 已应用的筛选视图名称；null = 未筛选（只联动选项/分组范围，D31） */
    val filterName: String? = null,
    /** 周视图当前周起始日 */
    val weekAnchor: Long = 0L,
    /** 周标题，如「2026年10月13日 – 19日」 */
    val weekTitle: String = "",
    /** 周矩阵：值覆盖「当前周 ±1 周」，保证横滑相邻页有内容（D32 预取思路） */
    val weekRowsByAnchor: Map<Long, List<WeekRow>> = emptyMap(),
    /** 年视图当前年 */
    val yearAnchor: Int = 0,
    /** 年标题，如「2026 年」 */
    val yearTitle: String = "",
    /** 年热力图：值覆盖「当前年 ±1 年」，保证横滑相邻页有内容（D32 预取思路） */
    val yearGridsByYear: Map<Int, YearGrid> = emptyMap(),
)

/**
 * 日历页（M8）：月 / 周 / 年视图共用筛选与「今天」。
 * - 月视图：左右滑动翻月（1970-01 ~ 2100-12，D32）、点标题弹年月选择器；记录窗口为当前月 ±1；
 * - 周视图（D34）：行=未删除且命中筛选的选项、列=周内 7 天，横滑/箭头换周；记录窗口为当前周 ±1 周；
 * - 年视图（D35）：行=年首周~年尾周、列=周内 7 天，强度=当日 DONE 去重项数分档，横滑/箭头换年；
 *   记录窗口为当前年 ±1 年，摘要只覆盖可见的当年；
 * - 筛选沿用今日页已应用视图的选项/分组范围（忽略日筛选，D31），数据变化实时刷新。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CalendarViewModel(private val repository: DailyTraceRepository) : ViewModel() {

    private data class Core(
        val items: List<CheckItem>,
        val rules: List<FrequencyRule>,
        val settings: AppSettings,
        val filter: FilterView?,
    )

    private sealed interface WindowData {
        val records: List<CheckRecord>
        val notes: List<DayNote>

        data class Month(
            override val records: List<CheckRecord>,
            override val notes: List<DayNote>,
            val month: YearMonth,
        ) : WindowData

        data class Week(
            override val records: List<CheckRecord>,
            override val notes: List<DayNote>,
            val anchor: Long,
        ) : WindowData

        data class Year(
            override val records: List<CheckRecord>,
            override val notes: List<DayNote>,
            val year: Int,
        ) : WindowData
    }

    private val month = MutableStateFlow(YearMonth.now())
    private val today = MutableStateFlow(DayClock.from(AppSettings()).today())
    private val weekStart = MutableStateFlow(DayOfWeek.MONDAY)
    private val weekAnchor = MutableStateFlow(DayClock.weekStartOf(today.value, DayOfWeek.MONDAY))
    private val yearAnchor = MutableStateFlow(LocalDate.ofEpochDay(today.value).year)
    private val mode = MutableStateFlow(CalendarViewMode.MONTH)
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

    private val monthWindow: Flow<WindowData.Month> = month.flatMapLatest { ym ->
        val start = ym.minusMonths(1).atDay(1).toEpochDay()
        val end = ym.plusMonths(1).atEndOfMonth().toEpochDay()
        combine(
            repository.observeRecordsBetween(start, end),
            repository.observeDayNotesBetween(start, end),
        ) { records, notes -> WindowData.Month(records, notes, ym) }
    }

    private val weekWindow: Flow<WindowData.Week> = weekAnchor.flatMapLatest { anchor ->
        combine(
            repository.observeRecordsBetween(anchor - 7, anchor + 13),
            repository.observeDayNotesBetween(anchor - 7, anchor + 13),
        ) { records, notes -> WindowData.Week(records, notes, anchor) }
    }

    private val yearWindow: Flow<WindowData.Year> = yearAnchor.flatMapLatest { year ->
        val start = LocalDate.of(year - 1, 1, 1).toEpochDay()
        val end = LocalDate.of(year + 1, 12, 31).toEpochDay()
        combine(
            repository.observeRecordsBetween(start, end),
            repository.observeDayNotesBetween(start, end),
        ) { records, notes -> WindowData.Year(records, notes, year) }
    }

    /** 仅订阅当前视图的数据窗口，避免非活动视图产生查询 */
    private val window: Flow<WindowData> = mode.flatMapLatest { viewMode ->
        when (viewMode) {
            CalendarViewMode.MONTH -> monthWindow
            CalendarViewMode.WEEK -> weekWindow
            CalendarViewMode.YEAR -> yearWindow
        }
    }

    val uiState: StateFlow<CalendarUiState> = combine(window, core, today) { windowValue, coreValue, todayDay ->
        buildState(windowValue, coreValue, todayDay)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CalendarUiState())

    init {
        // 首帧可能落后于真实设置（时区/起始小时/周起始），异步校正
        viewModelScope.launch {
            val settings = repository.getSettings()
            val clock = DayClock.from(settings)
            val todayDay = clock.today()
            today.value = todayDay
            month.value = YearMonth.from(LocalDate.ofEpochDay(todayDay))
            weekStart.value = clock.weekStart
            weekAnchor.value = clock.weekStartOf(todayDay)
            yearAnchor.value = LocalDate.ofEpochDay(todayDay).year
        }
    }

    fun setViewMode(value: CalendarViewMode) {
        mode.value = value
    }

    fun prevMonth() {
        setMonth(month.value.minusMonths(1))
    }

    fun nextMonth() {
        setMonth(month.value.plusMonths(1))
    }

    /** 滑动翻月 / 年月选择器跳转；越界钳制到 1970-01 ~ 2100-12（D32） */
    fun setMonth(value: YearMonth) {
        month.value = value.coerceIn(CALENDAR_MIN_MONTH, CALENDAR_MAX_MONTH)
    }

    fun prevWeek() {
        shiftWeek(-7)
    }

    fun nextWeek() {
        shiftWeek(7)
    }

    private fun shiftWeek(delta: Long) {
        setWeekAnchor(weekAnchor.value + delta)
    }

    /** 滑动换周 / 周箭头；越界钳制到 1970-01-01 所在周 ~ 2100-12-31 所在周 */
    fun setWeekAnchor(value: Long) {
        val bounds = calendarWeekBounds(weekStart.value)
        weekAnchor.value = value.coerceIn(bounds.first, bounds.last)
    }

    fun prevYear() {
        setYear(yearAnchor.value - 1)
    }

    fun nextYear() {
        setYear(yearAnchor.value + 1)
    }

    /** 滑动换年 / 年箭头；越界钳制到 1970 ~ 2100 */
    fun setYear(value: Int) {
        yearAnchor.value = value.coerceIn(CALENDAR_MIN_YEAR, CALENDAR_MAX_YEAR)
    }

    /** 已应用筛选视图的入口；-1 表示清除筛选 */
    fun setFilter(id: Long) {
        filterId.value = id
    }

    /** 顶栏「今天」：月视图回当前月、周视图回本周、年视图回当年 */
    fun backToToday() {
        when (mode.value) {
            CalendarViewMode.MONTH -> setMonth(YearMonth.from(LocalDate.ofEpochDay(today.value)))
            CalendarViewMode.WEEK -> viewModelScope.launch {
                val settings = repository.getSettings()
                val clock = DayClock.from(settings)
                val todayDay = clock.today()
                today.value = todayDay
                weekStart.value = clock.weekStart
                weekAnchor.value = clock.weekStartOf(todayDay)
            }
            CalendarViewMode.YEAR -> viewModelScope.launch {
                val settings = repository.getSettings()
                val clock = DayClock.from(settings)
                val todayDay = clock.today()
                today.value = todayDay
                yearAnchor.value = LocalDate.ofEpochDay(todayDay).year
            }
        }
    }

    /** 回到前台后刷新（跨逻辑日边界） */
    fun refreshToday() {
        viewModelScope.launch {
            val settings = repository.getSettings()
            val clock = DayClock.from(settings)
            today.value = clock.today()
            if (weekStart.value != clock.weekStart) {
                weekStart.value = clock.weekStart
                // 周起始设置变化：把当前周锚点归一到新口径
                weekAnchor.value = clock.weekStartOf(weekAnchor.value)
            }
        }
    }

    private fun buildState(window: WindowData, core: Core, todayDay: Long): CalendarUiState {
        val clock = DayClock.from(core.settings)
        val filterName = core.filter?.name?.ifBlank { "未命名视图" }
        return when (window) {
            is WindowData.Month -> {
                val ym = window.month
                val gridStart = clock.weekStartOf(ym.atDay(1).toEpochDay())
                val built = CalendarMarks.build(
                    records = window.records,
                    notes = window.notes,
                    rules = core.rules,
                    items = core.items,
                    filter = core.filter,
                )
                CalendarUiState(
                    viewMode = CalendarViewMode.MONTH,
                    title = "${ym.year}年${ym.monthValue}月",
                    month = ym,
                    weekStart = clock.weekStart,
                    weekHeaders = (0 until 7).map { clock.format(gridStart + it, "EEEEE") },
                    marks = built.marks,
                    summaries = built.summaries,
                    today = todayDay,
                    filterName = filterName,
                    weekAnchor = weekAnchor.value,
                    yearAnchor = yearAnchor.value,
                )
            }
            is WindowData.Week -> {
                val anchor = window.anchor
                val bounds = calendarWeekBounds(clock.weekStart)
                val built = CalendarMarks.build(
                    records = window.records,
                    notes = window.notes,
                    rules = core.rules,
                    items = core.items,
                    filter = core.filter,
                )
                val rowsByAnchor = listOf(anchor - 7, anchor, anchor + 7)
                    .filter { it in bounds }
                    .associateWith { start ->
                        WeekMatrix.build(
                            days = (start until start + 7).toList(),
                            records = window.records,
                            rules = core.rules,
                            items = core.items,
                            filter = core.filter,
                        )
                    }
                CalendarUiState(
                    viewMode = CalendarViewMode.WEEK,
                    weekStart = clock.weekStart,
                    summaries = built.summaries,
                    today = todayDay,
                    filterName = filterName,
                    weekAnchor = anchor,
                    weekTitle = weekTitle(anchor),
                    weekRowsByAnchor = rowsByAnchor,
                    yearAnchor = yearAnchor.value,
                )
            }
            is WindowData.Year -> {
                val year = window.year
                // 摘要只覆盖可见格（当年）；相邻年仅渲染色格，长按交互发生在停稳页
                val built = CalendarMarks.build(
                    records = window.records.filter { LocalDate.ofEpochDay(it.date).year == year },
                    notes = window.notes.filter { LocalDate.ofEpochDay(it.date).year == year },
                    rules = core.rules,
                    items = core.items,
                    filter = core.filter,
                )
                val grids = (year - 1..year + 1)
                    .filter { it in CALENDAR_MIN_YEAR..CALENDAR_MAX_YEAR }
                    .associateWith { target ->
                        YearHeatmap.build(
                            year = target,
                            weekStart = clock.weekStart,
                            records = window.records,
                            items = core.items,
                            filter = core.filter,
                        )
                    }
                CalendarUiState(
                    viewMode = CalendarViewMode.YEAR,
                    weekStart = clock.weekStart,
                    summaries = built.summaries,
                    today = todayDay,
                    filterName = filterName,
                    yearAnchor = year,
                    yearTitle = "$year 年",
                    yearGridsByYear = grids,
                )
            }
        }
    }

    /** 周标题：同年同月「2026年10月13日 – 19日」，跨月/跨年补全 */
    private fun weekTitle(anchor: Long): String {
        val start = LocalDate.ofEpochDay(anchor)
        val end = LocalDate.ofEpochDay(anchor + 6)
        return when {
            start.year == end.year && start.month == end.month ->
                "${start.year}年${start.monthValue}月${start.dayOfMonth}日 – ${end.dayOfMonth}日"
            start.year == end.year ->
                "${start.year}年${start.monthValue}月${start.dayOfMonth}日 – ${end.monthValue}月${end.dayOfMonth}日"
            else ->
                "${start.year}年${start.monthValue}月${start.dayOfMonth}日 – " +
                    "${end.year}年${end.monthValue}月${end.dayOfMonth}日"
        }
    }
}
