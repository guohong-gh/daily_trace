@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.gh.daily_trace.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.gh.daily_trace.data.model.CheckType
import com.gh.daily_trace.di.Container
import com.gh.daily_trace.domain.stats.GroupRate
import com.gh.daily_trace.domain.stats.PeriodStat
import com.gh.daily_trace.domain.stats.RateResult
import com.gh.daily_trace.ui.common.EmptyState
import com.gh.daily_trace.ui.common.ItemBadge
import com.gh.daily_trace.ui.common.LoadingState
import com.gh.daily_trace.ui.common.SectionTitle
import com.gh.daily_trace.ui.common.StatusPill
import com.gh.daily_trace.ui.common.adaptiveGridColumns
import com.gh.daily_trace.ui.common.formatMinutes
import com.gh.daily_trace.ui.common.formatValue
import kotlin.math.roundToInt

@Composable
fun StatsRoute(container: Container, onBack: () -> Unit) {
    val viewModel: StatsViewModel = viewModel(
        factory = viewModelFactory { initializer { StatsViewModel(container.repository) } }
    )
    StatsScreen(viewModel = viewModel, onBack = onBack)
}

@Composable
fun StatsScreen(viewModel: StatsViewModel, onBack: () -> Unit) {
    val state by viewModel.uiState.collectAsState()

    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose { }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("统计") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        if (!state.loaded) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                LoadingState(text = "正在统计…")
            }
            return@Scaffold
        }
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            val columns = adaptiveGridColumns(maxWidth.value)
            LazyVerticalGrid(
                columns = GridCells.Fixed(columns),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    TodaySummaryCard(done = state.todayDone, total = state.todayTotal)
                }

                item(span = { GridItemSpan(maxLineSpan) }) { SectionTitle("统计范围") }
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatRange.entries.forEach { r ->
                            FilterChip(
                                selected = state.range == r,
                                onClick = { viewModel.setRange(r) },
                                label = { Text(r.label) },
                            )
                        }
                    }
                }

                item(span = { GridItemSpan(maxLineSpan) }) { SectionTitle("分组统计") }
                if (state.groupStats.isEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Text(
                            "暂无可统计的分组",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Card(Modifier.fillMaxWidth()) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(14.dp),
                            ) {
                                state.groupStats.forEach { stat -> GroupStatRow(stat) }
                            }
                        }
                    }
                }

                item(span = { GridItemSpan(maxLineSpan) }) { SectionTitle("选项统计") }
                if (state.itemStats.isEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        EmptyState(
                            emoji = "📊",
                            title = "暂无使用中的选项",
                            description = "创建并启用打卡选项后，这里会显示完成率与连续天数",
                        )
                    }
                } else {
                    items(state.itemStats, key = { it.item.id }) { stat ->
                        ItemStatCard(stat)
                    }
                }

                item(span = { GridItemSpan(maxLineSpan) }) {
                    SectionTitle("趋势") {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TrendMode.entries.forEach { m ->
                                FilterChip(
                                    selected = state.trendMode == m,
                                    onClick = { viewModel.setTrendMode(m) },
                                    label = { Text(m.label) },
                                )
                            }
                        }
                    }
                }
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Text(
                                "柱高 = 所有使用中选项的合计完成率",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(12.dp))
                            TrendBars(state.trend)
                        }
                    }
                }
            }
        }
    }
}

/** 今日总览：环形进度 + 完成数量 */
@Composable
private fun TodaySummaryCard(done: Int, total: Int) {
    val ratio = if (total == 0) 0f else done * 1f / total
    val percent = (ratio * 100).roundToInt()
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(16.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "今日完成",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "$done / $total 项",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    progress = { ratio },
                    modifier = Modifier.size(64.dp),
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    trackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.15f),
                    strokeWidth = 6.dp,
                )
                Text(
                    "$percent%",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
    }
}

