@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.gh.daily_trace.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.gh.daily_trace.di.Container
import com.gh.daily_trace.domain.stats.PeriodStat
import com.gh.daily_trace.domain.stats.RateResult
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
                navigationIcon = { TextButton(onClick = onBack) { Text("返回") } },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                "今天已完成 ${state.todayDone} / ${state.todayTotal}",
                style = MaterialTheme.typography.titleMedium,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatRange.entries.forEach { r ->
                    FilterChip(
                        selected = state.range == r,
                        onClick = { viewModel.setRange(r) },
                        label = { Text(r.label) },
                    )
                }
            }

            Text("选项统计", style = MaterialTheme.typography.labelLarge)
            if (state.itemStats.isEmpty()) {
                Text("暂无使用中的选项", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            state.itemStats.forEach { stat -> ItemStatRow(stat) }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("趋势", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                TrendMode.entries.forEach { m ->
                    FilterChip(
                        selected = state.trendMode == m,
                        onClick = { viewModel.setTrendMode(m) },
                        label = { Text(m.label) },
                    )
                    Spacer(Modifier.width(6.dp))
                }
            }
            Text(
                "柱高 = 所有使用中选项的合计完成率（周对齐按设置）",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TrendBars(state.trend)
        }
    }
}

@Composable
private fun ItemStatRow(stat: ItemStat) {
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (stat.item.icon.isNotEmpty()) {
                Text(stat.item.icon)
                Spacer(Modifier.width(6.dp))
            }
            Text(stat.item.name, modifier = Modifier.weight(1f))
            if (stat.isQuota) {
                Text(
                    "本月 ${stat.quotaDone}/${stat.quotaTarget} 次",
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                Text(
                    formatRate(stat.rate),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        Text(
            if (stat.isQuota) {
                "按月度配额统计，不计算连续"
            } else {
                "完成 ${stat.rate.doneDays}/${stat.rate.expectedDays} 天 · 连续 ${stat.streak.current} 天 · 最佳 ${stat.streak.best} 天"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        HorizontalDivider(Modifier.padding(top = 8.dp))
    }
}

private fun formatRate(rate: RateResult): String =
    rate.rate?.let { "${(it * 100).roundToInt()}%" } ?: "—"

@Composable
private fun TrendBars(trend: List<PeriodStat>) {
    if (trend.isEmpty()) return
    val barArea = 96.dp
    Row(
        modifier = Modifier.fillMaxWidth().height(barArea + 24.dp),
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
                        .background(
                            if (p.expectedDays == 0) {
                                MaterialTheme.colorScheme.surfaceVariant
                            } else {
                                MaterialTheme.colorScheme.primary
                            }
                        ),
                )
                Text(p.label, style = MaterialTheme.typography.labelSmall, maxLines = 1)
            }
        }
    }
}
