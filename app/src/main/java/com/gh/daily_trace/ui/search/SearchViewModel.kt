package com.gh.daily_trace.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gh.daily_trace.data.model.CheckRecord
import com.gh.daily_trace.data.model.DayNote
import com.gh.daily_trace.data.repository.DailyTraceRepository
import com.gh.daily_trace.domain.search.SearchDatePreset
import com.gh.daily_trace.domain.search.SearchDateRange
import com.gh.daily_trace.domain.search.normalizeSearchRange
import com.gh.daily_trace.domain.search.resolveSearchRange
import com.gh.daily_trace.domain.time.DateDisplay
import com.gh.daily_trace.domain.time.DayClock
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RecordHit(val record: CheckRecord, val dateLabel: String)

data class NoteHit(val note: DayNote, val dateLabel: String)

data class SearchUiState(
    val query: String = "",
    val preset: SearchDatePreset = SearchDatePreset.ALL,
    val customFrom: Long? = null,
    val customTo: Long? = null,
    /** 最近一次搜索实际生效的范围（预设已按「今天」解析） */
    val range: SearchDateRange = SearchDateRange(),
    val records: List<RecordHit> = emptyList(),
    val notes: List<NoteHit> = emptyList(),
    /** 是否已执行过搜索（区分「未搜索」与「无结果」） */
    val searched: Boolean = false,
)

/**
 * 搜索：命中打卡备注/选项快照名 + 日期备注，按日期倒序。
 * 关键词与日期范围合并为 criteria 统一防抖 300ms，避免范围切换时重复查询。
 */
@OptIn(FlowPreview::class)
class SearchViewModel(private val repository: DailyTraceRepository) : ViewModel() {

    private data class Criteria(
        val query: String = "",
        val preset: SearchDatePreset = SearchDatePreset.ALL,
        val customFrom: Long? = null,
        val customTo: Long? = null,
    )

    private data class Results(
        val range: SearchDateRange = SearchDateRange(),
        val records: List<RecordHit> = emptyList(),
        val notes: List<NoteHit> = emptyList(),
        val searched: Boolean = false,
    )

    private val criteria = MutableStateFlow(Criteria())
    private val results = MutableStateFlow(Results())

    val uiState: StateFlow<SearchUiState> = combine(criteria, results) { c, r ->
        SearchUiState(
            query = c.query,
            preset = c.preset,
            customFrom = c.customFrom,
            customTo = c.customTo,
            range = r.range,
            records = r.records,
            notes = r.notes,
            searched = r.searched,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchUiState())

    init {
        viewModelScope.launch {
            criteria.debounce(300).distinctUntilChanged().collectLatest { c ->
                val q = c.query.trim()
                val settings = repository.getSettings()
                val clock = DayClock.from(settings)
                val dateDisplay = DateDisplay.from(settings)
                val range = resolveSearchRange(c.preset, clock.today(), c.customFrom, c.customTo)
                if (q.isEmpty()) {
                    results.value = Results(range = range)
                    return@collectLatest
                }
                val recordHits = repository.searchRecords(q, range.from, range.to)
                    .map { RecordHit(it, dateDisplay.short(it.date)) }
                val noteHits = repository.searchDayNotes(q, range.from, range.to)
                    .map { NoteHit(it, dateDisplay.short(it.date)) }
                results.value = Results(range, recordHits, noteHits, searched = true)
            }
        }
    }

    fun setQuery(q: String) = criteria.update { it.copy(query = q) }

    /** 切换预设；自定义日期保留，便于在「不限 / 近 N 天 / 自定义」间来回切换。 */
    fun setPreset(preset: SearchDatePreset) = criteria.update { it.copy(preset = preset) }

    /** 自定义起止（任一可为 null）；起 > 止自动交换，并切到自定义预设。 */
    fun setCustomRange(from: Long?, to: Long?) {
        val normalized = normalizeSearchRange(from, to)
        criteria.update {
            it.copy(
                preset = SearchDatePreset.CUSTOM,
                customFrom = normalized.from,
                customTo = normalized.to,
            )
        }
    }
}
