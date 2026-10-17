package com.gh.daily_trace.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gh.daily_trace.data.model.CheckRecord
import com.gh.daily_trace.data.model.DayNote
import com.gh.daily_trace.data.repository.DailyTraceRepository
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
import kotlinx.coroutines.launch

data class RecordHit(val record: CheckRecord, val dateLabel: String)

data class NoteHit(val note: DayNote, val dateLabel: String)

data class SearchUiState(
    val query: String = "",
    val records: List<RecordHit> = emptyList(),
    val notes: List<NoteHit> = emptyList(),
    /** 是否已执行过搜索（区分「未搜索」与「无结果」） */
    val searched: Boolean = false,
)

/** 搜索：命中打卡备注/选项快照名 + 日期备注，按日期倒序；输入防抖 300ms */
@OptIn(FlowPreview::class)
class SearchViewModel(private val repository: DailyTraceRepository) : ViewModel() {

    private data class Results(
        val records: List<RecordHit> = emptyList(),
        val notes: List<NoteHit> = emptyList(),
        val searched: Boolean = false,
    )

    private val query = MutableStateFlow("")
    private val results = MutableStateFlow(Results())

    val uiState: StateFlow<SearchUiState> = combine(query, results) { q, r ->
        SearchUiState(query = q, records = r.records, notes = r.notes, searched = r.searched)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchUiState())

    init {
        viewModelScope.launch {
            query.debounce(300).distinctUntilChanged().collectLatest { raw ->
                val q = raw.trim()
                if (q.isEmpty()) {
                    results.value = Results()
                    return@collectLatest
                }
                val clock = DayClock.from(repository.getSettings())
                val recordHits = repository.searchRecords(q)
                    .map { RecordHit(it, clock.format(it.date, "M月d日 EEE")) }
                val noteHits = repository.searchDayNotes(q)
                    .map { NoteHit(it, clock.format(it.date, "M月d日 EEE")) }
                results.value = Results(recordHits, noteHits, searched = true)
            }
        }
    }

    fun setQuery(q: String) {
        query.value = q
    }
}
