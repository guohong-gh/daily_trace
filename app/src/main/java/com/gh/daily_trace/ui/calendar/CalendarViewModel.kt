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
import java.time.LocalDate
import java.time.YearMonth

data class CalendarUiState(
    val title: String = "",
    val weekHeaders: List<String> = emptyList(),
    val rows: List<List<Long?>> = emptyList(),
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
        month.flatMapLatest { ym ->
            repository.observeRecordsBetween(ym.atDay(1).toEpochDay(), ym.atEndOfMonth().toEpochDay())
        },
        month.flatMapLatest { ym ->
            repository.observeDayNotesBetween(ym.atDay(1).toEpochDay(), ym.atEndOfMonth().toEpochDay())
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
        month.value = month.value.minusMonths(1)
    }

    fun nextMonth() {
        month.value = month.value.plusMonths(1)
    }

    fun backToToday() {
        month.value = YearMonth.from(LocalDate.ofEpochDay(today.value))
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
            weekHeaders = weekHeaders,
            rows = clock.monthGridRowsOf(ym),
            marks = built.marks,
            summaries = built.summaries,
            today = todayDay,
            filterName = core.filter?.name?.ifBlank { "未命名视图" },
        )
    }
}
