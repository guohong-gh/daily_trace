package com.gh.daily_trace.ui.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.gh.daily_trace.data.model.CheckItem
import com.gh.daily_trace.data.model.CheckType
import com.gh.daily_trace.data.model.RecordStatus

/**
 * 记录对话框：数值（非布尔型）+ 备注 + 可选状态。
 * - allowPartial=true 时显示「完成 / 部分完成」选项，默认「完成」；
 * - planMode=true（未来日期）时固定保存为「计划中」，不显示状态选项；
 * - 布尔型不显示数值输入，onConfirm 固定收到 value=1.0。
 */
@Composable
fun RecordDialog(
    item: CheckItem,
    onDismiss: () -> Unit,
    onConfirm: (Double?, String, RecordStatus) -> Unit,
    allowPartial: Boolean = false,
    planMode: Boolean = false,
) {
    var valueText by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var partial by remember { mutableStateOf(false) }
    val isBoolean = item.type == CheckType.BOOLEAN
    val showStatus = allowPartial && !planMode

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (planMode) "计划：${item.name}" else item.name) },
        text = {
            Column {
                if (planMode) {
                    Text(
                        "未来日期：保存为「计划中」，不计入完成；当天到来后可正常打卡。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                }
                if (showStatus) {
                    Row {
                        FilterChip(
                            selected = !partial,
                            onClick = { partial = false },
                            label = { Text("完成") },
                        )
                        Spacer(Modifier.width(8.dp))
                        FilterChip(
                            selected = partial,
                            onClick = { partial = true },
                            label = { Text("部分完成") },
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                }
                if (!isBoolean) {
                    val unitLabel = item.unit.takeIf { it.isNotBlank() }?.let { "（$it）" } ?: ""
                    OutlinedTextField(
                        value = valueText,
                        onValueChange = { valueText = it },
                        label = { Text(if (planMode) "计划数值$unitLabel（可留空）" else "数值$unitLabel") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                    )
                    Spacer(Modifier.height(8.dp))
                }
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text(if (planMode) "计划备注" else "备注") },
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val value = if (isBoolean) 1.0 else valueText.trim().toDoubleOrNull()
                    val status = when {
                        planMode -> RecordStatus.PLANNED
                        partial -> RecordStatus.PARTIAL
                        else -> RecordStatus.DONE
                    }
                    onConfirm(value, note.trim(), status)
                },
                enabled = isBoolean || valueText.isNotBlank() || note.isNotBlank(),
            ) { Text(if (planMode) "保存计划" else "保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
