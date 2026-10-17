package com.gh.daily_trace

import android.app.Application
import com.gh.daily_trace.di.Container
import com.gh.daily_trace.reminder.ReminderNotifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class DailyTraceApp : Application() {
    lateinit var container: Container
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        container = Container(this)
        ReminderNotifier.ensureChannels(this)
        // 启动时重排一次提醒（覆盖强制停止等导致闹钟被系统清空的场景）
        appScope.launch {
            runCatching { container.reminderScheduler.rescheduleAll() }
        }
        // 自动备份到期检查（数据变化只在应用内发生，使用前补齐上一轮变更）
        appScope.launch {
            runCatching { container.autoBackup.runIfDue() }
        }
    }
}
