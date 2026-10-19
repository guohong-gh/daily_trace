@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class,
)

package com.gh.daily_trace.ui.datedetail

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.gh.daily_trace.data.model.CheckItem
import com.gh.daily_trace.data.model.CheckRecord
import com.gh.daily_trace.data.model.CheckType
import com.gh.daily_trace.data.model.RecordStatus
import com.gh.daily_trace.di.Container
import com.gh.daily_trace.ui.common.EmptyState
import com.gh.daily_trace.ui.common.ItemBadge
import com.gh.daily_trace.ui.common.RecordDialog
import com.gh.daily_trace.ui.common.RecordStatusPill
import com.gh.daily_trace.ui.common.SectionCard
import com.gh.daily_trace.ui.common.SectionTitle
import com.gh.daily_trace.ui.common.SelectionActionBar
import com.gh.daily_trace.ui.common.StatusPill
import com.gh.daily_trace.ui.common.formatValue
import com.gh.daily_trace.ui.common.rememberSelectedIds
import com.gh.daily_trace.ui.common.timeFormatter
import kotlinx.coroutines.launch
import java.time.Instant

@Composable
fun DateDetailRoute(container: Container, date: Long, onBack: () -> Unit) {
    val viewModel: DateDetailViewModel = viewModel(
        key = "date-detail-$date",
        factory = viewModelFactory { initializer { DateDetailViewModel(container.repository, date) } },
    )
    DateDetailScreen(viewModel = viewModel, onBack = onBack)
}

