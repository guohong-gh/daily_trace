@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.gh.daily_trace.ui.filter

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.gh.daily_trace.data.model.CheckItem
import com.gh.daily_trace.data.model.DayFilter
import com.gh.daily_trace.data.model.FilterLogic
import com.gh.daily_trace.data.model.FilterMode
import com.gh.daily_trace.data.model.FilterView
import com.gh.daily_trace.data.model.Group
import com.gh.daily_trace.di.Container

@Composable
fun FilterRoute(container: Container, onBack: () -> Unit, onApply: (Long) -> Unit) {
    val viewModel: FilterViewModel = viewModel(
        factory = viewModelFactory { initializer { FilterViewModel(container.repository) } }
    )
    FilterScreen(viewModel = viewModel, onBack = onBack, onApply = onApply)
}

@Composable
fun FilterScreen(viewModel: FilterViewModel, onBack: () -> Unit, onApply: (Long) -> Unit) {
    val state by viewModel.uiState.collectAsState()
    // null = 列表模式；非 null = 编辑草稿（id=0 表示新建）
    var editing by remember { mutableStateOf<FilterView?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (editing == null) "筛选视图" else "编辑筛选视图") },
                navigationIcon = {
                    TextButton(onClick = { if (editing != null) editing = null else onBack() }) { Text("返回") }
                },
            )
        },
    ) { padding ->
        val draft = editing
        if (draft == null) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = padding.calculateTopPadding() + 8.dp,
                    bottom = padding.calculateBottomPadding() + 24.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item(key = "new") {
                    FilledTonalButton(onClick = { editing = FilterView(name = "") }) { Text("新建筛选视图") }
                }
                if (state.views.isEmpty()) {
                    item(key = "empty") {
                        Text(
                            "还没有保存的筛选视图。新建后可在今日页「更多 → 筛选视图」中应用。",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                items(state.views, key = { it.id }) { view ->
                    ViewCard(
                        view = view,
                        summary = summaryOf(view),
                        onApply = { onApply(view.id) },
                        onEdit = { editing = view },
                        onDelete = { viewModel.delete(view.id) },
                    )
                }
            }
        } else {
            FilterEditor(
                draft = draft,
                items = state.items,
                groups = state.groups,
                onCancel = { editing = null },
                onSave = { viewModel.save(it); editing = null },
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = padding.calculateTopPadding() + 8.dp,
                    bottom = padding.calculateBottomPadding() + 24.dp,
                ),
            )
        }
    }
}

@Composable
private fun ViewCard(
    view: FilterView,
    summary: String,
    onApply: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text(view.name.ifBlank { "未命名视图" }, style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(4.dp))
            Text(summary, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = onDelete) { Text("删除") }
                TextButton(onClick = onEdit) { Text("编辑") }
                Button(onClick = onApply) { Text("应用") }
            }
        }
    }
}

@Composable
private fun FilterEditor(
    draft: FilterView,
    items: List<CheckItem>,
    groups: List<Group>,
    onCancel: () -> Unit,
    onSave: (FilterView) -> Unit,
    contentPadding: PaddingValues,
) {
    var name by remember(draft) { mutableStateOf(draft.name) }
    var mode by remember(draft) { mutableStateOf(draft.mode) }
    var itemIds by remember(draft) { mutableStateOf(draft.itemIds.toSet()) }
    var groupIds by remember(draft) { mutableStateOf(draft.groupIds.toSet()) }
    var logic by remember(draft) { mutableStateOf(draft.logic) }
    var dayFilter by remember(draft) { mutableStateOf(draft.dayFilter) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "name") {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("视图名称") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        item(key = "mode") {
            Text("范围", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = mode == FilterMode.ALL,
                    onClick = { mode = FilterMode.ALL },
                    label = { Text("全部选项") },
                )
                FilterChip(
                    selected = mode == FilterMode.SELECTED,
                    onClick = { mode = FilterMode.SELECTED },
                    label = { Text("仅选中") },
                )
            }
        }

        if (mode == FilterMode.SELECTED) {
            item(key = "items") {
                Text("按选项（可多选）", style = MaterialTheme.typography.labelLarge)
                Column {
                    items.forEach { item ->
                        CheckRow(
                            label = item.name,
                            checked = item.id in itemIds,
                            onCheckedChange = { checked ->
                                itemIds = if (checked) itemIds + item.id else itemIds - item.id
                            },
                        )
                    }
                    if (items.isEmpty()) Text("暂无选项", style = MaterialTheme.typography.bodySmall)
                }
            }
            item(key = "groups") {
                Text("按分组（可多选）", style = MaterialTheme.typography.labelLarge)
                Column {
                    groups.forEach { group ->
                        CheckRow(
                            label = group.name,
                            checked = group.id in groupIds,
                            onCheckedChange = { checked ->
                                groupIds = if (checked) groupIds + group.id else groupIds - group.id
                            },
                        )
                    }
                    if (groups.isEmpty()) Text("暂无分组", style = MaterialTheme.typography.bodySmall)
                }
            }
            item(key = "logic") {
                Text("选项与分组的关系", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = logic == FilterLogic.ANY,
                        onClick = { logic = FilterLogic.ANY },
                        label = { Text("任一命中") },
                    )
                    FilterChip(
                        selected = logic == FilterLogic.ALL,
                        onClick = { logic = FilterLogic.ALL },
                        label = { Text("全部命中") },
                    )
                }
            }
        }

        item(key = "day") {
            Text("日期状态", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = dayFilter == DayFilter.ALL,
                    onClick = { dayFilter = DayFilter.ALL },
                    label = { Text("全部") },
                )
                FilterChip(
                    selected = dayFilter == DayFilter.RECORDED,
                    onClick = { dayFilter = DayFilter.RECORDED },
                    label = { Text("已打卡") },
                )
                FilterChip(
                    selected = dayFilter == DayFilter.UNRECORDED,
                    onClick = { dayFilter = DayFilter.UNRECORDED },
                    label = { Text("未打卡") },
                )
            }
        }

        item(key = "actions") {
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = onCancel) { Text("取消") }
                Spacer(Modifier.width(4.dp))
                Button(
                    onClick = {
                        onSave(
                            draft.copy(
                                name = name.trim(),
                                mode = mode,
                                itemIds = itemIds.toList(),
                                groupIds = groupIds.toList(),
                                logic = logic,
                                dayFilter = dayFilter,
                            )
                        )
                    },
                    enabled = name.isNotBlank(),
                ) { Text("保存") }
            }
        }
    }
}

@Composable
private fun CheckRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().height(40.dp),
    ) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        Text(label)
    }
}

/** 卡片上的条件摘要 */
private fun summaryOf(view: FilterView): String {
    val base = if (view.mode == FilterMode.ALL) {
        "全部选项"
    } else {
        val parts = listOfNotNull(
            view.itemIds.takeIf { it.isNotEmpty() }?.let { "选项 ${it.size} 个" },
            view.groupIds.takeIf { it.isNotEmpty() }?.let { "分组 ${it.size} 个" },
        ).joinToString(" + ")
        if (parts.isEmpty()) "未选条件（等同全部）"
        else "$parts（${if (view.logic == FilterLogic.ANY) "任一" else "全部"}命中）"
    }
    val day = when (view.dayFilter) {
        DayFilter.ALL -> ""
        DayFilter.RECORDED -> " · 仅已打卡"
        DayFilter.UNRECORDED -> " · 仅未打卡"
    }
    return base + day
}
