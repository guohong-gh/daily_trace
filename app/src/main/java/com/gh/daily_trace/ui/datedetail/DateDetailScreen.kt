@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.gh.daily_trace.ui.datedetail

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalButton
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
import com.gh.daily_trace.di.Container
import com.gh.daily_trace.ui.common.RecordDialog
import com.gh.daily_trace.ui.common.formatValue
import com.gh.daily_trace.ui.common.timeFormatter
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

    LifecycleResumeEffect(Unit) {
        viewModel.refreshToday()
        onPauseOrDispose { }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.title) },
                navigationIcon = { TextButton(onClick = onBack) { Text("返回") } },
            )
        },
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
                    },
                )
            }

            item(key = "records-header") {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "打卡记录（${state.records.size}）",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(1f),
                    )
                    if (state.canCheckIn) {
                        FilledTonalButton(onClick = { pickerOpen = true }) { Text(state.checkInLabel) }
                    }
                }
                state.checkInHint?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall)
                }
            }

            if (state.records.isEmpty()) {
                item(key = "no-records") {
                    Text("当天暂无记录", style = MaterialTheme.typography.bodySmall)
                }
            }
            items(state.records, key = { it.id }) { record ->
                RecordCard(record = record, onDelete = { viewModel.deleteRecord(record.id) })
            }
        }
    }

    if (pickerOpen) {
        AlertDialog(
            onDismissRequest = { pickerOpen = false },
            title = { Text(state.checkInLabel) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    if (state.activeItems.isEmpty()) {
                        Text("没有可用的打卡选项")
                    }
                    state.activeItems.forEach { item ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    recordDialogItem = item
                                    pickerOpen = false
                                }
                                .padding(vertical = 10.dp),
                        ) {
                            Text(item.icon.ifBlank { "•" }, fontSize = 18.sp)
                            Spacer(Modifier.width(8.dp))
                            Text(item.name)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { pickerOpen = false }) { Text("取消") } },
        )
    }

    recordDialogItem?.let { item ->
        RecordDialog(
            item = item,
            onDismiss = { recordDialogItem = null },
            onConfirm = { value, note ->
                viewModel.addRecord(item, value, note)
                recordDialogItem = null
            },
        )
    }
}

@Composable
private fun DayNoteEditor(
    saved: String,
    draft: String?,
    onDraftChange: (String) -> Unit,
    onSave: () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text("当天备注", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = draft ?: saved,
                onValueChange = onDraftChange,
                placeholder = { Text("记录这一天…") },
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                val clearEnabled = (draft ?: saved).isNotEmpty()
                val saveEnabled = draft != null && draft != saved
                TextButton(
                    onClick = { onDraftChange(""); onSave() },
                    enabled = clearEnabled,
                ) { Text("清除") }
                Spacer(Modifier.width(4.dp))
                Button(onClick = onSave, enabled = saveEnabled) { Text("保存备注") }
            }
        }
    }
}

@Composable
private fun RecordCard(record: CheckRecord, onDelete: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(record.itemIconSnapshot.ifBlank { "•" }, fontSize = 18.sp)
                Spacer(Modifier.width(8.dp))
                Text(
                    record.itemNameSnapshot,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                if (record.isBackfill) {
                    Text("补卡", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
                }
                TextButton(onClick = onDelete) { Text("删除") }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    timeFormatter.format(Instant.ofEpochMilli(record.createdAt)),
                    style = MaterialTheme.typography.bodySmall,
                )
                val valueText = record.value?.let { formatValue(it) + record.unit }
                val text = listOfNotNull(valueText, record.note.ifBlank { null }).joinToString(" · ")
                if (text.isNotBlank()) {
                    Spacer(Modifier.width(8.dp))
                    Text(text, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
