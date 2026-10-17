@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.gh.daily_trace.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.gh.daily_trace.di.Container
import java.time.LocalDate

@Composable
fun CalendarRoute(container: Container, onBack: () -> Unit, onOpenDate: (Long) -> Unit) {
    val viewModel: CalendarViewModel = viewModel(
        factory = viewModelFactory { initializer { CalendarViewModel(container.repository) } }
    )
    CalendarScreen(viewModel = viewModel, onBack = onBack, onOpenDate = onOpenDate)
}

@Composable
fun CalendarScreen(viewModel: CalendarViewModel, onBack: () -> Unit, onOpenDate: (Long) -> Unit) {
    val state by viewModel.uiState.collectAsState()

    LifecycleResumeEffect(Unit) {
        viewModel.refreshToday()
        onPauseOrDispose { }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("日历") },
                navigationIcon = { TextButton(onClick = onBack) { Text("返回") } },
                actions = { TextButton(onClick = { viewModel.backToToday() }) { Text("今天") } },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 8.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                TextButton(onClick = { viewModel.prevMonth() }) { Text("上月") }
                Text(
                    state.title,
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { viewModel.nextMonth() }) { Text("下月") }
            }
            Row(Modifier.fillMaxWidth()) {
                state.weekHeaders.forEach { header ->
                    Text(
                        header,
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            state.rows.forEach { week ->
                Row(Modifier.fillMaxWidth()) {
                    week.forEach { date ->
                        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                            if (date != null) {
                                DayCell(date = date, state = state, onOpenDate = onOpenDate)
                            } else {
                                Spacer(Modifier.height(52.dp))
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                LegendDot(MaterialTheme.colorScheme.primary, "有打卡")
                LegendDot(MaterialTheme.colorScheme.tertiary, "有备注")
            }
        }
    }
}

@Composable
private fun DayCell(date: Long, state: CalendarUiState, onOpenDate: (Long) -> Unit) {
    val mark = state.marks[date]
    val isToday = date == state.today

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clickable { onOpenDate(date) }
            .padding(vertical = 4.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(28.dp)
                .then(
                    if (isToday) {
                        Modifier.background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
                    } else {
                        Modifier
                    }
                ),
        ) {
            Text(
                LocalDate.ofEpochDay(date).dayOfMonth.toString(),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
            )
        }
        Spacer(Modifier.height(2.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            if (mark?.hasRecords == true) Dot(MaterialTheme.colorScheme.primary)
            if (mark?.hasNote == true) Dot(MaterialTheme.colorScheme.tertiary)
        }
    }
}

@Composable
private fun Dot(color: Color) {
    Box(Modifier.size(5.dp).background(color, CircleShape))
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Dot(color)
        Text(label, style = MaterialTheme.typography.bodySmall)
    }
}
