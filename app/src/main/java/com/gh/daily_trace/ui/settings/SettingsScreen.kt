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
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
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
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
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
import com.gh.daily_trace.data.model.DateFormat
import com.gh.daily_trace.data.model.DateLanguage
import com.gh.daily_trace.data.model.ThemeMode
import com.gh.daily_trace.di.Container
import com.gh.daily_trace.domain.reminder.ReminderPause
import com.gh.daily_trace.domain.time.DateDisplay
import com.gh.daily_trace.domain.time.DayClock
import com.gh.daily_trace.ui.common.SectionCard
import com.gh.daily_trace.ui.common.SwitchRow
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
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
                    dataRepair = container.dataRepair,
                    crashLogs = container.crashLogs,
                    reminderScheduler = container.reminderScheduler,
                    dailySummaryScheduler = container.dailySummaryScheduler,
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
    val crashInfo by viewModel.crashInfo.collectAsState()
    val dataHealth by viewModel.dataHealth.collectAsState()
    val checkingData by viewModel.checkingData.collectAsState()
    val message by viewModel.message.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var pendingImport by remember { mutableStateOf<Uri?>(null) }
    var pendingRestore by remember { mutableStateOf(false) }
    var pendingClearCrash by remember { mutableStateOf(false) }
    var pauseStep by remember { mutableStateOf(PauseStep.NONE) }
    var pauseDate by remember { mutableStateOf<Long?>(null) }
    var summaryTimeDialog by remember { mutableStateOf(false) }
    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    // 提醒时间与逻辑日均按 settings.timezone 解释，暂停展示也保持一致
    val pauseZone = remember(settings.timezone) {
        runCatching { ZoneId.of(settings.timezone) }.getOrDefault(ZoneId.systemDefault())
    }
    val datePreview = remember(settings.dateLanguage, settings.dateFormat) {
        DateDisplay(settings.dateLanguage, settings.dateFormat)
    }
    val previewDay = remember(settings.timezone, settings.startOfDay) {
        DayClock.from(settings).today()
    }

    LaunchedEffect(settings.pausedUntil) {
        while (true) {
            nowMillis = System.currentTimeMillis()
            delay(30_000)
        }
    }

    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    // 回到设置页时刷新备份概况（后台自动备份可能刚发生）与崩溃日志概况
    LifecycleResumeEffect(Unit) {
        viewModel.refreshBackupInfo()
        viewModel.refreshCrashInfo()
        onPauseOrDispose { }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) viewModel.exportTo(uri, context.contentResolver)
    }
    val csvExportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null) viewModel.exportCsvTo(uri, context.contentResolver)
    }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) pendingImport = uri
    }
    val crashExportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        if (uri != null) viewModel.exportCrashLogsTo(uri, context.contentResolver)
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

            SectionCard(title = "日期与语言") {
                SettingLabel("日期语言")
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    DateLanguage.entries.forEach { language ->
                        FilterChip(
                            selected = settings.dateLanguage == language,
                            onClick = { viewModel.update { it.copy(dateLanguage = language) } },
                            label = { Text(dateLanguageLabel(language)) },
                        )
                    }
                }
                SettingLabel("日期格式")
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    DateFormat.entries.forEach { format ->
                        FilterChip(
                            selected = settings.dateFormat == format,
                            onClick = { viewModel.update { it.copy(dateFormat = format) } },
                            label = { Text(dateFormatLabel(format)) },
                        )
                    }
                }
                Text(
                    "预览：${datePreview.short(previewDay)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    "仅影响日期显示，应用界面仍为中文。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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

            SectionCard(title = "提醒") {
                val pausedUntil = ReminderPause.activeUntil(settings.pausedUntil, nowMillis)
                if (pausedUntil != null) {
                    Text("已暂停提醒", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "将于 ${formatPauseEnd(pausedUntil, pauseZone)} 恢复（约 ${remainingLabel(pausedUntil, nowMillis)}）",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedButton(
                        onClick = { viewModel.resumeReminders() },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("立即恢复") }
                } else {
                    Text(
                        "暂停期间所有打卡提醒、稍后提醒与每日汇总都不会触发；恢复后按原规则重新调度。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        listOf(1L to "1 小时", 8L to "8 小时", 24L to "24 小时").forEach { (hours, label) ->
                            AssistChip(
                                onClick = {
                                    viewModel.pauseUntil(System.currentTimeMillis() + hours * 3_600_000L)
                                },
                                label = { Text(label) },
                            )
                        }
                        AssistChip(
                            onClick = {
                                pauseDate = null
                                pauseStep = PauseStep.DATE
                            },
                            label = { Text("自定义") },
                        )
                    }
                }

                Spacer(Modifier.height(4.dp))
                SwitchRow(
                    title = "每日汇总提醒",
                    subtitle = "每天固定时间汇总当天完成情况（暂停期间不发送）",
                    checked = settings.dailySummaryEnabled,
                    onCheckedChange = viewModel::setDailySummaryEnabled,
                )
                if (settings.dailySummaryEnabled) {
                    SettingLabel("汇总时间")
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        val presetTimes = listOf(20 * 60, 21 * 60, 22 * 60)
                        presetTimes.forEach { minutes ->
                            FilterChip(
                                selected = settings.dailySummaryTime == minutes,
                                onClick = { viewModel.setDailySummaryTime(minutes) },
                                label = { Text(formatSummaryTime(minutes)) },
                            )
                        }
                        val custom = settings.dailySummaryTime !in presetTimes
                        FilterChip(
                            selected = custom,
                            onClick = { summaryTimeDialog = true },
                            label = { Text(if (custom) formatSummaryTime(settings.dailySummaryTime) else "自定义") },
                        )
                    }
                    Text(
                        "按「${settings.timezone}」时区计算；系统调度可能有几分钟偏差。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
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
                OutlinedButton(
                    onClick = { csvExportLauncher.launch(defaultCsvName(pauseZone)) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("导出 CSV（打卡记录）") }
                Text(
                    "CSV 包含全部打卡记录（含补卡与历史快照），供表格分析；完整恢复请使用「导出备份」。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
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

            SectionCard(title = "诊断与修复") {
                OutlinedButton(
                    onClick = { viewModel.checkData() },
                    enabled = !checkingData,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(if (checkingData) "检查中…" else "检查数据") }
                Text(
                    "检查孤立频率规则 / 提醒、无效分组引用、空日期备注与越界设置；不会删除打卡历史。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(
                    onClick = { pendingRestore = true },
                    enabled = backupInfo.count > 0,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("从最近本机备份恢复") }
                Text(
                    "数据大面积异常时，可用最近一份本机备份覆盖恢复（自动备份或「立即备份」均可）。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(Modifier.height(4.dp))
                Text(
                    "崩溃日志 ${crashInfo.count} 份 · 最近 ${formatBackupTime(crashInfo.lastAt)}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { crashExportLauncher.launch(defaultCrashName()) },
                        enabled = crashInfo.count > 0,
                        modifier = Modifier.weight(1f),
                    ) { Text("导出崩溃日志") }
                    OutlinedButton(
                        onClick = { pendingClearCrash = true },
                        enabled = crashInfo.count > 0,
                        modifier = Modifier.weight(1f),
                    ) { Text("清除") }
                }
                Text(
                    "崩溃日志仅含版本、设备与堆栈，保存在应用私有目录，最多保留 ${com.gh.daily_trace.diagnostics.CrashLogManager.MAX_FILES} 份。",
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

    if (pauseStep == PauseStep.DATE) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = pauseDate?.times(MILLIS_PER_DAY)
        )
        DatePickerDialog(
            onDismissRequest = { pauseStep = PauseStep.NONE },
            confirmButton = {
                TextButton(
                    enabled = pickerState.selectedDateMillis != null,
                    onClick = {
                        pickerState.selectedDateMillis?.let { millis ->
                            // Material3 DatePicker 的选择值为 UTC 零点毫秒，转 epochDay 不涉及时区
                            pauseDate = Instant.ofEpochMilli(millis)
                                .atZone(ZoneOffset.UTC)
                                .toLocalDate()
                                .toEpochDay()
                            pauseStep = PauseStep.TIME
                        }
                    },
                ) { Text("下一步") }
            },
            dismissButton = {
                TextButton(onClick = { pauseStep = PauseStep.NONE }) { Text("取消") }
            },
        ) {
            DatePicker(state = pickerState)
        }
    }

    if (pauseStep == PauseStep.TIME && pauseDate != null) {
        val timeState = rememberTimePickerState(initialHour = 21, initialMinute = 0, is24Hour = true)
        val untilMillis = LocalDate.ofEpochDay(pauseDate!!)
            .atTime(timeState.hour, timeState.minute)
            .atZone(pauseZone)
            .toInstant()
            .toEpochMilli()
        AlertDialog(
            onDismissRequest = { pauseStep = PauseStep.NONE },
            title = { Text("暂停至") },
            text = {
                Column {
                    Text(formatPauseEnd(untilMillis, pauseZone), style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(12.dp))
                    TimePicker(state = timeState)
                }
            },
            confirmButton = {
                TextButton(
                    enabled = ReminderPause.isValidUntil(untilMillis, System.currentTimeMillis()),
                    onClick = {
                        viewModel.pauseUntil(untilMillis)
                        pauseStep = PauseStep.NONE
                    },
                ) { Text("暂停") }
            },
            dismissButton = {
                TextButton(onClick = { pauseStep = PauseStep.NONE }) { Text("取消") }
            },
        )
    }

    if (summaryTimeDialog) {
        val timeState = rememberTimePickerState(
            initialHour = settings.dailySummaryTime / 60,
            initialMinute = settings.dailySummaryTime % 60,
            is24Hour = true,
        )
        AlertDialog(
            onDismissRequest = { summaryTimeDialog = false },
            title = { Text("汇总时间") },
            text = {
                Column {
                    Text(
                        formatSummaryTime(timeState.hour * 60 + timeState.minute),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Spacer(Modifier.height(12.dp))
                    TimePicker(state = timeState)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.setDailySummaryTime(timeState.hour * 60 + timeState.minute)
                    summaryTimeDialog = false
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { summaryTimeDialog = false }) { Text("取消") }
            },
        )
    }

    dataHealth?.let { report ->
        AlertDialog(
            onDismissRequest = viewModel::dismissDataHealth,
            title = { Text("数据检查") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        "选项 ${report.items}（启用 ${report.activeItems}）· 记录 ${report.records} · 备注 ${report.notes}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        "分组 ${report.groups} · 规则 ${report.rules} · 提醒 ${report.reminders} · 筛选 ${report.filterViews}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        if (report.integrityOk) "数据库完整性：通过" else "数据库完整性：${report.quickCheck}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (report.integrityOk) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.error
                        },
                    )
                    Spacer(Modifier.height(8.dp))
                    var hasIssue = false
                    if (report.orphanRules > 0) {
                        hasIssue = true
                        Text("孤立频率规则 ${report.orphanRules} 条（所属选项已不存在）", style = MaterialTheme.typography.bodySmall)
                    }
                    if (report.orphanReminders > 0) {
                        hasIssue = true
                        Text("孤立提醒 ${report.orphanReminders} 条（所属选项已不存在）", style = MaterialTheme.typography.bodySmall)
                    }
                    if (report.invalidGroupRefs > 0) {
                        hasIssue = true
                        Text("无效分组引用 ${report.invalidGroupRefs} 个（将置为未分组）", style = MaterialTheme.typography.bodySmall)
                    }
                    if (report.emptyNotes > 0) {
                        hasIssue = true
                        Text("空日期备注 ${report.emptyNotes} 条（将清理）", style = MaterialTheme.typography.bodySmall)
                    }
                    if (report.staleFilterViews > 0) {
                        hasIssue = true
                        Text("筛选视图含失效引用 ${report.staleFilterViews} 个（将清理引用）", style = MaterialTheme.typography.bodySmall)
                    }
                    if (report.settingsMissing) {
                        hasIssue = true
                        Text("设置缺失（将重建默认设置）", style = MaterialTheme.typography.bodySmall)
                    } else if (report.settingsIssues > 0) {
                        hasIssue = true
                        Text("设置值越界（将修正为合法范围）", style = MaterialTheme.typography.bodySmall)
                    }
                    if (report.orphanRecords > 0) {
                        hasIssue = true
                        Text(
                            "历史记录 ${report.orphanRecords} 条（所属选项已彻底删除，保留只读展示，不删除）",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (!hasIssue) {
                        Text("未发现数据异常。", style = MaterialTheme.typography.bodySmall)
                    }
                    if (!report.integrityOk) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "数据库文件存在完整性错误，建议关闭本弹窗后从最近本机备份恢复。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            },
            confirmButton = {
                if (report.integrityOk && report.fixableIssues > 0) {
                    TextButton(onClick = { viewModel.repairData() }, enabled = !checkingData) { Text("修复") }
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissDataHealth() }) { Text("关闭") }
            },
        )
    }

    if (pendingRestore) {
        AlertDialog(
            onDismissRequest = { pendingRestore = false },
            title = { Text("从最近本机备份恢复？") },
            text = {
                Text(
                    "将用最近一份自动备份覆盖当前全部数据（选项、记录、备注、分组、提醒、设置），且不可撤销。" +
                        if (backupInfo.count > 0) "\n最近备份：${formatBackupTime(backupInfo.lastAt)}" else ""
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingRestore = false
                    viewModel.restoreLatestBackup()
                }) { Text("覆盖恢复") }
            },
            dismissButton = {
                TextButton(onClick = { pendingRestore = false }) { Text("取消") }
            },
        )
    }

    if (pendingClearCrash) {
        AlertDialog(
            onDismissRequest = { pendingClearCrash = false },
            title = { Text("清除崩溃日志？") },
            text = { Text("将删除本机保存的全部崩溃日志（${crashInfo.count} 份），不可撤销。") },
            confirmButton = {
                TextButton(onClick = {
                    pendingClearCrash = false
                    viewModel.clearCrashLogs()
                }) { Text("清除") }
            },
            dismissButton = {
                TextButton(onClick = { pendingClearCrash = false }) { Text("取消") }
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

private fun defaultCsvName(zone: ZoneId): String =
    "daily_trace_records_" + LocalDate.now(zone).format(DateTimeFormatter.BASIC_ISO_DATE) + ".csv"

private fun defaultCrashName(): String =
    "daily_trace_crash_" + SimpleDateFormat("yyyyMMdd", Locale.US).format(Date()) + ".txt"

private fun formatBackupTime(at: Long?): String =
    if (at == null) "暂无" else SimpleDateFormat("M-d HH:mm", Locale.US).format(Date(at))

private fun themeLabel(mode: ThemeMode): String = when (mode) {
    ThemeMode.SYSTEM -> "跟随系统"
    ThemeMode.LIGHT -> "浅色"
    ThemeMode.DARK -> "深色"
}

private fun dateLanguageLabel(language: DateLanguage): String = when (language) {
    DateLanguage.SYSTEM -> "跟随系统"
    DateLanguage.ZH -> "中文"
    DateLanguage.EN -> "English"
}

private fun dateFormatLabel(format: DateFormat): String = when (format) {
    DateFormat.AUTO -> "跟随语言"
    DateFormat.YMD -> "年-月-日"
    DateFormat.MDY -> "月/日/年"
    DateFormat.DMY -> "日/月/年"
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

/** 自定义暂停的两步选择：先日期，再时间 */
private enum class PauseStep { NONE, DATE, TIME }

private const val MILLIS_PER_DAY = 86_400_000L

private fun formatPauseEnd(untilMillis: Long, zone: ZoneId): String =
    Instant.ofEpochMilli(untilMillis)
        .atZone(zone)
        .format(DateTimeFormatter.ofPattern("M-d HH:mm", Locale.CHINA))

private fun formatSummaryTime(minutes: Int): String =
    String.format(Locale.US, "%02d:%02d", minutes / 60, minutes % 60)

private fun remainingLabel(untilMillis: Long, nowMillis: Long): String {
    val minutes = ((untilMillis - nowMillis) / 60_000L).coerceAtLeast(1L)
    return when {
        minutes < 60 -> "$minutes 分钟"
        minutes < 24 * 60 -> "${minutes / 60} 小时 ${minutes % 60} 分"
        else -> "${minutes / (24 * 60)} 天 ${(minutes % (24 * 60)) / 60} 小时"
    }
}
