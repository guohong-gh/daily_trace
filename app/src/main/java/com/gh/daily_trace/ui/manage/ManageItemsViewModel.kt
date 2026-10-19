package com.gh.daily_trace.ui.manage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gh.daily_trace.data.model.CheckItem
import com.gh.daily_trace.data.repository.DailyTraceRepository
import com.gh.daily_trace.reminder.ReminderScheduler
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ManageRow(val item: CheckItem, val groupName: String?)

data class ManageSections(
    val active: List<ManageRow> = emptyList(),
    val disabled: List<ManageRow> = emptyList(),
    val archived: List<ManageRow> = emptyList(),
    val trashed: List<ManageRow> = emptyList(),
) {
    val isEmpty: Boolean get() = active.isEmpty() && disabled.isEmpty() && archived.isEmpty() && trashed.isEmpty()
}

/**
 * 选项管理：使用中 / 已停用 / 已归档 / 回收站 四个分区；
 * 排序仅作用于「使用中」列表（按当前显示顺序重写 sort）。
 */
class ManageItemsViewModel(
    private val repository: DailyTraceRepository,
    private val scheduler: ReminderScheduler,
) : ViewModel() {

    val uiState: StateFlow<ManageSections> = combine(
        repository.items,
        repository.groups,
    ) { items, groups ->
        val groupNames = groups.associate { it.id to it.name }
        fun ManageRowOf(item: CheckItem) = ManageRow(item, item.groupId?.let { groupNames[it] })

        ManageSections(
            active = items.filter { it.deletedAt == null && !it.archived && it.enabled }.map(::ManageRowOf),
            disabled = items.filter { it.deletedAt == null && !it.archived && !it.enabled }.map(::ManageRowOf),
            archived = items.filter { it.deletedAt == null && it.archived }.map(::ManageRowOf),
            trashed = items.filter { it.deletedAt != null }.map(::ManageRowOf),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ManageSections())

    fun moveUp(item: CheckItem) = viewModelScope.launch { move(item, -1) }

    fun moveDown(item: CheckItem) = viewModelScope.launch { move(item, +1) }

    fun setEnabled(item: CheckItem, enabled: Boolean) = viewModelScope.launch {
        repository.updateItem(item.copy(enabled = enabled, updatedAt = System.currentTimeMillis()))
        scheduler.rescheduleAll()
    }

    fun setArchived(item: CheckItem, archived: Boolean) = viewModelScope.launch {
        repository.setItemArchived(item.id, archived)
        scheduler.rescheduleAll()
    }

    fun moveToTrash(item: CheckItem) = viewModelScope.launch {
        repository.softDeleteItem(item.id)
        scheduler.rescheduleAll()
    }

    fun restore(item: CheckItem) = viewModelScope.launch {
        repository.restoreItem(item.id)
        scheduler.rescheduleAll()
    }

    /** 彻底删除（回收站内） */
    fun purge(item: CheckItem) = viewModelScope.launch {
        repository.purgeItem(item.id)
        scheduler.rescheduleAll()
    }

    /** 批量归档 / 取消归档 */
    fun archiveAll(ids: Set<Long>, archived: Boolean) = viewModelScope.launch {
        if (ids.isEmpty()) return@launch
        ids.sorted().forEach { repository.setItemArchived(it, archived) }
        scheduler.rescheduleAll()
    }

    /** 批量移入回收站 */
    fun trashAll(ids: Set<Long>) = viewModelScope.launch {
        if (ids.isEmpty()) return@launch
        ids.sorted().forEach { repository.softDeleteItem(it) }
        scheduler.rescheduleAll()
    }

    /** 批量恢复 */
    fun restoreAll(ids: Set<Long>) = viewModelScope.launch {
        if (ids.isEmpty()) return@launch
        ids.sorted().forEach { repository.restoreItem(it) }
        scheduler.rescheduleAll()
    }

    /** 批量彻底删除 */
    fun purgeAll(ids: Set<Long>) = viewModelScope.launch {
        if (ids.isEmpty()) return@launch
        ids.sorted().forEach { repository.purgeItem(it) }
        scheduler.rescheduleAll()
    }

    private suspend fun move(item: CheckItem, delta: Int) {
        val actives = repository.getAllItems()
            .filter { !it.archived && it.enabled } // getAll 已按 sort,id 排序且不含回收站
        val index = actives.indexOfFirst { it.id == item.id }
        if (index < 0) return
        val target = index + delta
        if (target !in actives.indices) return

        val reordered = actives.toMutableList().apply { add(target, removeAt(index)) }
        reordered.forEachIndexed { i, it ->
            if (it.sort != i) repository.setItemSort(it.id, i)
        }
    }
}
