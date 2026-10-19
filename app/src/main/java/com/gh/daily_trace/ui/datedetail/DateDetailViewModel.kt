package com.gh.daily_trace.ui.datedetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gh.daily_trace.data.model.AppSettings
import com.gh.daily_trace.data.model.CheckItem
import com.gh.daily_trace.data.model.CheckRecord
import com.gh.daily_trace.data.model.CheckType
import com.gh.daily_trace.data.model.DayNote
import com.gh.daily_trace.data.model.FrequencyRule
import com.gh.daily_trace.data.model.RecordStatus
import com.gh.daily_trace.data.repository.DailyTraceRepository
import com.gh.daily_trace.domain.checkin.BackfillPolicy
import com.gh.daily_trace.domain.checkin.BatchCheckIn
import com.gh.daily_trace.domain.checkin.BatchCheckInResult
import com.gh.daily_trace.domain.checkin.CheckInPlan
import com.gh.daily_trace.domain.checkin.CheckInPlanner
import com.gh.daily_trace.domain.checkin.applyPlan
import com.gh.daily_trace.domain.time.DayClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 补卡/计划选择器中的一行：选项 + 当天状态 */
data class PickerRow(
    val item: CheckItem,
    val skipped: Boolean,
    val allowSkip: Boolean,
    val doneCount: Int = 0,
    val partialCount: Int = 0,
    val plannedCount: Int = 0,
) {
    /** 当天该选项的状态文案（空 = 未记录） */
    val stateText: String get() = when {
        skipped -> "已跳过"
        doneCount > 0 -> "已完成"
        partialCount > 0 -> "部分完成"
        plannedCount > 0 -> "计划中"
        else -> ""
    }
}

data class DateDetailUiState(
    val date: Long = 0L,
    val title: String = "",
    val dayNote: String = "",
    val records: List<CheckRecord> = emptyList(),
    val pickerRows: List<PickerRow> = emptyList(),
    val canCheckIn: Boolean = false,
    /** 未来日期可创建「计划中」 */
    val canPlan: Boolean = false,
    /** 不可记录的原因（可记录时为 null） */
    val checkInHint: String? = null,
    val isToday: Boolean = false,
) {
    /** 记录/计划对话框是否可用 */
    val canRecord: Boolean get() = canCheckIn || canPlan

    /** 记录入口文案 */
    val checkInLabel: String get() = when {
        canPlan -> "添加计划"
        canCheckIn && isToday -> "记一笔"
        canCheckIn -> "补卡"
        else -> "标记状态"
    }
}

/**
 * 日期详情：当天记录 + 日期整体备注（第三层备注）+ 补卡/计划/跳过登记。
 * 写入仍统一走 CheckInPlanner；补卡合法性由 BackfillPolicy 判定，
 * 跳过写入频率规则 skipDates（受 allowSkip 门控）。
 */
