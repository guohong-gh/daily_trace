package com.gh.daily_trace.reminder

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.gh.daily_trace.DailyTraceApp
import com.gh.daily_trace.domain.reminder.ReminderPause
import com.gh.daily_trace.domain.summary.buildDailySummary
import com.gh.daily_trace.domain.time.DayClock

/**
 * 每日汇总提醒（M9-3）：
 * - 汇总「逻辑日」当天在册选项的完成情况；
 * - 暂停期间、开关关闭、通知权限缺失时静默跳过；
 * - 单次失败不重试（次日会再次运行，避免 WorkManager 退避把通知拖到深夜）。
 */
class DailySummaryWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? DailyTraceApp ?: return Result.success()
        val repository = app.container.repository
        val settings = runCatching { repository.getSettings() }.getOrNull() ?: return Result.success()

        if (!settings.dailySummaryEnabled) return Result.success()
        if (ReminderPause.isPaused(settings.pausedUntil, System.currentTimeMillis())) return Result.success()
        if (!ReminderPermissions.notificationsGranted(applicationContext)) return Result.success()

        runCatching {
            val today = DayClock.from(settings).today()
            val summary = buildDailySummary(repository.getAllItems(), repository.getRecordsOn(today))
            ReminderNotifier.notifyDailySummary(applicationContext, summary)
        }
        return Result.success()
    }

    companion object {
        /** 唯一周期任务名：同一时刻只存在一个汇总任务 */
        const val WORK_NAME = "daily_summary"
    }
}
