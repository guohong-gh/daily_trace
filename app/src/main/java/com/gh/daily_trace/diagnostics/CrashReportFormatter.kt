package com.gh.daily_trace.diagnostics

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 崩溃日志文本格式（M10-7，纯函数，便于 JVM 单测）。
 *
 * 日志只包含应用版本、设备 / 系统、线程与堆栈，不含用户数据；
 * 由 [CrashLogManager] 写入应用私有目录，用户可主动导出。
 */
object CrashReportFormatter {

    fun format(
        timestamp: Long,
        appVersion: String,
        versionCode: Long,
        device: String,
        androidVersion: String,
        sdkInt: Int,
        threadName: String,
        stackTrace: String,
    ): String {
        val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS Z", Locale.US).format(Date(timestamp))
        return buildString {
            appendLine("Daily Trace 崩溃日志")
            appendLine("时间: $time")
            appendLine("应用: ${appVersion.ifBlank { "-" }} ($versionCode)")
            appendLine("设备: $device")
            appendLine("系统: Android $androidVersion (SDK $sdkInt)")
            appendLine("线程: $threadName")
            appendLine("堆栈:")
            appendLine(stackTrace.trimEnd())
        }
    }
}
