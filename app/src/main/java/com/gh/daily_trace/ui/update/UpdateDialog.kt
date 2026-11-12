package com.gh.daily_trace.ui.update

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import com.gh.daily_trace.update.UpdateChecker

/** 启动时发现新版本的非强制提示：稍后仅关闭，去更新跳浏览器 */
@Composable
fun UpdateDialog(
    version: String,
    currentVersion: String,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("发现新版本 $version") },
        text = {
            Text(
                "当前版本 $currentVersion。可前往 GitHub Releases 下载安装包；" +
                    "选择稍后也不会影响使用，之后可在「设置 → 版本」中手动检查。"
            )
        },
        confirmButton = {
            TextButton(onClick = {
                onDismiss()
                openReleasesPage(context)
            }) { Text("去更新") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("稍后") }
        },
    )
}

/** 打开 Releases 下载页；无可用浏览器时提示。应用内不做下载与安装。 */
fun openReleasesPage(context: Context): Boolean = try {
    context.startActivity(Intent(Intent.ACTION_VIEW, UpdateChecker.RELEASES_PAGE.toUri()))
    true
} catch (_: ActivityNotFoundException) {
    Toast.makeText(context, "未找到可用浏览器", Toast.LENGTH_SHORT).show()
    false
}
