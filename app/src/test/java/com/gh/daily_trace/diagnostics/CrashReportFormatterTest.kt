package com.gh.daily_trace.diagnostics

import org.junit.Assert.assertTrue
import org.junit.Test

/** M10-7：崩溃日志文本格式（纯函数） */
class CrashReportFormatterTest {

    @Test
    fun formatContainsAppDeviceThreadAndStack() {
        val text = CrashReportFormatter.format(
            timestamp = 1_750_000_000_000L,
            appVersion = "1.2.3",
            versionCode = 42,
            device = "Test Device",
            androidVersion = "15",
            sdkInt = 35,
            threadName = "main",
            stackTrace = "java.lang.IllegalStateException: boom\n\tat com.example.Foo.bar(Foo.kt:1)\n",
        )

        assertTrue(text.contains("Daily Trace 崩溃日志"))
        assertTrue(text.contains("应用: 1.2.3 (42)"))
        assertTrue(text.contains("设备: Test Device"))
        assertTrue(text.contains("系统: Android 15 (SDK 35)"))
        assertTrue(text.contains("线程: main"))
        assertTrue(text.contains("java.lang.IllegalStateException: boom"))
        assertTrue(text.contains("at com.example.Foo.bar(Foo.kt:1)"))
    }

    @Test
    fun formatFallsBackForBlankVersion() {
        val text = CrashReportFormatter.format(
            timestamp = 0L,
            appVersion = "",
            versionCode = 1,
            device = "D",
            androidVersion = "14",
            sdkInt = 34,
            threadName = "t",
            stackTrace = "x",
        )
        assertTrue(text.contains("应用: - (1)"))
    }
}
