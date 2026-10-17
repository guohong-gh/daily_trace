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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.gh.daily_trace.data.model.CheckType
import com.gh.daily_trace.data.model.MultiRecordRule
import com.gh.daily_trace.data.model.RepeatType
import com.gh.daily_trace.di.Container
import com.gh.daily_trace.reminder.ReminderPermissions
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
                navigationIcon = { TextButton(onClick = onBack) { Text("返回") } },
                actions = {
                    TextButton(
                        onClick = { viewModel.save() },
                        enabled = state.loaded && state.name.isNotBlank(),
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
            OutlinedTextField(
                value = state.name,
                onValueChange = { v -> viewModel.update { it.copy(name = v) } },
                label = { Text("名称") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = state.icon,
                    onValueChange = { v -> viewModel.update { it.copy(icon = v.take(4)) } },
                    label = { Text("图标") },
                    singleLine = true,
                    modifier = Modifier.width(120.dp),
                )
                Spacer(Modifier.width(12.dp))
                Row(
                    modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    COLOR_PALETTE.forEach { c ->
                        Box(
                            modifier = Modifier
                                .size(26.dp)
                                .clip(CircleShape)
                                .background(Color(c))
                                .clickable { viewModel.update { it.copy(color = c) } }
                                .then(
                                    if (state.color == c) {
                                        Modifier.border(2.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                    } else {
                                        Modifier
                                    }
                                ),
                        )
                    }
                }
            }

            Text("类型", style = MaterialTheme.typography.labelLarge)
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
                OutlinedTextField(
                    value = state.unit,
                    onValueChange = { v -> viewModel.update { it.copy(unit = v) } },
                    label = { Text("单位（如 次 / 分钟 / 毫升）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = state.targetText,
                    onValueChange = { v -> viewModel.update { it.copy(targetText = v) } },
                    label = { Text("目标值（可留空）") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            OutlinedTextField(
                value = state.defaultNote,
                onValueChange = { v -> viewModel.update { it.copy(defaultNote = v) } },
                label = { Text("默认备注（可留空）") },
                modifier = Modifier.fillMaxWidth(),
            )

            val groupName = state.groups.firstOrNull { it.id == state.groupId }?.name ?: "不分组"
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("分组", modifier = Modifier.weight(1f))
                TextButton(onClick = { showGroupPicker = true }) { Text("$groupName ▾") }
            }

            if (state.type != CheckType.BOOLEAN) {
                Text("同一天多次打卡", style = MaterialTheme.typography.labelLarge)
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

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("启用（出现在打卡列表）", modifier = Modifier.weight(1f))
                Switch(
                    checked = state.enabled,
                    onCheckedChange = { v -> viewModel.update { it.copy(enabled = v) } },
                )
            }

            Text("提醒", style = MaterialTheme.typography.labelLarge)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("启用提醒", modifier = Modifier.weight(1f))
                Switch(
                    checked = state.reminderEnabled,
                    onCheckedChange = { v -> viewModel.update { it.copy(reminderEnabled = v) } },
                )
            }

            if (state.reminderEnabled) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("提醒时刻", modifier = Modifier.weight(1f))
                    TextButton(onClick = { timePickerTarget = TimeField.REMIND }) {
                        Text(formatTime(state.reminderTime))
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = state.reminderRepeat == RepeatType.DAILY,
                        onClick = { viewModel.update { it.copy(reminderRepeat = RepeatType.DAILY) } },
                        label = { Text("每天") },
                    )
                    FilterChip(
                        selected = state.reminderRepeat == RepeatType.WEEKDAYS,
                        onClick = { viewModel.update { it.copy(reminderRepeat = RepeatType.WEEKDAYS) } },
                        label = { Text("工作日") },
                    )
                    FilterChip(
                        selected = state.reminderRepeat == RepeatType.WEEKLY,
                        onClick = {
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
                        },
                        label = { Text("每周") },
                    )
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

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("免打扰时段（静默提醒）", modifier = Modifier.weight(1f))
                    Switch(
                        checked = state.dndEnabled,
                        onCheckedChange = { v -> viewModel.update { it.copy(dndEnabled = v) } },
                    )
                }
                if (state.dndEnabled) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("时段", modifier = Modifier.weight(1f))
                        TextButton(onClick = { timePickerTarget = TimeField.DND_START }) {
                            Text(formatTime(state.dndStart))
                        }
                        Text("—")
                        TextButton(onClick = { timePickerTarget = TimeField.DND_END }) {
                            Text(formatTime(state.dndEnd))
                        }
                    }
                }

                if (!state.notificationsAllowed) {
                    Text("尚未允许通知权限，提醒不会显示。", color = MaterialTheme.colorScheme.error)
                    OutlinedButton(
                        onClick = { notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }
                    ) { Text("允许通知") }
                }
                if (!state.exactAlarmAllowed) {
                    Text("未允许精确闹钟，提醒可能延迟数分钟。")
                    OutlinedButton(
                        onClick = {
                            context.startActivity(ReminderPermissions.exactAlarmSettingsIntent(context))
                        }
                    ) { Text("去允许精准提醒") }
                }
            }

            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

            if (!state.isNew) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { viewModel.toggleArchived() }) {
                        Text(if (state.archived) "取消归档" else "归档")
                    }
                    TextButton(onClick = { confirmTrash = true }) {
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
    TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Text((if (selected) "✓ " else "") + name, modifier = Modifier.fillMaxWidth())
    }
}

private fun typeLabel(type: CheckType): String = when (type) {
    CheckType.BOOLEAN -> "布尔"
    CheckType.COUNT -> "计数"
    CheckType.DURATION -> "时长"
    CheckType.VALUE -> "数值"
}

private fun ruleLabel(rule: MultiRecordRule): String = when (rule) {
    MultiRecordRule.MERGE -> "合并一条"
    MultiRecordRule.ACCUMULATE -> "累加"
    MultiRecordRule.KEEP_MULTIPLE -> "保留多条"
}
