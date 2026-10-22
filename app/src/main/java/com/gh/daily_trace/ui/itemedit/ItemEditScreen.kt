@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.gh.daily_trace.ui.itemedit

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTimePickerState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.gh.daily_trace.data.model.CheckType
import com.gh.daily_trace.data.model.FrequencyType
import com.gh.daily_trace.data.model.MultiRecordRule
import com.gh.daily_trace.data.model.RepeatType
import com.gh.daily_trace.di.Container
import com.gh.daily_trace.reminder.ReminderPermissions
import com.gh.daily_trace.ui.common.SectionCard
import com.gh.daily_trace.ui.common.SwitchRow
import java.time.LocalDate

private val COLOR_PALETTE = listOf(
    0xFF2E7D32, 0xFF1565C0, 0xFF6A1B9A, 0xFFEF6C00, 0xFFC62828,
    0xFF00838F, 0xFFAD1457, 0xFF4E342E, 0xFF455A64, 0xFF827717,
).map { it.toInt() }

@Composable
fun ItemEditRoute(container: Container, itemId: Long, onBack: () -> Unit) {
    val viewModel: ItemEditViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                ItemEditViewModel(container.repository, container.reminderScheduler, itemId)
            }
        }
    )
    ItemEditScreen(viewModel = viewModel, onBack = onBack)
}

