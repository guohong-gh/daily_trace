package com.gh.daily_trace.diagnostics

import android.content.Context
import android.os.Build
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 崩溃日志（M10-7）：
 * - 安装全局未捕获异常处理器，进程崩溃前把应用版本、设备、线程与完整堆栈写入
 *   `files/crash_logs/crash_*.log`；随后继续交给系统默认处理器，不吞掉系统崩溃流程。
 * - 最多保留最近 [MAX_FILES] 份，设置页可导出或清除。
 * - 只写应用私有目录，不需要存储权限。
 */
class CrashLogManager(context: Context) {

    private val appContext = context.applicationContext
    private val dir = File(appContext.filesDir, DIR_NAME)

    fun list(): List<File> =
        dir.listFiles { file -> file.isFile && file.name.startsWith(PREFIX) && file.name.endsWith(SUFFIX) }
            ?.sortedByDescending { it.name }
            ?: emptyList()

    fun latest(): File? = list().firstOrNull()

    /** 所有日志按新到旧拼接；没有日志时返回空串 */
    fun exportText(): String = list().joinToString("\n\n") { file ->
        "===== ${file.name} =====\n" + file.readText(Charsets.UTF_8)
    }

    fun clear(): Int {
        val files = list()
        var deleted = 0
        files.forEach { if (it.delete()) deleted++ }
        return deleted
    }

    /** 写入一条崩溃日志；失败时不抛异常，避免二次崩溃。 */
    fun record(
        thread: Thread,
        throwable: Throwable,
        now: Long = System.currentTimeMillis(),
    ): File? = runCatching {
        val packageInfo = appContext.packageManager.getPackageInfo(appContext.packageName, 0)
        @Suppress("DEPRECATION")
        val versionCode = packageInfo.versionCode.toLong()
        val report = CrashReportFormatter.format(
            timestamp = now,
            appVersion = packageInfo.versionName.orEmpty(),
            versionCode = versionCode,
            device = "${Build.MANUFACTURER} ${Build.MODEL}".trim(),
            androidVersion = Build.VERSION.RELEASE.orEmpty(),
            sdkInt = Build.VERSION.SDK_INT,
            threadName = thread.name,
            stackTrace = Log.getStackTraceString(throwable),
        )
        if (!dir.exists()) dir.mkdirs()
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date(now))
        var file = File(dir, "$PREFIX$stamp$SUFFIX")
        var suffix = 1
        while (file.exists()) {
            file = File(dir, "${PREFIX}${stamp}_${suffix++}$SUFFIX")
        }
        file.writeText(report, Charsets.UTF_8)
        file.setLastModified(now)
        prune()
        file
    }.getOrNull()

    private fun prune() {
        list().drop(MAX_FILES).forEach { it.delete() }
    }

    companion object {
        const val MAX_FILES = 10
        private const val DIR_NAME = "crash_logs"
        private const val PREFIX = "crash_"
        private const val SUFFIX = ".log"

        @Volatile
        private var installed = false

        /** 在 Application.onCreate 最早期调用；重复调用无副作用。 */
        fun install(context: Context) {
            if (installed) return
            synchronized(this) {
                if (installed) return
                installed = true
                val manager = CrashLogManager(context)
                val previous = Thread.getDefaultUncaughtExceptionHandler()
                val handler = Thread.UncaughtExceptionHandler { thread, throwable ->
                    manager.record(thread, throwable)
                    if (previous != null) {
                        previous.uncaughtException(thread, throwable)
                    } else {
                        android.os.Process.killProcess(android.os.Process.myPid())
                    }
                }
                Thread.setDefaultUncaughtExceptionHandler(handler)
            }
        }
    }
}
