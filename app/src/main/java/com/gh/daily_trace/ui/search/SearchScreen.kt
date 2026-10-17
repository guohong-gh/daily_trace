@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.gh.daily_trace.ui.search

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.gh.daily_trace.di.Container
import com.gh.daily_trace.ui.common.EmptyState
import com.gh.daily_trace.ui.common.ItemBadge
import com.gh.daily_trace.ui.common.LoadingState
import com.gh.daily_trace.ui.common.StatusPill
import com.gh.daily_trace.ui.common.formatValue

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

            when {
                state.query.isBlank() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
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
                        description = "换个关键词，或检查是否有错别字",
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
