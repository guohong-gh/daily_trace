package com.gh.daily_trace.ui.today

import androidx.compose.animation.animateContentSize
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextOverflow
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
import com.gh.daily_trace.ui.common.DoneBadge
import com.gh.daily_trace.ui.common.EmptyState
import com.gh.daily_trace.ui.common.ItemBadge
import com.gh.daily_trace.ui.common.RecordDialog
import com.gh.daily_trace.ui.common.RecordStatusPill
import com.gh.daily_trace.ui.common.formatTarget
import com.gh.daily_trace.ui.common.formatValue
import com.gh.daily_trace.ui.common.timeFormatter
import kotlinx.coroutines.launch
import java.time.Instant
import kotlin.math.roundToInt

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
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LifecycleResumeEffect(Unit) {
        viewModel.refreshToday()
        onPauseOrDispose { }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("今日打卡", style = MaterialTheme.typography.titleLarge)
                        Text(
                            state.dateLabel,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onCalendar) {
                        Icon(Icons.Filled.DateRange, contentDescription = "日历")
                    }
                    IconButton(onClick = onSearch) {
                        Icon(Icons.Filled.Search, contentDescription = "搜索")
                    }
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "更多")
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            MenuEntry("📊", "统计") { menuOpen = false; onStats() }
                            MenuEntry("🗂️", "筛选视图") { menuOpen = false; onFilter() }
                            MenuEntry("🧩", "管理选项") { menuOpen = false; onManage() }
                            MenuEntry("⚙️", "设置") { menuOpen = false; onSettings() }
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        if (state.sections.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                if (state.itemsBeforeFilter == 0) {
                    EmptyState(
                        emoji = "📋",
                        title = "还没有打卡选项",
                        description = "创建第一个选项，开始记录每天的小坚持",
                        actionLabel = "创建选项",
                        onAction = onManage,
                    )
                } else {
                    EmptyState(
                        emoji = "🔍",
                        title = "筛选后没有匹配的选项",
                        description = state.filterName?.let { "当前筛选视图「$it」没有命中任何选项" },
                        actionLabel = "清除筛选",
                        onAction = onClearFilter,
                    )
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
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(start = 12.dp, end = 4.dp),
                            ) {
                                Text(
                                    "筛选视图：$name",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.weight(1f),
                                )
                                TextButton(onClick = onClearFilter) { Text("清除") }
                            }
                        }
                    }
                }
                item(key = "progress") {
                    ProgressCard(done = state.doneCount, total = state.totalCount)
                }
                state.sections.forEach { section ->
                    item(key = "section-${section.title}") {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 10.dp, bottom = 2.dp),
                        ) {
                            Text(
                                section.title,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f),
                            )
                            val done = section.rows.count { it.done }
                            Text(
                                "$done/${section.rows.size}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    items(section.rows, key = { it.item.id }) { row ->
                        ItemCard(
                            row = row,
                            onToggleBoolean = { viewModel.toggleBoolean(row.item) },
                            onQuickAdd = { viewModel.addValue(row.item, 1.0, "") },
                            onOpenRecord = { recordDialogItem = row.item },
                            onToggleSkip = { viewModel.toggleSkipToday(row.item) },
                            onDeleteRecord = { id ->
                                viewModel.deleteRecord(id)
                                scope.launch { snackbarHostState.showSnackbar("已删除该条记录") }
                            },
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
            onConfirm = { value, note, status ->
                viewModel.addValue(item, value, note, status)
                recordDialogItem = null
            },
            allowPartial = true,
        )
    }
}

@Composable
private fun MenuEntry(emoji: String, label: String, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(label) },
        leadingIcon = { Text(emoji, fontSize = 16.sp) },
        onClick = onClick,
    )
}

/** 今日进度卡片：百分比 + 进度条 + 完成数 */
@Composable
private fun ProgressCard(done: Int, total: Int) {
    val ratio = if (total == 0) 0f else done * 1f / total
    val percent = (ratio * 100).roundToInt()
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "今日进度",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "已打卡 $done / $total 项",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
                    )
                }
                Text(
                    "$percent%",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            Spacer(Modifier.height(12.dp))
            LinearProgressIndicator(
                progress = { ratio },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(CircleShape),
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                trackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.15f),
                drawStopIndicator = {},
            )
            if (total > 0 && done == total) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "今日全部完成，太棒了 🎉",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
    }
}

