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
import com.gh.daily_trace.domain.calendar.TimelineDay
import com.gh.daily_trace.domain.calendar.TimelineFeed
import com.gh.daily_trace.domain.calendar.WeekMatrix
import com.gh.daily_trace.domain.calendar.WeekRow
import com.gh.daily_trace.domain.calendar.YearGrid
import com.gh.daily_trace.domain.calendar.YearHeatmap
import com.gh.daily_trace.domain.time.DateDisplay
import com.gh.daily_trace.domain.time.DayClock
import com.gh.daily_trace.domain.time.watchLogicalDay
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

/** 日历页视图模式（M8：月/周/年/时间线） */
enum class CalendarViewMode { MONTH, WEEK, YEAR, TIMELINE }

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

/** 时间线每次加载天数（D36） */
const val TIMELINE_PAGE_DAYS = 30

/** 时间线可回溯下限（与月/年视图同口径，1970-01-01） */
val TIMELINE_FLOOR: Long = LocalDate.of(1970, 1, 1).toEpochDay()

/** 时间线窗口起始日（含今天共 30 天，不早于 [TIMELINE_FLOOR]） */
fun timelineWindowStart(today: Long): Long =
    (today - (TIMELINE_PAGE_DAYS - 1)).coerceAtLeast(TIMELINE_FLOOR)

data class CalendarUiState(
    val viewMode: CalendarViewMode = CalendarViewMode.MONTH,
    val title: String = "",
    /** 当前月（与滑动 pager 双向同步） */
    val month: YearMonth = YearMonth.now(),
    /** 周起始（逐月/逐周网格渲染用，见 DayClock.monthGridRows） */
    val weekStart: DayOfWeek = DayOfWeek.MONDAY,
    /** 星期表头（按 weekStart 顺序；中文单字 / 英文三字母） */
    val weekHeaders: List<String> = emptyList(),
    /** 日期显示格式器（今日/标题之外的长按摘要、时间线等共用） */
    val dateDisplay: DateDisplay = DateDisplay(),
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
    /** 时间线：窗口内内容日倒序（D36） */
    val timelineDays: List<TimelineDay> = emptyList(),
    /** 时间线：窗口起始日（回「今天」时重置；UI 依据其增大判断回顶） */
    val timelineOldest: Long = 0L,
    /** 时间线：「加载更早」是否可用（更早无记录/备注时为 false） */
    val timelineCanLoadEarlier: Boolean = false,
    /** 时间线：回「今天」信号（+1 递增；UI 据此回到顶部） */
    val timelineRecenter: Int = 0,
)

