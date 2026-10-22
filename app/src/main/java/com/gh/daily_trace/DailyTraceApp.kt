package com.gh.daily_trace

import android.app.Application
import com.gh.daily_trace.diagnostics.CrashLogManager
import com.gh.daily_trace.di.Container
import com.gh.daily_trace.reminder.ReminderNotifier
import com.gh.daily_trace.widget.TodayWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch

@OptIn(FlowPreview::class)
class DailyTraceApp : Application() {
    lateinit var container: Container
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        // 尽早安装崩溃日志处理器，后续初始化异常也能留下现场。
        CrashLogManager.install(this)
        container = Container(this)
        ReminderNotifier.ensureChannels(this)
        // 启动时重排一次提醒（覆盖强制停止等导致闹钟被系统清空的场景）
        appScope.launch {
            runCatching { container.reminderScheduler.rescheduleAll() }
        }
        // 每日汇总任务随设置同步（覆盖强制停止、应用升级后任务丢失的场景）
        appScope.launch {
            runCatching { container.dailySummaryScheduler.sync(container.repository.getSettings()) }
        }
        // 自动备份到期检查（数据变化只在应用内发生，使用前补齐上一轮变更）
        appScope.launch {
            runCatching { container.autoBackup.runIfDue() }
        }
        // 应用内数据变化时刷新桌面组件（进程不在时由系统 30 分钟周期兜底）
        appScope.launch {
            combine(
                container.repository.activeItems,
                container.repository.allRecords,
                container.repository.settings,
            ) { _, _, _ -> Unit }
                .debounce(300)
                .collect { TodayWidget.requestUpdate(this@DailyTraceApp) }
        }
    }
}
