@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.gh.daily_trace.ui.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.gh.daily_trace.di.Container
import com.gh.daily_trace.domain.search.SearchDatePreset
import com.gh.daily_trace.ui.common.EmptyState
import com.gh.daily_trace.ui.common.ItemBadge
import com.gh.daily_trace.ui.common.LoadingState
import com.gh.daily_trace.ui.common.StatusPill
import com.gh.daily_trace.ui.common.formatValue
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

private const val MILLIS_PER_DAY = 86_400_000L
private val DAY_LABEL_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd")

private enum class DateBound { START, END }

@Composable
fun SearchRoute(container: Container, onBack: () -> Unit, onOpenDate: (Long) -> Unit) {
    val viewModel: SearchViewModel = viewModel(
        factory = viewModelFactory { initializer { SearchViewModel(container.repository) } }
    )
    SearchScreen(viewModel = viewModel, onBack = onBack, onOpenDate = onOpenDate)
}

@Composable
fun SearchScreen(viewModel: SearchViewModel, onBack: () -> Unit, onOpenDate: (Long) -> Unit) {
    val state by viewModel.uiState.collectAsState()
    var editingBound by remember { mutableStateOf<DateBound?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("搜索") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
            OutlinedTextField(
                value = state.query,
                onValueChange = { viewModel.setQuery(it) },
                placeholder = { Text("搜索打卡备注、选项名或日期备注") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (state.query.isNotEmpty()) {
                        IconButton(onClick = { viewModel.setQuery("") }) {
                            Icon(Icons.Filled.Close, contentDescription = "清空输入")
                        }
                    }
                },
                singleLine = true,
                shape = MaterialTheme.shapes.extraLarge,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))

            SearchRangeChips(preset = state.preset, onSelect = viewModel::setPreset)
            if (state.preset == SearchDatePreset.CUSTOM) {
                Spacer(Modifier.height(8.dp))
                CustomRangeRow(
                    from = state.customFrom,
                    to = state.customTo,
                    onEditStart = { editingBound = DateBound.START },
                    onEditEnd = { editingBound = DateBound.END },
                )
            }
            Spacer(Modifier.height(8.dp))

            Box(Modifier.fillMaxWidth().weight(1f)) {
                when {
                    state.query.isBlank() -> Box(
                        Modifier.fillMaxSize(),
                        contentAlignment = Alignment.TopCenter,
                    ) {
                        EmptyState(
                            emoji = "🔎",
                            title = "搜索历史记录",
                            description = "输入关键词，搜索打卡备注、选项名与日期备注",
                        )
                    }

                    !state.searched -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                        LoadingState(text = "搜索中…")
                    }

                    state.records.isEmpty() && state.notes.isEmpty() -> Box(
                        Modifier.fillMaxSize(),
                        contentAlignment = Alignment.TopCenter,
                    ) {
                        EmptyState(
                            emoji = "🫥",
                            title = "没有匹配结果",
                            description = if (state.range.active) {
                                "换个关键词，或放宽日期范围"
                            } else {
                                "换个关键词，或检查是否有错别字"
                            },
                        )
                    }

                    else -> LazyColumn(
                        contentPadding = PaddingValues(bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (state.records.isNotEmpty()) {
                            item(key = "header-records") {
                                Text(
                                    "打卡记录（${state.records.size}）",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 8.dp),
                                )
                            }
                            items(state.records, key = { "r-${it.record.id}" }) { hit ->
                                RecordHitCard(hit) { onOpenDate(hit.record.date) }
                            }
                        }
                        if (state.notes.isNotEmpty()) {
                            item(key = "header-notes") {
                                Text(
                                    "日期备注（${state.notes.size}）",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 8.dp),
                                )
                            }
                            items(state.notes, key = { "n-${it.note.date}" }) { hit ->
                                NoteHitCard(hit) { onOpenDate(hit.note.date) }
                            }
                        }
                    }
                }
            }
        }
    }

    editingBound?.let { bound ->
        val initialMillis = when (bound) {
            DateBound.START -> state.customFrom
            DateBound.END -> state.customTo
        }?.let { it * MILLIS_PER_DAY }
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
        DatePickerDialog(
            onDismissRequest = { editingBound = null },
            confirmButton = {
                TextButton(
                    enabled = pickerState.selectedDateMillis != null,
                    onClick = {
                        pickerState.selectedDateMillis?.let { millis ->
                            // Material3 DatePicker 的选择值为 UTC 零点毫秒，转 epochDay 不涉及时区
                            val day = Instant.ofEpochMilli(millis)
                                .atZone(ZoneOffset.UTC)
                                .toLocalDate()
                                .toEpochDay()
                            when (bound) {
                                DateBound.START -> viewModel.setCustomRange(day, state.customTo)
                                DateBound.END -> viewModel.setCustomRange(state.customFrom, day)
                            }
                        }
                        editingBound = null
                    },
                ) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { editingBound = null }) { Text("取消") }
            },
        ) {
            DatePicker(state = pickerState)
        }
    }
}

@Composable
private fun SearchRangeChips(preset: SearchDatePreset, onSelect: (SearchDatePreset) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(
            selected = preset == SearchDatePreset.ALL,
            onClick = { onSelect(SearchDatePreset.ALL) },
            label = { Text("不限") },
        )
        FilterChip(
            selected = preset == SearchDatePreset.LAST_7,
            onClick = { onSelect(SearchDatePreset.LAST_7) },
            label = { Text("近 7 天") },
        )
        FilterChip(
            selected = preset == SearchDatePreset.LAST_30,
            onClick = { onSelect(SearchDatePreset.LAST_30) },
            label = { Text("近 30 天") },
        )
        FilterChip(
            selected = preset == SearchDatePreset.CUSTOM,
            onClick = { onSelect(SearchDatePreset.CUSTOM) },
            label = { Text("自定义") },
        )
    }
}

@Composable
private fun CustomRangeRow(
    from: Long?,
    to: Long?,
    onEditStart: () -> Unit,
    onEditEnd: () -> Unit,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = onEditStart, modifier = Modifier.weight(1f)) {
            Text(
                text = "起始 ${from?.let(::formatDay) ?: "未选"}",
                maxLines = 1,
                style = MaterialTheme.typography.labelLarge,
            )
        }
        OutlinedButton(onClick = onEditEnd, modifier = Modifier.weight(1f)) {
            Text(
                text = "结束 ${to?.let(::formatDay) ?: "未选"}",
                maxLines = 1,
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

private fun formatDay(epochDay: Long): String =
    LocalDate.ofEpochDay(epochDay).format(DAY_LABEL_FORMATTER)

@Composable
private fun RecordHitCard(hit: RecordHit, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ItemBadge(
                    icon = hit.record.itemIconSnapshot,
                    color = hit.record.itemColorSnapshot,
                    size = 32.dp,
                    iconSize = 16.sp,
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    hit.record.itemNameSnapshot,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                StatusPill(hit.dateLabel, color = MaterialTheme.colorScheme.secondary)
            }
            val valueText = hit.record.value?.let { formatValue(it) + hit.record.unit }
            val text = listOfNotNull(valueText, hit.record.note.ifBlank { null }).joinToString(" · ")
            if (text.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 44.dp),
                )
            }
        }
    }
}

@Composable
private fun NoteHitCard(hit: NoteHit, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.padding(12.dp)) {
            StatusPill(hit.dateLabel, color = MaterialTheme.colorScheme.secondary)
            Spacer(Modifier.height(6.dp))
            Text(hit.note.note, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