@Composable
private fun GroupStatRow(stat: GroupRate) {
    val ratio = (stat.rate.rate ?: 0.0).toFloat().coerceIn(0f, 1f)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(stat.color?.let { Color(it) } ?: MaterialTheme.colorScheme.outline),
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stat.name,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    formatRate(stat.rate),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(Modifier.height(2.dp))
            Text(
                groupDescription(stat),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { ratio },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp)),
                drawStopIndicator = {},
            )
        }
    }
}

private fun groupDescription(stat: GroupRate): String = buildList {
    add("${stat.itemCount} 个选项")
    if (stat.rate.expectedDays == 0) {
        add("暂无日级期望")
    } else {
        add("完成 ${stat.rate.doneDays}/${stat.rate.expectedDays} 项次")
    }
    if (stat.quotaItemCount > 0) add("含 ${stat.quotaItemCount} 个配额项")
}.joinToString(" · ")

@Composable
private fun ItemStatCard(stat: ItemStat) {
    val ratio: Float = when {
        stat.isQuota && stat.quotaTarget > 0 -> (stat.quotaDone * 1f / stat.quotaTarget).coerceIn(0f, 1f)
        else -> (stat.rate.rate ?: 0.0).toFloat().coerceIn(0f, 1f)
    }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ItemBadge(
                    icon = stat.item.icon,
                    color = stat.item.color,
                    size = 36.dp,
                    iconSize = 18.sp,
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    stat.item.name,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                if (stat.isQuota) {
                    Text(
                        "${stat.quotaDone}/${stat.quotaTarget} 次",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                } else {
                    Text(
                        formatRate(stat.rate),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            LinearProgressIndicator(
                progress = { ratio },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                drawStopIndicator = {},
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (stat.isQuota) {
                        "按月度配额统计，不计算连续"
                    } else {
                        "完成 ${stat.rate.doneDays}/${stat.rate.expectedDays} 天 · 最佳 ${stat.streak.best} 天"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                if (!stat.isQuota) {
                    StatusPill("连续 ${stat.streak.current} 天", color = MaterialTheme.colorScheme.primary)
                }
            }
            if (!stat.totals.isEmpty) {
                Spacer(Modifier.height(6.dp))
                Text(
                    totalsSummary(stat),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** 「累计」行文案（D28）：全时段累计，不随范围切换；计数型取数值合计，时长型分钟友好化；部分完成单独注记 */
private fun totalsSummary(stat: ItemStat): String {
    val totals = stat.totals
    val unit = stat.item.unit
    val base = when (stat.item.type) {
        CheckType.BOOLEAN -> "累计完成 ${totals.doneCount} 次"
        CheckType.COUNT -> "累计 ${formatValue(totals.valueSum)}${unit.ifBlank { "次" }}"
        CheckType.DURATION ->
            if (unit.isBlank() || unit == "分钟") "累计时长 ${formatMinutes(totals.valueSum)}"
            else "累计 ${formatValue(totals.valueSum)}$unit"
        CheckType.VALUE -> "累计 ${formatValue(totals.valueSum)}$unit"
    }
    return if (totals.partialCount > 0) "$base · 含部分完成 ${totals.partialCount} 次" else base
}

private fun formatRate(rate: RateResult): String =
    rate.rate?.let { "${(it * 100).roundToInt()}%" } ?: "—"

@Composable
private fun TrendBars(trend: List<PeriodStat>) {
    if (trend.isEmpty()) {
        Text(
            "暂无数据",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    val barArea = 96.dp
    val twoLineLabels = LocalDensity.current.fontScale > 1.4f
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = barArea + 28.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        trend.forEach { p ->
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val height = (barArea * p.rate.coerceIn(0.0, 1.0).toFloat()).coerceAtLeast(2.dp)
                Box(
                    Modifier
                        .width(16.dp)
                        .height(height)
                        .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                        .background(
                            if (p.expectedDays == 0) {
                                MaterialTheme.colorScheme.surfaceVariant
                            } else {
                                MaterialTheme.colorScheme.primary
                            }
                        ),
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    if (twoLineLabels) p.label.replace("/", "/\n") else p.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
