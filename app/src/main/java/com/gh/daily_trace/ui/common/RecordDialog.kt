package com.gh.daily_trace.ui.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
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

/**
 * 记录对话框：数值（非布尔型）+ 备注。
 * 布尔型不显示数值输入，onConfirm 固定收到 value=1.0。
 */
@Composable
fun RecordDialog(item: CheckItem, onDismiss: () -> Unit, onConfirm: (Double?, String) -> Unit) {
    var valueText by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    val isBoolean = item.type == CheckType.BOOLEAN

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(item.name) },
        text = {
            Column {
                if (!isBoolean) {
                    OutlinedTextField(
                        value = valueText,
                        onValueChange = { valueText = it },
                        label = {
                            val unitLabel = item.unit.takeIf { it.isNotBlank() }?.let { "（$it）" } ?: ""
                            Text("数值$unitLabel")
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                    )
                    Spacer(Modifier.height(8.dp))
                }
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("备注") },
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val value = if (isBoolean) 1.0 else valueText.trim().toDoubleOrNull()
                    onConfirm(value, note.trim())
                },
                enabled = isBoolean || valueText.isNotBlank() || note.isNotBlank(),
            ) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
