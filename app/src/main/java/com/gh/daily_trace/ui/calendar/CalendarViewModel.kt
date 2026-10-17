package com.gh.daily_trace.ui.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gh.daily_trace.data.model.AppSettings
import com.gh.daily_trace.data.model.CheckRecord
import com.gh.daily_trace.data.model.DayNote
import com.gh.daily_trace.data.repository.DailyTraceRepository
import com.gh.daily_trace.domain.time.DayClock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

/** 某天的标记：有打卡记录 / 有日期备注 */
data class DayMark(val hasRecords: Boolean, val hasNote: Boolean)

data class CalendarUiState(
    val title: String = "",
    val weekHeaders: List<String> = emptyList(),
    val rows: List<List<Long?>> = emptyList(),
    val marks: Map<Long, DayMark> = emptyMap(),
    val today: Long = 0L,
)

/** 日历月视图：标记有记录/有备注的日期，点击进入日期详情 */
@OptIn(ExperimentalCoroutinesApi::class)
class CalendarViewModel(private val repository: DailyTraceRepository) : ViewModel() {

    private val month = MutableStateFlow(YearMonth.now())
    private val today = MutableStateFlow(DayClock.from(AppSettings()).today())

    val uiState: StateFlow<CalendarUiState> = combine(
        month,
        month.flatMapLatest { ym ->
            repository.observeRecordsBetween(ym.atDay(1).toEpochDay(), ym.atEndOfMonth().toEpochDay())
        },
        month.flatMapLatest { ym ->
            repository.observeDayNotesBetween(ym.atDay(1).toEpochDay(), ym.atEndOfMonth().toEpochDay())
        },
        repository.settings,
        today,
    ) { ym, records, notes, settings, todayDay ->
        buildState(ym, records, notes, settings, todayDay)
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
        settings: AppSettings,
        todayDay: Long,
    ): CalendarUiState {
        val clock = DayClock.from(settings)
        val recordDates = records.map { it.date }.toSet()
        val noteDates = notes.map { it.date }.toSet()
        val marks = (recordDates + noteDates).associateWith { DayMark(it in recordDates, it in noteDates) }
        val gridStart = clock.weekStartOf(ym.atDay(1).toEpochDay())
        val weekHeaders = (0 until 7).map { clock.format(gridStart + it, "EEEEE") }
        return CalendarUiState(
            title = "${ym.year}年${ym.monthValue}月",
            weekHeaders = weekHeaders,
            rows = clock.monthGridRowsOf(ym),
            marks = marks,
            today = todayDay,
        )
    }
}
