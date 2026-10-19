@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class,
)

package com.gh.daily_trace.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.gh.daily_trace.data.model.CheckType
import com.gh.daily_trace.data.model.RecordStatus
import com.gh.daily_trace.di.Container
import com.gh.daily_trace.domain.calendar.DayMarks
import com.gh.daily_trace.domain.calendar.DaySummary
import com.gh.daily_trace.domain.calendar.SummaryEntry
import com.gh.daily_trace.ui.common.ItemBadge
import com.gh.daily_trace.ui.common.RecordStatusPill
import com.gh.daily_trace.ui.common.StatusPill
import com.gh.daily_trace.ui.common.formatValue
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun CalendarRoute(
    container: Container,
    appliedFilterId: Long,
    onBack: () -> Unit,
    onOpenDate: (Long) -> Unit,
) {
    val viewModel: CalendarViewModel = viewModel(
        factory = viewModelFactory { initializer { CalendarViewModel(container.repository) } }
    )
    LaunchedEffect(appliedFilterId) {
        viewModel.setFilter(appliedFilterId)
    }
    CalendarScreen(viewModel = viewModel, onBack = onBack, onOpenDate = onOpenDate)
}

@Composable
fun CalendarScreen(viewModel: CalendarViewModel, onBack: () -> Unit, onOpenDate: (Long) -> Unit) {
    val state by viewModel.uiState.collectAsState()
    var summaryDate by remember { mutableStateOf<Long?>(null) }

    LifecycleResumeEffect(Unit) {
        viewModel.refreshToday()
        onPauseOrDispose { }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("日历") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    TextButton(onClick = { viewModel.backToToday() }) { Text("今天") }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
        ) {
            state.filterName?.let { name ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    StatusPill("筛选：$name", color = MaterialTheme.colorScheme.primary)
                    Text(
                        "标记已按筛选联动",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(8.dp))
            }
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        IconButton(onClick = { viewModel.prevMonth() }) {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "上个月")
                        }
                        Text(
                            state.title,
                            style = MaterialTheme.typography.titleMedium,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = { viewModel.nextMonth() }) {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "下个月")
                        }
                    }
                    Row(Modifier.fillMaxWidth()) {
                        state.weekHeaders.forEach { header ->
                            Text(
                                header,
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                                        DayCell(
                                            date = date,
                                            mark = state.marks[date],
                                            isToday = date == state.today,
                                            hasSummary = state.summaries.containsKey(date),
                                            onOpenDate = { onOpenDate(date) },
                                            onLongPress = { summaryDate = date },
                                        )
                                    } else {
                                        Spacer(Modifier.height(48.dp))
                                    }
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    Dot(Color(0xFF2E6B4A), 4.dp)
                    Dot(Color(0xFF1565C0), 4.dp)
                    Dot(Color(0xFFEF6C00), 4.dp)
                    Spacer(Modifier.width(3.dp))
                    Text(
                        "已完成的选项（颜色对应选项）",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Dot(MaterialTheme.colorScheme.tertiary, 5.dp)
                    Text(
                        "有备注",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "长按日期查看当日摘要",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    val summary = summaryDate?.let { state.summaries[it] }
    val date = summaryDate
    if (date != null && summary != null) {
        DaySummaryDialog(
            date = date,
            summary = summary,
            onOpenDetail = {
                summaryDate = null
                onOpenDate(date)
            },
            onDismiss = { summaryDate = null },
        )
    }
}

@Composable
private fun DayCell(
    date: Long,
    mark: DayMarks?,
    isToday: Boolean,
    hasSummary: Boolean,
    onOpenDate: () -> Unit,
    onLongPress: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(MaterialTheme.shapes.small)
            .combinedClickable(
                onLongClick = if (hasSummary) onLongPress else null,
                onClick = onOpenDate,
            )
            .padding(vertical = 4.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(28.dp)
                .then(
                    if (isToday) {
                        Modifier.background(MaterialTheme.colorScheme.primary, CircleShape)
                    } else {
                        Modifier
                    }
                ),
        ) {
            Text(
                LocalDate.ofEpochDay(date).dayOfMonth.toString(),
                style = MaterialTheme.typography.bodyMedium,
                color = if (isToday) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
            )
        }
        Spacer(Modifier.height(2.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            mark?.dotColors?.forEach { color -> Dot(Color(color), 5.dp) }
            if ((mark?.overflow ?: 0) > 0) {
                Text(
                    "+${mark!!.overflow}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (mark?.hasNote == true) {
                Dot(MaterialTheme.colorScheme.tertiary, 5.dp)
            }
        }
    }
}

@Composable
private fun Dot(color: Color, size: Dp) {
    Box(Modifier.size(size).background(color, CircleShape))
}

/** 长按摘要对话框：记录条目 + 休息日 + 日期备注；「查看详情」进入日期详情页 */
@Composable
private fun DaySummaryDialog(
    date: Long,
    summary: DaySummary,
    onOpenDetail: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(dayLabel(date)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                summary.entries.forEach { entry -> SummaryEntryRow(entry) }
                if (summary.skipNames.isNotEmpty()) {
                    Text(
                        "休息日：${summary.skipNames.joinToString("、")}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
                summary.noteText?.let { note ->
                    Text(
                        "备注：$note",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onOpenDetail) { Text("查看详情") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
    )
}

@Composable
private fun SummaryEntryRow(entry: SummaryEntry) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        ItemBadge(icon = entry.icon, color = entry.color, size = 28.dp, iconSize = 14.sp)
        Spacer(Modifier.width(8.dp))
        Text(
            entry.name,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        RecordStatusPill(entry.status)
        val valueText = entryValueText(entry)
        when {
            valueText != null -> Text(
                valueText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            entry.status == RecordStatus.DONE &&
                entry.type == CheckType.BOOLEAN -> Icon(
                Icons.Filled.Check,
                contentDescription = "已完成",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

/** 布尔型只表达完成与否，不展示数值 */
private fun entryValueText(entry: SummaryEntry): String? =
    if (entry.type == CheckType.BOOLEAN) null else entry.value?.let { "${formatValue(it)}${entry.unit}" }

private fun dayLabel(date: Long): String =
    LocalDate.ofEpochDay(date).format(DateTimeFormatter.ofPattern("M月d日 EEE", Locale.CHINA))
