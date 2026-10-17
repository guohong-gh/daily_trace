package com.gh.daily_trace.ui.today

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.gh.daily_trace.data.model.CheckItem
import com.gh.daily_trace.data.model.CheckRecord
import com.gh.daily_trace.data.model.CheckType
import com.gh.daily_trace.di.Container
import com.gh.daily_trace.ui.common.RecordDialog
import com.gh.daily_trace.ui.common.formatTarget
import com.gh.daily_trace.ui.common.formatValue
import com.gh.daily_trace.ui.common.timeFormatter
import java.time.Instant

@Composable
fun TodayRoute(
    container: Container,
    onManage: () -> Unit,
    onSettings: () -> Unit,
    onCalendar: () -> Unit,
    onSearch: () -> Unit,
    onFilter: () -> Unit,
    onStats: () -> Unit,
    appliedFilterId: Long,
    onClearFilter: () -> Unit,
) {
    val viewModel: TodayViewModel = viewModel(
        factory = viewModelFactory { initializer { TodayViewModel(container.repository) } }
    )
    LaunchedEffect(appliedFilterId) {
        viewModel.setFilter(appliedFilterId)
    }
    TodayScreen(
        viewModel = viewModel,
        onManage = onManage,
        onSettings = onSettings,
        onCalendar = onCalendar,
        onSearch = onSearch,
        onFilter = onFilter,
        onStats = onStats,
        onClearFilter = onClearFilter,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayScreen(
    viewModel: TodayViewModel,
    onManage: () -> Unit,
    onSettings: () -> Unit,
    onCalendar: () -> Unit,
    onSearch: () -> Unit,
    onFilter: () -> Unit,
    onStats: () -> Unit,
    onClearFilter: () -> Unit,
) {
    val state by viewModel.uiState.collectAsState()
    var recordDialogItem by remember { mutableStateOf<CheckItem?>(null) }
    var menuOpen by remember { mutableStateOf(false) }

    LifecycleResumeEffect(Unit) {
        viewModel.refreshToday()
        onPauseOrDispose { }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("今日打卡")
                        Text(state.dateLabel, style = MaterialTheme.typography.bodySmall)
                    }
                },
                actions = {
                    TextButton(onClick = onCalendar) { Text("日历") }
                    TextButton(onClick = onSearch) { Text("搜索") }
                    Box {
                        TextButton(onClick = { menuOpen = true }) { Text("更多") }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text("统计") },
                                onClick = { menuOpen = false; onStats() },
                            )
                            DropdownMenuItem(
                                text = { Text("筛选视图") },
                                onClick = { menuOpen = false; onFilter() },
                            )
                            DropdownMenuItem(
                                text = { Text("管理选项") },
                                onClick = { menuOpen = false; onManage() },
                            )
                            DropdownMenuItem(
                                text = { Text("设置") },
                                onClick = { menuOpen = false; onSettings() },
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        if (state.sections.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (state.itemsBeforeFilter == 0) {
                        Text("还没有打卡选项")
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = onManage) { Text("去创建") }
                    } else {
                        Text("筛选后无匹配选项")
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = onClearFilter) { Text("清除筛选") }
                    }
                }
            }
        } else {
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
                state.filterName?.let { name ->
                    item(key = "filter-chip") {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Text(
                                "筛选：$name",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(onClick = onClearFilter) { Text("清除") }
                        }
                    }
                }
                item(key = "progress") {
                    val progress = if (state.totalCount == 0) 0f else state.doneCount * 1f / state.totalCount
                    Column {
                        Text("已打卡 ${state.doneCount} / ${state.totalCount}", style = MaterialTheme.typography.labelLarge)
                        Spacer(Modifier.height(4.dp))
                        LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                    }
                }
                state.sections.forEach { section ->
                    item(key = "section-${section.title}") {
                        Text(
                            section.title,
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                    items(section.rows, key = { it.item.id }) { row ->
                        ItemCard(
                            row = row,
                            onToggleBoolean = { viewModel.toggleBoolean(row.item) },
                            onQuickAdd = { viewModel.addValue(row.item, 1.0, "") },
                            onOpenRecord = { recordDialogItem = row.item },
                            onDeleteRecord = { id -> viewModel.deleteRecord(id) },
                        )
                    }
                }
            }
        }
    }

    recordDialogItem?.let { item ->
        RecordDialog(
            item = item,
            onDismiss = { recordDialogItem = null },
            onConfirm = { value, note ->
                viewModel.addValue(item, value, note)
                recordDialogItem = null
            },
        )
    }
}

@Composable
private fun ItemCard(
    row: ItemRow,
    onToggleBoolean: () -> Unit,
    onQuickAdd: () -> Unit,
    onOpenRecord: () -> Unit,
    onDeleteRecord: (Long) -> Unit,
) {
    val item = row.item
    var expanded by rememberSaveable(item.id) { mutableStateOf(false) }

    Card(modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded }) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(item.icon.ifBlank { "•" }, fontSize = 22.sp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(item.name, style = MaterialTheme.typography.titleMedium)
                    val subtitle = buildString {
                        if (item.type != CheckType.BOOLEAN) {
                            append(formatTarget(item))
                            if (row.records.isNotEmpty()) {
                                append(" · 今日 ${formatValue(row.totalValue)}${item.unit}")
                            }
                        } else if (row.done) {
                            append("今日已完成")
                        }
                    }
                    if (subtitle.isNotBlank()) {
                        Text(subtitle, style = MaterialTheme.typography.bodySmall)
                    }
                }
                when (item.type) {
                    CheckType.BOOLEAN -> Checkbox(checked = row.done, onCheckedChange = { onToggleBoolean() })
                    CheckType.COUNT -> FilledTonalButton(
                        onClick = onQuickAdd,
                        contentPadding = PaddingValues(horizontal = 14.dp),
                    ) { Text("+1") }
                    else -> OutlinedButton(onClick = onOpenRecord) { Text("记一笔") }
                }
            }

            if (expanded) {
                Spacer(Modifier.height(8.dp))
                when (item.type) {
                    CheckType.BOOLEAN -> OutlinedButton(
                        onClick = onOpenRecord,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("带备注打卡") }
                    CheckType.COUNT -> OutlinedButton(
                        onClick = onOpenRecord,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("记一笔（可备注）") }
                    else -> Unit
                }
                if (row.records.isEmpty()) {
                    Text(
                        "今日暂无记录",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                row.records.forEach { record ->
                    RecordLine(record = record, unit = item.unit, onDelete = { onDeleteRecord(record.id) })
                }
            }
        }
    }
}

@Composable
private fun RecordLine(record: CheckRecord, unit: String, onDelete: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
    ) {
        Text(
            timeFormatter.format(Instant.ofEpochMilli(record.createdAt)),
            style = MaterialTheme.typography.bodySmall,
        )
        Spacer(Modifier.width(8.dp))
        val valueText = record.value?.let { formatValue(it) + unit }
        val text = listOfNotNull(valueText, record.note.ifBlank { null }).joinToString(" · ")
        Text(text, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
        TextButton(onClick = onDelete) { Text("删除") }
    }
}
