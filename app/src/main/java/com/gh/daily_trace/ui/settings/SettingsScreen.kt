@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.gh.daily_trace.ui.settings

import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import com.gh.daily_trace.ui.common.SectionCard
import com.gh.daily_trace.ui.common.SwitchRow
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

    val versionName = remember {
        @Suppress("DEPRECATION")
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "-"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
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
            SectionCard(title = "外观") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemeMode.entries.forEach { mode ->
                        FilterChip(
                            selected = settings.theme == mode,
                            onClick = { viewModel.update { it.copy(theme = mode) } },
                            label = { Text(themeLabel(mode)) },
                        )
                    }
                }
                val dynamicSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                SwitchRow(
                    title = "动态取色",
                    subtitle = if (dynamicSupported) {
                        "跟随系统壁纸配色"
                    } else {
                        "需要 Android 12 及以上系统"
                    },
                    checked = settings.dynamicColor && dynamicSupported,
                    enabled = dynamicSupported,
                    onCheckedChange = { v -> viewModel.update { it.copy(dynamicColor = v) } },
                )
            }

            SectionCard(title = "使用习惯") {
                SettingLabel("每周从哪天开始")
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

                SettingLabel("新一天从几点开始")
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

                SettingLabel("补卡上限")
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
            }

            SectionCard(title = "备份") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { exportLauncher.launch(defaultBackupName()) },
                        modifier = Modifier.weight(1f),
                    ) { Text("导出备份") }
                    OutlinedButton(
                        onClick = { importLauncher.launch(arrayOf("application/json")) },
                        modifier = Modifier.weight(1f),
                    ) { Text("导入备份") }
                }
                SwitchRow(
                    title = "自动备份到本机",
                    subtitle = "最多保留最近 5 份",
                    checked = settings.autoBackup,
                    onCheckedChange = { v -> viewModel.update { it.copy(autoBackup = v) } },
                )
                if (settings.autoBackup) {
                    SettingLabel("备份间隔")
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
                OutlinedButton(
                    onClick = { viewModel.backupNow() },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(6.dp))
                    Text("立即备份到本机")
                }
                Text(
                    "本机备份 ${backupInfo.count} 份 · 最近 ${formatBackupTime(backupInfo.lastAt)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "导出会把全部数据写入所选文件；导入将覆盖当前全部数据，且不可撤销。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            SectionCard(title = "关于") {
                InfoRow("版本", versionName)
                InfoRow("时区", "${settings.timezone}（暂不支持修改）")
                Text(
                    "所有数据仅保存在本机。建议定期导出备份文件，用于迁移或防止误删。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
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

@Composable
private fun SettingLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(value, style = MaterialTheme.typography.bodyMedium)
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