@Composable
fun DateDetailScreen(viewModel: DateDetailViewModel, onBack: () -> Unit) {
    val state by viewModel.uiState.collectAsState()

    // null = 未编辑，直接展示已保存值；非 null = 本地草稿
    var noteDraft by rememberSaveable(state.date) { mutableStateOf<String?>(null) }
    var pickerOpen by rememberSaveable { mutableStateOf(false) }
    var recordDialogItem by remember { mutableStateOf<CheckItem?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // 选择器多选（弹窗内）与记录区多选（长按进入）
    var pickerSelected by remember { mutableStateOf(emptySet<Long>()) }
    var pickerBatchValues by remember { mutableStateOf<Map<Long, Double?>?>(null) }
    var recordsSelecting by rememberSaveable { mutableStateOf(false) }
    var recordSelection by rememberSelectedIds()
    var noteDialogOpen by remember { mutableStateOf(false) }
    var deleteConfirmOpen by remember { mutableStateOf(false) }

    val exitRecordSelection: () -> Unit = {
        recordsSelecting = false
        recordSelection = emptySet()
    }
    BackHandler(enabled = recordsSelecting) { exitRecordSelection() }

    LifecycleResumeEffect(Unit) {
        viewModel.refreshToday()
        onPauseOrDispose { }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (recordsSelecting) {
                        Text("已选 ${recordSelection.size} 项")
                    } else {
                        Text(state.title)
                    }
                },
                navigationIcon = {
                    if (recordsSelecting) {
                        IconButton(onClick = exitRecordSelection) {
                            Icon(Icons.Filled.Close, contentDescription = "退出多选")
                        }
                    } else {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                        }
                    }
                },
                actions = {
                    if (recordsSelecting) {
                        val allIds = state.records.map { it.id }.toSet()
                        val allSelected = allIds.isNotEmpty() && allIds.all { it in recordSelection }
                        TextButton(onClick = {
                            recordSelection = if (allSelected) emptySet() else allIds
                        }) {
                            Text(if (allSelected) "取消全选" else "全选")
                        }
                    }
                },
            )
        },
        bottomBar = {
            if (recordsSelecting) {
                SelectionActionBar {
                    FilledTonalButton(
                        onClick = { noteDialogOpen = true },
                        enabled = recordSelection.isNotEmpty(),
                        modifier = Modifier.weight(1f),
                    ) { Text("改备注") }
                    Button(
                        onClick = { deleteConfirmOpen = true },
                        enabled = recordSelection.isNotEmpty(),
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        ),
                    ) { Text("删除") }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
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
            item(key = "day-note") {
                DayNoteEditor(
                    saved = state.dayNote,
                    draft = noteDraft,
                    onDraftChange = { noteDraft = it },
                    onSave = {
                        noteDraft?.let { viewModel.saveDayNote(it) }
                        noteDraft = null
                        scope.launch { snackbarHostState.showSnackbar("备注已保存") }
                    },
                )
            }

            item(key = "records-header") {
                val skippedRows = state.pickerRows.filter { it.skipped }
                Column {
                    SectionTitle("打卡记录（${state.records.size}）") {
                        if (!recordsSelecting) {
                            FilledTonalButton(onClick = {
                                pickerSelected = emptySet()
                                pickerBatchValues = null
                                pickerOpen = true
                                scope.launch { pickerBatchValues = viewModel.loadBatchValues() }
                            }) {
                                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(state.checkInLabel)
                            }
                        }
                    }
                    state.checkInHint?.let {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (skippedRows.isNotEmpty()) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "已跳过：${skippedRows.joinToString("、") { it.item.name }}（不计入完成率，不中断连续）",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.tertiary,
                        )
                    }
                }
            }

            if (state.records.isEmpty()) {
                item(key = "no-records") {
                    Box(Modifier.fillMaxWidth().padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
                        EmptyState(
                            emoji = "🗒️",
                            title = "当天暂无打卡记录",
                            description = when {
                                state.canPlan -> "点上方「添加计划」为这天做准备"
                                state.canRecord -> "点上方「${state.checkInLabel}」补记一笔"
                                else -> null
                            },
                        )
                    }
                }
            }
            items(state.records, key = { it.id }) { record ->
                RecordCard(
                    record = record,
                    selecting = recordsSelecting,
                    selected = record.id in recordSelection,
                    onLongPress = {
                        recordsSelecting = true
                        recordSelection = setOf(record.id)
                    },
                    onToggleSelect = {
                        recordSelection = if (record.id in recordSelection) {
                            recordSelection - record.id
                        } else {
                            recordSelection + record.id
                        }
                    },
                    onDelete = {
                        viewModel.deleteRecord(record.id)
                        scope.launch { snackbarHostState.showSnackbar("已删除该条记录") }
                    },
                )
            }
        }
    }

    if (pickerOpen) {
        AlertDialog(
            onDismissRequest = { pickerOpen = false },
            title = { Text(state.checkInLabel) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    when {
                        state.canPlan -> PickerHint("未来日期：勾选多项可批量创建「计划中」；点选项行可单笔填写；右侧「跳过」登记请假/休息日")
                        !state.canRecord -> PickerHint("超出补卡上限，仅可标记跳过")
                        else -> PickerHint("勾选多项可批量记录（默认值直记）；点选项行可单笔填写数值/备注")
                    }
                    if (state.pickerRows.isEmpty()) {
                        Text("没有可用的打卡选项")
                    }
                    state.pickerRows.forEach { row ->
                        val batchValue = pickerBatchValues?.get(row.item.id)
                        val batchable = state.canRecord && (
                            row.item.type == CheckType.BOOLEAN ||
                                row.item.type == CheckType.COUNT ||
                                row.item.targetValue != null ||
                                pickerBatchValues == null ||
                                batchValue != null
                            )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Checkbox(
                                checked = row.item.id in pickerSelected,
                                onCheckedChange = { checked ->
                                    pickerSelected = if (checked) {
                                        pickerSelected + row.item.id
                                    } else {
                                        pickerSelected - row.item.id
                                    }
                                },
                                enabled = batchable,
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(MaterialTheme.shapes.small)
                                    .clickable(enabled = state.canRecord) {
                                        recordDialogItem = row.item
                                        pickerOpen = false
                                    }
                                    .padding(vertical = 10.dp),
                            ) {
                                ItemBadge(icon = row.item.icon, color = row.item.color, size = 32.dp, iconSize = 16.sp)
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Text(
                                        row.item.name,
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = if (state.canRecord) {
                                            MaterialTheme.colorScheme.onSurface
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                    )
                                    if (row.stateText.isNotBlank()) {
                                        Text(
                                            row.stateText,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                            TextButton(
                                onClick = { viewModel.toggleSkip(row.item) },
                                enabled = row.allowSkip,
                            ) { Text(if (row.skipped) "取消跳过" else "跳过") }
                        }
                    }
                }
            },
            confirmButton = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (state.canRecord) {
                        Button(
                            onClick = {
                                val ids = pickerSelected
                                val planMode = state.canPlan
                                scope.launch {
                                    val result = viewModel.batchAddRecords(ids)
                                    pickerOpen = false
                                    pickerSelected = emptySet()
                                    pickerBatchValues = null
                                    val message = buildString {
                                        if (result.recorded > 0) {
                                            append(if (planMode) "已创建 ${result.recorded} 项计划" else "已记录 ${result.recorded} 项")
                                        } else {
                                            append("没有可记录的选项")
                                        }
                                        if (result.skipped > 0) append("（跳过 ${result.skipped} 项）")
                                    }
                                    snackbarHostState.showSnackbar(message)
                                }
                            },
                            enabled = pickerSelected.isNotEmpty(),
                        ) { Text("记录所选（${pickerSelected.size}）") }
                        Spacer(Modifier.width(8.dp))
                    }
                    TextButton(onClick = { pickerOpen = false }) { Text("关闭") }
                }
            },
        )
    }

    recordDialogItem?.let { item ->
        RecordDialog(
            item = item,
            onDismiss = { recordDialogItem = null },
            onConfirm = { value, note, status ->
                viewModel.addRecord(item, value, note, status)
                recordDialogItem = null
                val message = when (status) {
                    RecordStatus.PLANNED -> "已创建计划"
                    RecordStatus.PARTIAL -> "已记录（部分完成）"
                    else -> "已记录"
                }
                scope.launch { snackbarHostState.showSnackbar(message) }
            },
            allowPartial = !state.canPlan,
            planMode = state.canPlan,
        )
    }

    if (noteDialogOpen) {
        var noteText by rememberSaveable { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { noteDialogOpen = false },
            title = { Text("批量改备注") },
            text = {
                Column {
                    Text(
                        "将统一覆盖所选 ${recordSelection.size} 条记录的备注；「清除备注」将清空所选备注。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = noteText,
                        onValueChange = { noteText = it },
                        placeholder = { Text("输入备注…") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = noteText.isNotBlank(),
                    onClick = {
                        val ids = recordSelection
                        viewModel.setRecordsNote(ids, noteText.trim())
                        noteDialogOpen = false
                        exitRecordSelection()
                        scope.launch { snackbarHostState.showSnackbar("已更新 ${ids.size} 条记录的备注") }
                    },
                ) { Text("应用") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        val ids = recordSelection
                        viewModel.setRecordsNote(ids, "")
                        noteDialogOpen = false
                        exitRecordSelection()
                        scope.launch { snackbarHostState.showSnackbar("已清除 ${ids.size} 条记录的备注") }
                    }) { Text("清除备注") }
                    TextButton(onClick = { noteDialogOpen = false }) { Text("取消") }
                }
            },
        )
    }

    if (deleteConfirmOpen) {
        AlertDialog(
            onDismissRequest = { deleteConfirmOpen = false },
            title = { Text("删除记录") },
            text = { Text("将删除所选的 ${recordSelection.size} 条记录，此操作不可恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    val ids = recordSelection
                    viewModel.deleteRecords(ids)
                    deleteConfirmOpen = false
                    exitRecordSelection()
                    scope.launch { snackbarHostState.showSnackbar("已删除 ${ids.size} 条记录") }
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleteConfirmOpen = false }) { Text("取消") } },
        )
    }
}