@Composable
fun ItemEditScreen(viewModel: ItemEditViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    var showGroupPicker by remember { mutableStateOf(false) }
    var confirmTrash by remember { mutableStateOf(false) }
    var timePickerTarget by remember { mutableStateOf<TimeField?>(null) }
    val canSave = state.loaded && state.name.isNotBlank()

    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        viewModel.setPermissions(
            ReminderPermissions.notificationsGranted(context),
            ReminderPermissions.canScheduleExactAlarms(context),
        )
    }

    // 回前台时刷新权限状态（可能刚在系统设置中调整过）
    LifecycleResumeEffect(Unit) {
        viewModel.setPermissions(
            ReminderPermissions.notificationsGranted(context),
            ReminderPermissions.canScheduleExactAlarms(context),
        )
        onPauseOrDispose { }
    }

    LaunchedEffect(state.done) {
        if (state.done) onBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (state.isNew) "新建选项" else "编辑选项") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    TextButton(
                        onClick = { viewModel.save() },
                        enabled = canSave,
                    ) { Text("保存") }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionCard(title = "基本信息") {
                OutlinedTextField(
                    value = state.name,
                    onValueChange = { v -> viewModel.update { it.copy(name = v) } },
                    label = { Text("名称") },
                    placeholder = { Text("例如：喝水、跑步、背单词") },
                    isError = state.loaded && state.name.isBlank(),
                    supportingText = {
                        if (state.loaded && state.name.isBlank()) {
                            Text("名称不能为空")
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = state.icon,
                        onValueChange = { v -> viewModel.update { it.copy(icon = v.take(4)) } },
                        label = { Text("图标") },
                        supportingText = { Text("emoji") },
                        singleLine = true,
                        modifier = Modifier.width(112.dp),
                    )
                    Spacer(Modifier.width(12.dp))
                    Row(
                        modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        COLOR_PALETTE.forEach { c ->
                            ColorSwatch(color = c, selected = state.color == c) {
                                viewModel.update { it.copy(color = c) }
                            }
                        }
                    }
                }
            }

            SectionCard(title = "记录方式") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CheckType.entries.forEach { t ->
                        FilterChip(
                            selected = state.type == t,
                            onClick = { viewModel.update { it.copy(type = t) } },
                            label = { Text(typeLabel(t)) },
                        )
                    }
                }

                if (state.type != CheckType.BOOLEAN) {
                    val targetInvalid = state.targetText.isNotBlank() &&
                        state.targetText.trim().toDoubleOrNull() == null
                    OutlinedTextField(
                        value = state.unit,
                        onValueChange = { v -> viewModel.update { it.copy(unit = v) } },
                        label = { Text("单位") },
                        placeholder = { Text("如 次 / 分钟 / 毫升") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = state.targetText,
                        onValueChange = { v -> viewModel.update { it.copy(targetText = v) } },
                        label = { Text("目标值（可留空）") },
                        isError = targetInvalid,
                        supportingText = {
                            Text(if (targetInvalid) "请输入数字，如 30 或 2.5" else "留空表示只记录、不设目标")
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        "同一天多次打卡",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MultiRecordRule.entries.forEach { r ->
                            FilterChip(
                                selected = state.rule == r,
                                onClick = { viewModel.update { it.copy(rule = r) } },
                                label = { Text(ruleLabel(r)) },
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = state.defaultNote,
                    onValueChange = { v -> viewModel.update { it.copy(defaultNote = v) } },
                    label = { Text("默认备注（可留空）") },
                    placeholder = { Text("每次打卡自动带上，例如：晨跑") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            SectionCard(title = "频率规则") {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FrequencyType.entries.forEach { t ->
                        FilterChip(
                            selected = state.freqType == t,
                            onClick = {
                                viewModel.update { s ->
                                    s.copy(
                                        freqType = t,
                                        freqDays = if (t == FrequencyType.DAYS_OF_WEEK && s.freqDays.isEmpty()) {
                                            listOf(LocalDate.now().dayOfWeek.value)
                                        } else {
                                            s.freqDays
                                        },
                                        freqTimesPerMonth = if (t == FrequencyType.TIMES_PER_MONTH && s.freqTimesPerMonth.isBlank()) {
                                            "3"
                                        } else {
                                            s.freqTimesPerMonth
                                        },
                                        freqTimesPerWeek = if (t == FrequencyType.TIMES_PER_WEEK && s.freqTimesPerWeek.isBlank()) {
                                            "3"
                                        } else {
                                            s.freqTimesPerWeek
                                        },
                                        freqIntervalDays = if (t == FrequencyType.CUSTOM && s.freqIntervalDays.isBlank()) {
                                            "2"
                                        } else {
                                            s.freqIntervalDays
                                        },
                                    )
                                }
                            },
                            label = { Text(freqTypeLabel(t)) },
                        )
                    }
                }

                when (state.freqType) {
                    FrequencyType.DAILY -> Text(
                        "每天记一次；未完成的日子计入完成率。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    FrequencyType.DAYS_OF_WEEK -> Column {
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            WEEKDAY_LABELS.forEachIndexed { index, label ->
                                val day = index + 1
                                FilterChip(
                                    selected = day in state.freqDays,
                                    onClick = {
                                        viewModel.update { s ->
                                            s.copy(
                                                freqDays = if (day in s.freqDays) {
                                                    s.freqDays - day
                                                } else {
                                                    (s.freqDays + day).sorted()
                                                },
                                            )
                                        }
                                    },
                                    label = { Text(label) },
                                )
                            }
                        }
                        Text(
                            "只在这些星期几计入完成率。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    FrequencyType.TIMES_PER_MONTH -> {
                        val invalid = state.freqTimesPerMonth.trim().toIntOrNull() !in 1..31
                        OutlinedTextField(
                            value = state.freqTimesPerMonth,
                            onValueChange = { v ->
                                viewModel.update { it.copy(freqTimesPerMonth = v.filter { c -> c.isDigit() }.take(2)) }
                            },
                            label = { Text("每月目标次数") },
                            isError = invalid,
                            supportingText = {
                                Text(if (invalid) "请输入 1-31 的整数" else "每月完成 N 天即达标，不按日计完成率")
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    FrequencyType.TIMES_PER_WEEK -> {
                        val invalid = state.freqTimesPerWeek.trim().toIntOrNull() !in 1..7
                        OutlinedTextField(
                            value = state.freqTimesPerWeek,
                            onValueChange = { v ->
                                viewModel.update { it.copy(freqTimesPerWeek = v.filter { c -> c.isDigit() }.take(1)) }
                            },
                            label = { Text("每周目标次数") },
                            isError = invalid,
                            supportingText = {
                                Text(if (invalid) "请输入 1-7 的整数" else "每周完成 N 天即达标，不按日计完成率")
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    FrequencyType.CUSTOM -> {
                        val invalid = state.freqIntervalDays.trim().toIntOrNull() !in 1..90
                        OutlinedTextField(
                            value = state.freqIntervalDays,
                            onValueChange = { v ->
                                viewModel.update { it.copy(freqIntervalDays = v.filter { c -> c.isDigit() }.take(2)) }
                            },
                            label = { Text("间隔天数 N") },
                            isError = invalid,
                            supportingText = {
                                Text(if (invalid) "请输入 1-90 的整数" else "从今天起每隔 N 天记一次；修改 N 后从今天重新起算")
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }

                SwitchRow(
                    title = "允许跳过/请假",
                    subtitle = "标记为跳过的日子不计入完成率，也不中断连续打卡",
                    checked = state.allowSkip,
                    onCheckedChange = { v -> viewModel.update { it.copy(allowSkip = v) } },
                )
            }

            SectionCard(title = "分组与状态") {
                val groupName = state.groups.firstOrNull { it.id == state.groupId }?.name ?: "不分组"
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text("分组", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    TextButton(onClick = { showGroupPicker = true }) {
                        Text("$groupName ▾")
                    }
                }
                SwitchRow(
                    title = "启用",
                    subtitle = "出现在今日打卡列表",
                    checked = state.enabled,
                    onCheckedChange = { v -> viewModel.update { it.copy(enabled = v) } },
                )
            }

            SectionCard(title = "提醒") {
                SwitchRow(
                    title = "启用提醒",
                    checked = state.reminderEnabled,
                    onCheckedChange = { v -> viewModel.update { it.copy(reminderEnabled = v) } },
                )

                if (state.reminderEnabled) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text("提醒时刻", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        TextButton(onClick = { timePickerTarget = TimeField.REMIND }) {
                            Text(
                                formatTime(state.reminderTime),
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        RepeatChip(
                            selected = state.reminderRepeat == RepeatType.DAILY,
                            label = "每天",
                        ) { viewModel.update { it.copy(reminderRepeat = RepeatType.DAILY) } }
                        RepeatChip(
                            selected = state.reminderRepeat == RepeatType.WEEKDAYS,
                            label = "工作日",
                        ) { viewModel.update { it.copy(reminderRepeat = RepeatType.WEEKDAYS) } }
                        RepeatChip(
                            selected = state.reminderRepeat == RepeatType.WEEKLY,
                            label = "每周",
                        ) {
                            viewModel.update {
                                it.copy(
                                    reminderRepeat = RepeatType.WEEKLY,
                                    reminderDays = if (it.reminderDays.isEmpty()) {
                                        listOf(LocalDate.now().dayOfWeek.value)
                                    } else {
                                        it.reminderDays
                                    },
                                )
                            }
                        }
                    }

                    if (state.reminderRepeat == RepeatType.WEEKLY) {
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            WEEKDAY_LABELS.forEachIndexed { index, label ->
                                val day = index + 1
                                FilterChip(
                                    selected = day in state.reminderDays,
                                    onClick = {
                                        viewModel.update {
                                            it.copy(
                                                reminderDays = if (day in it.reminderDays) {
                                                    it.reminderDays - day
                                                } else {
                                                    it.reminderDays + day
                                                },
                                            )
                                        }
                                    },
                                    label = { Text(label) },
                                )
                            }
                        }
                    }

                    SwitchRow(
                        title = "免打扰时段",
                        subtitle = "此时段内静默提醒，不响铃振动",
                        checked = state.dndEnabled,
                        onCheckedChange = { v -> viewModel.update { it.copy(dndEnabled = v) } },
                    )
                    if (state.dndEnabled) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Text("时段", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                            TextButton(onClick = { timePickerTarget = TimeField.DND_START }) {
                                Text(formatTime(state.dndStart))
                            }
                            Text("—", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            TextButton(onClick = { timePickerTarget = TimeField.DND_END }) {
                                Text(formatTime(state.dndEnd))
                            }
                        }
                    }

                    if (!state.notificationsAllowed) {
                        PermissionNotice(
                            text = "尚未允许通知权限，提醒不会显示。",
                            actionLabel = "允许通知",
                            onAction = {
                                notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            },
                        )
                    }
                    if (!state.exactAlarmAllowed) {
                        PermissionNotice(
                            text = "未允许精确闹钟，提醒可能延迟数分钟。",
                            actionLabel = "去允许精准提醒",
                            onAction = {
                                context.startActivity(ReminderPermissions.exactAlarmSettingsIntent(context))
                            },
                        )
                    }
                }
            }

            state.error?.let { error ->
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        "⚠️ $error",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(12.dp),
                    )
                }
            }

            Button(
                onClick = { viewModel.save() },
                enabled = canSave,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (state.isNew) "创建选项" else "保存修改") }

            if (!state.isNew) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    OutlinedButton(
                        onClick = { viewModel.toggleArchived() },
                        modifier = Modifier.weight(1f),
                    ) { Text(if (state.archived) "取消归档" else "归档") }
                    Spacer(Modifier.width(8.dp))
                    TextButton(
                        onClick = { confirmTrash = true },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("移入回收站", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }

    if (showGroupPicker) {
        AlertDialog(
            onDismissRequest = { showGroupPicker = false },
            title = { Text("选择分组") },
            text = {
                Column {
                    GroupOption("不分组", selected = state.groupId == null) {
                        viewModel.update { it.copy(groupId = null) }
                        showGroupPicker = false
                    }
                    state.groups.forEach { g ->
                        GroupOption(g.name, selected = state.groupId == g.id) {
                            viewModel.update { it.copy(groupId = g.id) }
                            showGroupPicker = false
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showGroupPicker = false }) { Text("取消") } },
        )
    }

    if (confirmTrash) {
        AlertDialog(
            onDismissRequest = { confirmTrash = false },
            title = { Text("移入回收站") },
            text = { Text("「${state.name}」将移入回收站，历史打卡记录保留，可随时恢复。") },
            confirmButton = {
                TextButton(onClick = { confirmTrash = false; viewModel.moveToTrash() }) {
                    Text("移入", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmTrash = false }) { Text("取消") } },
        )
    }

    timePickerTarget?.let { target ->
        ReminderTimePickerDialog(
            initialMinutes = when (target) {
                TimeField.REMIND -> state.reminderTime
                TimeField.DND_START -> state.dndStart
                TimeField.DND_END -> state.dndEnd
            },
            onConfirm = { minutes ->
                viewModel.update { s ->
                    when (target) {
                        TimeField.REMIND -> s.copy(reminderTime = minutes)
                        TimeField.DND_START -> s.copy(dndStart = minutes)
                        TimeField.DND_END -> s.copy(dndEnd = minutes)
                    }
                }
                timePickerTarget = null
            },
            onDismiss = { timePickerTarget = null },
        )
    }
}

private enum class TimeField { REMIND, DND_START, DND_END }

private val WEEKDAY_LABELS = listOf("一", "二", "三", "四", "五", "六", "日")

private fun formatTime(minutes: Int): String =
    "%02d:%02d".format(minutes.coerceIn(0, 1439) / 60, minutes.coerceIn(0, 1439) % 60)

@Composable
private fun ColorSwatch(color: Int, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(Color(color))
                .then(
                    if (selected) {
                        Modifier.border(2.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                    } else {
                        Modifier
                    }
                ),
        )
        if (selected) {
            Icon(
                Icons.Filled.Check,
                contentDescription = "已选颜色",
                tint = Color.White,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

@Composable
private fun RepeatChip(selected: Boolean, label: String, onClick: () -> Unit) {
    FilterChip(selected = selected, onClick = onClick, label = { Text(label) })
}

@Composable
private fun PermissionNotice(text: String, actionLabel: String, onAction: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(
                "⚠️ $text",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            Spacer(Modifier.size(8.dp))
            OutlinedButton(onClick = onAction) { Text(actionLabel) }
        }
    }
}

@Composable
private fun ReminderTimePickerDialog(
    initialMinutes: Int,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val timeState = rememberTimePickerState(
        initialHour = initialMinutes.coerceIn(0, 1439) / 60,
        initialMinute = initialMinutes.coerceIn(0, 1439) % 60,
        is24Hour = true,
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择时间") },
        text = { TimePicker(state = timeState) },
        confirmButton = {
            TextButton(onClick = { onConfirm(timeState.hour * 60 + timeState.minute) }) { Text("确定") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun GroupOption(name: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 12.dp),
    ) {
        Text(
            name,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        if (selected) {
            Icon(
                Icons.Filled.Check,
                contentDescription = "已选中",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

private fun typeLabel(type: CheckType): String = when (type) {
    CheckType.BOOLEAN -> "打卡"
    CheckType.COUNT -> "计数"
    CheckType.DURATION -> "时长"
    CheckType.VALUE -> "数值"
}

private fun ruleLabel(rule: MultiRecordRule): String = when (rule) {
    MultiRecordRule.MERGE -> "合并一条"
    MultiRecordRule.ACCUMULATE -> "累加"
    MultiRecordRule.KEEP_MULTIPLE -> "保留多条"
}

private fun freqTypeLabel(type: FrequencyType): String = when (type) {
    FrequencyType.DAILY -> "每天"
    FrequencyType.DAYS_OF_WEEK -> "每周几"
    FrequencyType.TIMES_PER_MONTH -> "每月几次"
    FrequencyType.TIMES_PER_WEEK -> "每周几次"
    FrequencyType.CUSTOM -> "自定义"
}
