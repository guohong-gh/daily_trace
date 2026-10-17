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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
                navigationIcon = { TextButton(onClick = onBack) { Text("返回") } },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
            OutlinedTextField(
                value = state.query,
                onValueChange = { viewModel.setQuery(it) },
                placeholder = { Text("搜索打卡备注、选项名或日期备注") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))

            when {
                state.query.isBlank() -> Hint("输入关键词开始搜索")

                !state.searched -> Hint("搜索中…")

                state.records.isEmpty() && state.notes.isEmpty() -> Hint("没有匹配结果")

                else -> LazyColumn(
                    contentPadding = PaddingValues(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (state.records.isNotEmpty()) {
                        item(key = "header-records") {
                            Text(
                                "打卡记录（${state.records.size}）",
                                style = MaterialTheme.typography.titleSmall,
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
private fun Hint(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun RecordHitCard(hit: RecordHit, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(hit.record.itemIconSnapshot.ifBlank { "•" }, fontSize = 18.sp)
                Spacer(Modifier.width(8.dp))
                Text(
                    hit.record.itemNameSnapshot,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                Text(hit.dateLabel, style = MaterialTheme.typography.bodySmall)
            }
            val valueText = hit.record.value?.let { formatValue(it) + hit.record.unit }
            val text = listOfNotNull(valueText, hit.record.note.ifBlank { null }).joinToString(" · ")
            if (text.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(text, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun NoteHitCard(hit: NoteHit, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.padding(12.dp)) {
            Text(hit.dateLabel, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(4.dp))
            Text(hit.note.note, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