@Composable
private fun PickerHint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 4.dp),
    )
}

@Composable
private fun DayNoteEditor(
    saved: String,
    draft: String?,
    onDraftChange: (String) -> Unit,
    onSave: () -> Unit,
) {
    SectionCard(title = "当天备注") {
        OutlinedTextField(
            value = draft ?: saved,
            onValueChange = onDraftChange,
            placeholder = { Text("记录这一天的想法…") },
            minLines = 2,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
            val clearEnabled = (draft ?: saved).isNotEmpty()
            val saveEnabled = draft != null && draft != saved
            TextButton(
                onClick = { onDraftChange(""); onSave() },
                enabled = clearEnabled,
            ) { Text("清除") }
            Spacer(Modifier.width(8.dp))
            Button(onClick = onSave, enabled = saveEnabled) { Text("保存备注") }
        }
    }
}

@Composable
private fun RecordCard(
    record: CheckRecord,
    selecting: Boolean = false,
    selected: Boolean = false,
    onLongPress: () -> Unit = {},
    onToggleSelect: () -> Unit = {},
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { if (selecting) onToggleSelect() },
                onLongClick = { if (!selecting) onLongPress() },
            ),
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                if (selecting) {
                    Checkbox(checked = selected, onCheckedChange = { onToggleSelect() })
                    Spacer(Modifier.width(4.dp))
                }
                ItemBadge(
                    icon = record.itemIconSnapshot,
                    color = record.itemColorSnapshot,
                    size = 32.dp,
                    iconSize = 16.sp,
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    record.itemNameSnapshot,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                if (record.isBackfill) {
                    StatusPill("补卡")
                    Spacer(Modifier.width(4.dp))
                }
                RecordStatusPill(record.status)
                if (!selecting) {
                    IconButton(onClick = onDelete) {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = "删除这条记录",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 44.dp)) {
                Text(
                    timeFormatter.format(Instant.ofEpochMilli(record.createdAt)),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val valueText = record.value?.let { formatValue(it) + record.unit }
                val text = listOfNotNull(valueText, record.note.ifBlank { null }).joinToString(" · ")
                if (text.isNotBlank()) {
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}
