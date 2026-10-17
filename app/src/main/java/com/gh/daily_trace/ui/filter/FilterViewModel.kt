package com.gh.daily_trace.ui.filter

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gh.daily_trace.data.model.CheckItem
import com.gh.daily_trace.data.model.FilterView
import com.gh.daily_trace.data.model.Group
import com.gh.daily_trace.data.repository.DailyTraceRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class FilterUiState(
    val views: List<FilterView> = emptyList(),
    val items: List<CheckItem> = emptyList(),
    val groups: List<Group> = emptyList(),
)

/** 筛选视图管理：保存/编辑/删除；「应用」由页面回调给导航层 */
class FilterViewModel(private val repository: DailyTraceRepository) : ViewModel() {

    val uiState: StateFlow<FilterUiState> = combine(
        repository.filterViews,
        repository.items,
        repository.groups,
    ) { views, items, groups ->
        FilterUiState(views, items, groups)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FilterUiState())

    fun save(view: FilterView) {
        viewModelScope.launch { repository.saveFilterView(view) }
    }

    fun delete(id: Long) {
        viewModelScope.launch { repository.deleteFilterView(id) }
    }
}