@Composable
private fun ItemCard(
    row: ItemRow,
    onToggleBoolean: () -> Unit,
    onQuickAdd: () -> Unit,
    onOpenRecord: () -> Unit,
    onToggleSkip: () -> Unit,
    onDeleteRecord: (Long) -> Unit,
) {
    val item = row.item
    var expanded by rememberSaveable(item.id) { mutableStateOf(false) }
    val done = row.done
    val reached = item.targetValue?.let { row.totalValue >= it } == true

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
            .clickable { expanded = !expanded },
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (item.type == CheckType.BOOLEAN && done) {
                    DoneBadge()
                } else {
                    ItemBadge(icon = item.icon, color = item.color)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        item.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val subtitle = itemSubtitle(item, row, done, reached)
                    if (subtitle.isNotBlank()) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (done || reached) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                }
                when (item.type) {
                    CheckType.BOOLEAN -> Checkbox(checked = done, onCheckedChange = { onToggleBoolean() })
                    CheckType.COUNT -> FilledTonalButton(
                        onClick = onQuickAdd,
                        contentPadding = PaddingValues(horizontal = 16.dp),
                    ) { Text("+1") }
                    else -> OutlinedButton(onClick = onOpenRecord) { Text("记一笔") }
                }
                Spacer(Modifier.width(2.dp))
                Icon(
                    imageVector = Icons.Filled.KeyboardArrowDown,
                    contentDescription = if (expanded) "收起明细" else "展开明细",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .size(20.dp)
                        .graphicsLayer { rotationZ = if (expanded) 180f else 0f },
                )
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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    TextButton(onClick = onToggleSkip, enabled = row.allowSkip) {
                        Text(if (row.skipped) "取消跳过" else "跳过今天")
                    }
                    when {
                        !row.allowSkip -> Text(
                            "已关闭「允许跳过」",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        row.skipped -> Text(
                            "不计入完成率，不中断连续",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (row.records.isEmpty()) {
                    Text(
                        "今日暂无记录",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                row.records.forEach { record ->
                    RecordLine(record = record, unit = item.unit, onDelete = { onDeleteRecord(record.id) })
                }
            }
        }
    }
}

/** 选项卡片副标题：展示今日完成量与目标对比（只看数据、不改判定） */
private fun itemSubtitle(item: CheckItem, row: ItemRow, done: Boolean, reached: Boolean): String {
    if (row.skipped) return "今日已跳过 · 不计完成率"
    val base = when {
        item.type == CheckType.BOOLEAN -> if (done) "今日已完成" else ""
        item.targetValue != null -> buildString {
            append("今日 ${formatValue(row.totalValue)}/${formatValue(item.targetValue)} ${item.unit}")
            if (reached) append(" · 已达标")
        }
        row.actualRecords.isNotEmpty() -> "今日 ${formatValue(row.totalValue)}${item.unit}"
        else -> formatTarget(item)
    }
    val planHint = row.planRecord?.takeIf { !done }?.let { planSummary(it, item.unit) }
    return when {
        planHint == null -> base
        base.isBlank() -> "计划：$planHint"
        else -> "计划：$planHint · $base"
    }
}

/** 计划摘要：「30分钟 · 备注」；无值无备注时为「已安排」 */
private fun planSummary(record: CheckRecord, unit: String): String {
    val valueText = record.value?.let { formatValue(it) + unit }
    return listOfNotNull(valueText, record.note.ifBlank { null })
        .joinToString(" · ")
        .ifBlank { "已安排" }
}

@Composable
private fun RecordLine(record: CheckRecord, unit: String, onDelete: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
    ) {
        Text(
            timeFormatter.format(Instant.ofEpochMilli(record.createdAt)),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(10.dp))
        RecordStatusPill(record.status)
        val valueText = record.value?.let { formatValue(it) + unit }
        val text = listOfNotNull(valueText, record.note.ifBlank { null }).joinToString(" · ")
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f),
        )
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