/**
 * 日历页（M8）：月 / 周 / 年视图共用筛选与「今天」。
 * - 月视图：左右滑动翻月（1970-01 ~ 2100-12，D32）、点标题弹年月选择器；记录窗口为当前月 ±1；
 * - 周视图（D34）：行=未删除且命中筛选的选项、列=周内 7 天，横滑/箭头换周；记录窗口为当前周 ±1 周；
 * - 年视图（D35）：行=年首周~年尾周、列=周内 7 天，强度=当日 DONE 去重项数分档，横滑/箭头换年；
 *   记录窗口为当前年 ±1 年，摘要只覆盖可见的当年；
 * - 时间线（D36）：倒序日流（只列有内容的天），窗口 = 最近 30 天、每次「加载更早」再回溯 30 天；
 *   数据窗口 = [窗口起始日, 今天]，更早是否还有内容由存在性查询判定；
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

        data class Timeline(
            override val records: List<CheckRecord>,
            override val notes: List<DayNote>,
            val oldest: Long,
            val newest: Long,
            val hasOlder: Boolean,
        ) : WindowData
    }

    private val month = MutableStateFlow(YearMonth.now())
    private val today = MutableStateFlow(DayClock.from(AppSettings()).today())
    private val weekStart = MutableStateFlow(DayOfWeek.MONDAY)
    private val weekAnchor = MutableStateFlow(DayClock.weekStartOf(today.value, DayOfWeek.MONDAY))
    private val yearAnchor = MutableStateFlow(LocalDate.ofEpochDay(today.value).year)
    private val timelineOldest = MutableStateFlow(timelineWindowStart(today.value))
    /** 时间线回「今天」信号（+1 递增；UI 据此回顶） */
    private val timelineRecenter = MutableStateFlow(0)
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

    private val timelineWindow: Flow<WindowData.Timeline> =
        combine(timelineOldest, today) { oldest, newest -> oldest to newest }
            .flatMapLatest { (oldest, newest) ->
                combine(
                    repository.observeRecordsBetween(oldest, newest),
                    repository.observeDayNotesBetween(oldest, newest),
                    repository.observeHasContentBefore(oldest),
                ) { records, notes, hasOlder ->
                    WindowData.Timeline(records, notes, oldest, newest, hasOlder)
                }
            }

    /** 仅订阅当前视图的数据窗口，避免非活动视图产生查询 */
    private val window: Flow<WindowData> = mode.flatMapLatest { viewMode ->
        when (viewMode) {
            CalendarViewMode.MONTH -> monthWindow
            CalendarViewMode.WEEK -> weekWindow
            CalendarViewMode.YEAR -> yearWindow
            CalendarViewMode.TIMELINE -> timelineWindow
        }
    }

    val uiState: StateFlow<CalendarUiState> = combine(
        window,
        core,
        today,
        timelineRecenter,
    ) { windowValue, coreValue, todayDay, recenter ->
        buildState(windowValue, coreValue, todayDay, recenter)
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
            timelineOldest.value = timelineWindowStart(todayDay)
        }
        // 长驻前台跨逻辑日边界时自动刷新（回前台刷新覆盖不到）
        viewModelScope.launch { watchLogicalDay({ repository.getSettings() }) { refreshToday() } }
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

    /** 时间线：再加载更早 30 天（不早于 1970-01-01） */
    fun loadEarlier() {
        timelineOldest.value = (timelineOldest.value - TIMELINE_PAGE_DAYS)
            .coerceAtLeast(TIMELINE_FLOOR)
    }

    /** 已应用筛选视图的入口；-1 表示清除筛选 */
    fun setFilter(id: Long) {
        filterId.value = id
    }

    /** 顶栏「今天」：月视图回当前月、周视图回本周、年视图回当年、时间线回最近 30 天 */
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
            CalendarViewMode.TIMELINE -> viewModelScope.launch {
                val settings = repository.getSettings()
                val clock = DayClock.from(settings)
                val todayDay = clock.today()
                today.value = todayDay
                timelineOldest.value = timelineWindowStart(todayDay)
                timelineRecenter.value += 1
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

    private fun buildState(window: WindowData, core: Core, todayDay: Long, timelineRecenter: Int): CalendarUiState {
        val clock = DayClock.from(core.settings)
        val dateDisplay = DateDisplay.from(core.settings)
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
                    title = dateDisplay.monthTitle(ym),
                    month = ym,
                    weekStart = clock.weekStart,
                    weekHeaders = (0 until 7).map { dateDisplay.weekdayHeader(gridStart + it) },
                    dateDisplay = dateDisplay,
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
                    weekHeaders = (0 until 7).map { dateDisplay.weekdayHeader(anchor + it) },
                    dateDisplay = dateDisplay,
                    summaries = built.summaries,
                    today = todayDay,
                    filterName = filterName,
                    weekAnchor = anchor,
                    weekTitle = dateDisplay.weekTitle(anchor, anchor + 6),
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
                    weekHeaders = (0 until 7).map { dateDisplay.weekdayHeader(clock.weekStartOf(todayDay) + it) },
                    dateDisplay = dateDisplay,
                    summaries = built.summaries,
                    today = todayDay,
                    filterName = filterName,
                    yearAnchor = year,
                    yearTitle = dateDisplay.yearTitle(year),
                    yearGridsByYear = grids,
                )
            }
            is WindowData.Timeline -> {
                val days = TimelineFeed.build(
                    oldest = window.oldest,
                    newest = window.newest,
                    records = window.records,
                    notes = window.notes,
                    rules = core.rules,
                    items = core.items,
                    filter = core.filter,
                )
                CalendarUiState(
                    viewMode = CalendarViewMode.TIMELINE,
                    weekStart = clock.weekStart,
                    dateDisplay = dateDisplay,
                    summaries = days.associate { it.date to it.summary },
                    today = todayDay,
                    filterName = filterName,
                    weekAnchor = weekAnchor.value,
                    yearAnchor = yearAnchor.value,
                    timelineDays = days,
                    timelineOldest = window.oldest,
                    timelineCanLoadEarlier = window.hasOlder,
                    timelineRecenter = timelineRecenter,
                )
            }
        }
    }

}