class DateDetailViewModel(
    private val repository: DailyTraceRepository,
    private val date: Long,
) : ViewModel() {

    private val today = MutableStateFlow(DayClock.from(AppSettings()).today())

    private val todayAndRules = combine(today, repository.frequencyRules) { day, rules -> day to rules }

    val uiState: StateFlow<DateDetailUiState> = combine(
        repository.observeRecordsOn(date),
        repository.observeDayNote(date),
        repository.activeItems,
        repository.settings,
        todayAndRules,
    ) { records, note, items, settings, (todayDay, rules) ->
        buildState(records, note, items, settings, todayDay, rules)
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

    /** 批量删除所选记录（不可恢复，UI 侧带确认框） */
    fun deleteRecords(recordIds: Set<Long>) {
        if (recordIds.isEmpty()) return
        viewModelScope.launch { applyPlan(repository, CheckInPlan(deletes = recordIds.sorted())) }
    }

    /** 批量改备注：统一覆盖所选记录；空串即清除 */
    fun setRecordsNote(recordIds: Set<Long>, note: String) {
        if (recordIds.isEmpty()) return
        viewModelScope.launch {
            repository.updateRecordsNote(recordIds.sorted(), note, System.currentTimeMillis())
        }
    }

    /**
     * 选择器批量取值预载：itemId → 将记录的值（null 表示暂不可批量）。
     * 口径见 [BatchCheckIn]（默认值直记）。
     */
    suspend fun loadBatchValues(): Map<Long, Double?> =
        repository.getAllItems()
            .filter { !it.archived && it.enabled }
            .associate { item ->
                item.id to BatchCheckIn.valueFor(item, repository.getLatestRecordUpTo(item.id, date))
            }

    /**
     * 批量记录（默认值直记）：今天 = 打卡、过去 = 补卡、未来 = 计划。
     * 逐项复用 BackfillPolicy 与 [CheckInPlanner]；已完成布尔项与不可批量项跳过。
     */
    suspend fun batchAddRecords(itemIds: Set<Long>): BatchCheckInResult {
        if (itemIds.isEmpty()) return BatchCheckInResult(0, 0)
        val settings = repository.getSettings()
        val todayDay = DayClock.from(settings).today()
        val canPlan = BackfillPolicy.canPlan(date, todayDay)
        val canCheckIn = BackfillPolicy.canCheckIn(date, todayDay, settings.maxBackfillDays)
        if (!canPlan && !canCheckIn) return BatchCheckInResult(0, itemIds.size)
        val items = repository.getAllItems()
            .filter { it.id in itemIds && !it.archived && it.enabled }
        val now = System.currentTimeMillis()
        var recorded = 0
        var skipped = 0
        items.forEach { item ->
            val existing = repository.getRecordsOfItemOn(item.id, date)
            val doneBoolean = item.type == CheckType.BOOLEAN && existing.any { it.status == RecordStatus.DONE }
            val value = BatchCheckIn.valueFor(item, repository.getLatestRecordUpTo(item.id, date))
            if (value == null || (!canPlan && doneBoolean)) {
                skipped++
                return@forEach
            }
            val plan = if (canPlan) {
                CheckInPlanner.planAhead(item, date, existing, value, note = "", timezone = settings.timezone, now = now)
            } else {
                CheckInPlanner.addValue(
                    item, date, existing, value, note = "",
                    timezone = settings.timezone, isBackfill = BackfillPolicy.isBackfill(date, todayDay), now = now,
                )
            }
            applyPlan(repository, plan)
            recorded++
        }
        return BatchCheckInResult(recorded, skipped)
    }

    /**
     * 记录一笔：status=DONE（默认）/ PARTIAL（部分完成）/ PLANNED（未来日期计划）。
     * 写入前按最新设置重新校验窗口。
     */
    fun addRecord(item: CheckItem, value: Double?, note: String, status: RecordStatus) {
        viewModelScope.launch {
            val settings = repository.getSettings()
            val todayDay = DayClock.from(settings).today()
            val existing = repository.getRecordsOfItemOn(item.id, date)
            val now = System.currentTimeMillis()
            val backfill = BackfillPolicy.isBackfill(date, todayDay)
            val plan = when (status) {
                RecordStatus.PLANNED -> {
                    if (!BackfillPolicy.canPlan(date, todayDay)) return@launch
                    CheckInPlanner.planAhead(item, date, existing, value, note, settings.timezone, now)
                }
                RecordStatus.PARTIAL -> {
                    if (!BackfillPolicy.canCheckIn(date, todayDay, settings.maxBackfillDays)) return@launch
                    CheckInPlanner.addPartial(item, date, value, note, settings.timezone, backfill, now)
                }
                else -> {
                    if (!BackfillPolicy.canCheckIn(date, todayDay, settings.maxBackfillDays)) return@launch
                    CheckInPlanner.addValue(item, date, existing, value, note, settings.timezone, backfill, now)
                }
            }
            applyPlan(repository, plan)
        }
    }

    /** 标记/取消跳过（休息日/请假登记；写入频率规则 skipDates，受 allowSkip 门控） */
    fun toggleSkip(item: CheckItem) {
        viewModelScope.launch {
            val rule = repository.getFrequencyOf(item.id)
            if (rule != null && !rule.allowSkip) return@launch
            val base = rule ?: FrequencyRule(itemId = item.id)
            val dates = if (date in base.skipDates) base.skipDates - date else base.skipDates + date
            repository.saveFrequency(base.copy(skipDates = dates.sorted(), updatedAt = System.currentTimeMillis()))
        }
    }

    private fun buildState(
        records: List<CheckRecord>,
        note: DayNote?,
        items: List<CheckItem>,
        settings: AppSettings,
        todayDay: Long,
        rules: List<FrequencyRule>,
    ): DateDetailUiState {
        val clock = DayClock.from(settings)
        val canCheckIn = BackfillPolicy.canCheckIn(date, todayDay, settings.maxBackfillDays)
        val canPlan = BackfillPolicy.canPlan(date, todayDay)
        val hint = when {
            canPlan -> "未来日期：可创建「计划中」或标记跳过（请假），当天到来后可正常打卡"
            canCheckIn -> null
            settings.maxBackfillDays <= 0 -> "补卡已禁用（可在设置中开启）；仍可标记跳过"
            else -> "超出补卡上限（${settings.maxBackfillDays} 天内）；仍可标记跳过"
        }
        val rulesByItem = rules.associateBy { it.itemId }
        val byItem = records.groupBy { it.itemId }
        val pickerRows = items.map { item ->
            val rule = rulesByItem[item.id]
            val itemRecords = byItem[item.id].orEmpty()
            PickerRow(
                item = item,
                skipped = rule?.allowSkip == true && date in rule.skipDates,
                allowSkip = rule?.allowSkip ?: true,
                doneCount = itemRecords.count { it.status == RecordStatus.DONE },
                partialCount = itemRecords.count { it.status == RecordStatus.PARTIAL },
                plannedCount = itemRecords.count { it.status == RecordStatus.PLANNED },
            )
        }
        return DateDetailUiState(
            date = date,
            title = clock.format(date, "M月d日 EEE"),
            dayNote = note?.note.orEmpty(),
            records = records,
            pickerRows = pickerRows,
            canCheckIn = canCheckIn,
            canPlan = canPlan,
            checkInHint = hint,
            isToday = date == todayDay,
        )
    }
}
