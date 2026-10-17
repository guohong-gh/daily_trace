package com.gh.daily_trace.ui.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gh.daily_trace.data.model.AppSettings
import com.gh.daily_trace.data.model.CheckItem
import com.gh.daily_trace.data.model.CheckRecord
import com.gh.daily_trace.data.model.FilterView
import com.gh.daily_trace.data.model.Group
import com.gh.daily_trace.data.model.RecordStatus
import com.gh.daily_trace.data.repository.DailyTraceRepository
import com.gh.daily_trace.domain.checkin.CheckInPlan
import com.gh.daily_trace.domain.checkin.CheckInPlanner
import com.gh.daily_trace.domain.checkin.applyPlan
import com.gh.daily_trace.domain.filter.FilterMatcher
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

/** 单个打卡选项的今日状态 */
data class ItemRow(
    val item: CheckItem,
    val records: List<CheckRecord>,
) {
    val done: Boolean get() = records.any { it.status == RecordStatus.DONE }
    val totalValue: Double get() = records.sumOf { it.value ?: 0.0 }
}

data class GroupSection(val title: String, val rows: List<ItemRow>)

data class TodayUiState(
    val date: Long = 0L,
    val dateLabel: String = "",
    val sections: List<GroupSection> = emptyList(),
    val totalCount: Int = 0,
    val doneCount: Int = 0,
    /** 已应用的筛选视图名称；null = 未筛选 */
    val filterName: String? = null,
    /** 未筛选前的选项总数（区分「没有选项」与「筛选后为空」） */
    val itemsBeforeFilter: Int = 0,
)

/**
 * 今日打卡页。逻辑日（epochDay）在进入页面/回到前台时刷新；
 * 打卡写入一律通过 CheckInPlanner 生成计划后落库；
 * 已应用的筛选视图（来自筛选视图页）作用于选项列表与记录状态。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TodayViewModel(private val repository: DailyTraceRepository) : ViewModel() {

    private data class Core(
        val items: List<CheckItem>,
        val groups: List<Group>,
        val settings: AppSettings,
        val filter: FilterView?,
    )

    private val today = MutableStateFlow(DayClock.from(AppSettings()).today())
    private val filterId = MutableStateFlow(-1L)

    private val activeFilter: Flow<FilterView?> = combine(filterId, repository.filterViews) { id, views ->
        views.firstOrNull { it.id == id }
    }

    private val core: Flow<Core> = combine(
        repository.activeItems,
        repository.groups,
        repository.settings,
        activeFilter,
    ) { items, groups, settings, filter -> Core(items, groups, settings, filter) }

    val uiState: StateFlow<TodayUiState> = combine(
        core,
        today.flatMapLatest { repository.observeRecordsOn(it) },
        today,
    ) { core, records, date ->
        buildState(core, records, date)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TodayUiState())

    init {
        refreshToday()
    }

    /** 回到前台/跨 4 点边界后刷新「今天」 */
    fun refreshToday() {
        viewModelScope.launch {
            val settings = repository.getSettings()
            today.value = DayClock.from(settings).today()
        }
    }

    /** 由导航层同步已应用的筛选视图；-1 表示清除筛选 */
    fun setFilter(id: Long) {
        filterId.value = id
    }

    /** 布尔型快速打卡：已打卡则取消 */
    fun toggleBoolean(item: CheckItem) = mutate(item) { existing, settings, now ->
        CheckInPlanner.toggleBoolean(item, today.value, existing, settings.timezone, isBackfill = false, now = now)
    }

    /** 新增一笔（数值可为空，仅备注） */
    fun addValue(item: CheckItem, value: Double?, note: String) = mutate(item) { existing, settings, now ->
        CheckInPlanner.addValue(item, today.value, existing, value, note, settings.timezone, isBackfill = false, now = now)
    }

    /** 删除单条记录 */
    fun deleteRecord(recordId: Long) {
        viewModelScope.launch { applyPlan(repository, CheckInPlanner.deleteRecord(recordId)) }
    }

    private fun mutate(
        item: CheckItem,
        planBuilder: suspend (existing: List<CheckRecord>, settings: AppSettings, now: Long) -> CheckInPlan,
    ) {
        viewModelScope.launch {
            val settings = repository.getSettings()
            val existing = repository.getRecordsOfItemOn(item.id, today.value)
            applyPlan(repository, planBuilder(existing, settings, System.currentTimeMillis()))
        }
    }

    private fun buildState(core: Core, records: List<CheckRecord>, date: Long): TodayUiState {
        val clock = DayClock.from(core.settings)
        val byItem = records.groupBy { it.itemId }

        val visibleItems = core.items.filter { item ->
            FilterMatcher.matches(item, hasRecords = byItem[item.id].orEmpty().isNotEmpty(), filter = core.filter)
        }
        val knownGroupIds = core.groups.map { it.id }.toSet()
        val grouped = visibleItems.groupBy { it.groupId?.takeIf { id -> id in knownGroupIds } }

        val sections = buildList {
            core.groups.forEach { g ->
                grouped[g.id]?.takeIf { it.isNotEmpty() }?.let { list ->
                    add(GroupSection(g.name, list.toRows(byItem)))
                }
            }
            grouped[null]?.takeIf { it.isNotEmpty() }?.let { list ->
                add(GroupSection("未分组", list.toRows(byItem)))
            }
        }
        val allRows = sections.flatMap { it.rows }
        return TodayUiState(
            date = date,
            dateLabel = clock.format(date, "M月d日") + " " + clock.format(date, "EEE"),
            sections = sections,
            totalCount = allRows.size,
            doneCount = allRows.count { it.done },
            filterName = core.filter?.name?.ifBlank { "未命名视图" },
            itemsBeforeFilter = core.items.size,
        )
    }

    private fun List<CheckItem>.toRows(byItem: Map<Long, List<CheckRecord>>): List<ItemRow> =
        map { item -> ItemRow(item, byItem[item.id].orEmpty().sortedBy { it.createdAt }) }
}
