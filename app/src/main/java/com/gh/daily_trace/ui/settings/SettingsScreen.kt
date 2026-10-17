@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.gh.daily_trace.ui.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.gh.daily_trace.data.model.ThemeMode
import com.gh.daily_trace.di.Container
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SettingsRoute(container: Container, onBack: () -> Unit) {
    val viewModel: SettingsViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                SettingsViewModel(
                    repository = container.repository,
                    backupManager = container.backupManager,
                    autoBackup = container.autoBackup,
                    reminderScheduler = container.reminderScheduler,
                )
            }
        }
    )
    SettingsScreen(viewModel = viewModel, onBack = onBack)
}

@Composable
fun SettingsScreen(viewModel: SettingsViewModel, onBack: () -> Unit) {
    val settings by viewModel.settings.collectAsState()
    val backupInfo by viewModel.backupInfo.collectAsState()
    val message by viewModel.message.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var pendingImport by remember { mutableStateOf<Uri?>(null) }

    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    // 回到设置页时刷新备份概况（后台自动备份可能刚发生）
    LifecycleResumeEffect(Unit) {
        viewModel.refreshBackupInfo()
        onPauseOrDispose { }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) viewModel.exportTo(uri, context.contentResolver)
    }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) pendingImport = uri
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置") },
                navigationIcon = { TextButton(onClick = onBack) { Text("返回") } },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("主题", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ThemeMode.entries.forEach { mode ->
                    FilterChip(
                        selected = settings.theme == mode,
                        onClick = { viewModel.update { it.copy(theme = mode) } },
                        label = { Text(themeLabel(mode)) },
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("动态取色（Android 12+）", modifier = Modifier.weight(1f))
                Switch(
                    checked = settings.dynamicColor,
                    onCheckedChange = { v -> viewModel.update { it.copy(dynamicColor = v) } },
                )
            }

            Text("周起始", style = MaterialTheme.typography.labelLarge)
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                (1..7).forEach { d ->
                    FilterChip(
                        selected = settings.weekStart == d,
                        onClick = { viewModel.update { it.copy(weekStart = d) } },
                        label = { Text(weekdayLabel(d)) },
                    )
                }
            }

            Text("新一天开始于", style = MaterialTheme.typography.labelLarge)
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                (0..6).forEach { h ->
                    FilterChip(
                        selected = settings.startOfDay == h,
                        onClick = { viewModel.update { it.copy(startOfDay = h) } },
                        label = { Text("${h}点") },
                    )
                }
            }

            Text("补卡上限", style = MaterialTheme.typography.labelLarge)
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(0, 1, 3, 7, 14, 30).forEach { days ->
                    FilterChip(
                        selected = settings.maxBackfillDays == days,
                        onClick = { viewModel.update { it.copy(maxBackfillDays = days) } },
                        label = { Text(if (days == 0) "禁用" else "${days}天") },
                    )
                }
            }

            Spacer(Modifier.padding(top = 4.dp))
            Text("备份", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { exportLauncher.launch(defaultBackupName()) }) {
                    Text("导出备份")
                }
                OutlinedButton(onClick = { importLauncher.launch(arrayOf("application/json")) }) {
                    Text("导入备份")
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("自动备份到本机", modifier = Modifier.weight(1f))
                Switch(
                    checked = settings.autoBackup,
                    onCheckedChange = { v -> viewModel.update { it.copy(autoBackup = v) } },
                )
            }
            if (settings.autoBackup) {
                Text("备份间隔", style = MaterialTheme.typography.bodySmall)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    listOf(1, 3, 7, 15, 30).forEach { days ->
                        FilterChip(
                            selected = settings.autoBackupIntervalDays == days,
                            onClick = { viewModel.update { it.copy(autoBackupIntervalDays = days) } },
                            label = { Text("${days}天") },
                        )
                    }
                }
            }

            OutlinedButton(onClick = { viewModel.backupNow() }) {
                Text("立即备份到本机")
            }
            Text(
                "本机备份 ${backupInfo.count} 份 · 最近 ${formatBackupTime(backupInfo.lastAt)}（保留最近 5 份）",
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                "导出会把全部数据写入所选文件；导入将覆盖当前全部数据（不可撤销）。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.padding(top = 4.dp))
            Text(
                "时区：${settings.timezone}（M1 暂不支持修改）",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }

    pendingImport?.let { uri ->
        AlertDialog(
            onDismissRequest = { pendingImport = null },
            title = { Text("导入备份？") },
            text = { Text("将用备份文件覆盖当前全部数据（选项、记录、备注、分组、提醒、设置），且不可撤销。") },
            confirmButton = {
                TextButton(onClick = {
                    pendingImport = null
                    viewModel.importFrom(uri, context.contentResolver)
                }) { Text("覆盖导入") }
            },
            dismissButton = {
                TextButton(onClick = { pendingImport = null }) { Text("取消") }
            },
        )
    }
}

private fun defaultBackupName(): String =
    "daily_trace_backup_" + SimpleDateFormat("yyyyMMdd", Locale.US).format(Date()) + ".json"

private fun formatBackupTime(at: Long?): String =
    if (at == null) "暂无" else SimpleDateFormat("M-d HH:mm", Locale.US).format(Date(at))

private fun themeLabel(mode: ThemeMode): String = when (mode) {
    ThemeMode.SYSTEM -> "跟随系统"
    ThemeMode.LIGHT -> "浅色"
    ThemeMode.DARK -> "深色"
}

private fun weekdayLabel(d: Int): String = when (d) {
    1 -> "周一"
    2 -> "周二"
    3 -> "周三"
    4 -> "周四"
    5 -> "周五"
    6 -> "周六"
    else -> "周日"
}
