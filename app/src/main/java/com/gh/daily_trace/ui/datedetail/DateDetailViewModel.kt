package com.gh.daily_trace.ui.datedetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gh.daily_trace.data.model.AppSettings
import com.gh.daily_trace.data.model.CheckItem
import com.gh.daily_trace.data.model.CheckRecord
import com.gh.daily_trace.data.model.DayNote
import com.gh.daily_trace.data.repository.DailyTraceRepository
import com.gh.daily_trace.domain.checkin.BackfillPolicy
import com.gh.daily_trace.domain.checkin.CheckInPlanner
import com.gh.daily_trace.domain.checkin.applyPlan
import com.gh.daily_trace.domain.time.DayClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DateDetailUiState(
    val date: Long = 0L,
    val title: String = "",
    val dayNote: String = "",
    val records: List<CheckRecord> = emptyList(),
    val activeItems: List<CheckItem> = emptyList(),
    val canCheckIn: Boolean = false,
    /** 不可记录的原因（可记录时为 null） */
    val checkInHint: String? = null,
    val isToday: Boolean = false,
) {
    /** 补卡按钮文案 */
    val checkInLabel: String get() = if (isToday) "记一笔" else "补卡"
}

/**
 * 日期详情：当天记录 + 日期整体备注（第三层备注）+ 补卡。
 * 写入仍统一走 CheckInPlanner；补卡合法性由 BackfillPolicy 判定。
 */
class DateDetailViewModel(
    private val repository: DailyTraceRepository,
    private val date: Long,
) : ViewModel() {

    private val today = MutableStateFlow(DayClock.from(AppSettings()).today())

    val uiState: StateFlow<DateDetailUiState> = combine(
        repository.observeRecordsOn(date),
        repository.observeDayNote(date),
        repository.activeItems,
        repository.settings,
        today,
    ) { records, note, items, settings, todayDay ->
        buildState(records, note, items, settings, todayDay)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DateDetailUiState(date = date))

    fun refreshToday() {
        viewModelScope.launch {
            today.value = DayClock.from(repository.getSettings()).today()
        }
    }

    /** 保存日期备注；空白视为清除（DAO 注释约定：空备注不保留） */
    fun saveDayNote(text: String) {
        val trimmed = text.trim()
        viewModelScope.launch {
            if (trimmed.isEmpty()) {
                repository.deleteDayNote(date)
            } else {
                val now = System.currentTimeMillis()
                val existing = repository.getDayNote(date)
                repository.saveDayNote(
                    DayNote(
                        date = date,
                        note = trimmed,
                        createdAt = existing?.createdAt ?: now,
                        updatedAt = now,
                    )
                )
            }
        }
    }

    fun deleteRecord(recordId: Long) {
        viewModelScope.launch { applyPlan(repository, CheckInPlanner.deleteRecord(recordId)) }
    }

    /** 记录一笔（今天）或补卡（过去日期）；写入前按最新设置再校验一次 */
    fun addRecord(item: CheckItem, value: Double?, note: String) {
        viewModelScope.launch {
            val settings = repository.getSettings()
            val todayDay = DayClock.from(settings).today()
            if (!BackfillPolicy.canCheckIn(date, todayDay, settings.maxBackfillDays)) return@launch
            val existing = repository.getRecordsOfItemOn(item.id, date)
            val plan = CheckInPlanner.addValue(
                item = item,
                date = date,
                existing = existing,
                inputValue = value,
                note = note,
                timezone = settings.timezone,
                isBackfill = BackfillPolicy.isBackfill(date, todayDay),
                now = System.currentTimeMillis(),
            )
            applyPlan(repository, plan)
        }
    }

    private fun buildState(
        records: List<CheckRecord>,
        note: DayNote?,
        items: List<CheckItem>,
        settings: AppSettings,
        todayDay: Long,
    ): DateDetailUiState {
        val clock = DayClock.from(settings)
        val canCheckIn = BackfillPolicy.canCheckIn(date, todayDay, settings.maxBackfillDays)
        val hint = when {
            date > todayDay -> "未来日期暂不支持记录"
            canCheckIn -> null
            settings.maxBackfillDays <= 0 -> "补卡已禁用（可在设置中开启）"
            else -> "超出补卡上限（${settings.maxBackfillDays} 天内）"
        }
        return DateDetailUiState(
            date = date,
            title = clock.format(date, "M月d日 EEE"),
            dayNote = note?.note.orEmpty(),
            records = records,
            activeItems = items,
            canCheckIn = canCheckIn,
            checkInHint = hint,
            isToday = date == todayDay,
        )
    }
}
