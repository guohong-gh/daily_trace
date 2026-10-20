package com.gh.daily_trace.reminder

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.gh.daily_trace.data.model.AppSettings
import com.gh.daily_trace.domain.summary.nextSummaryMillis
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/**
 * 每日汇总的 WorkManager 周期任务（M9-3）。
 *
 * 每次 [sync] 都按当前设置重建任务：首次触发延迟到下一个汇总时刻，
 * 之后每 24 小时一次。WorkManager 的周期任务不保证秒级准点（通常误差数分钟）。
 */
class DailySummaryScheduler(
    private val context: Context,
    private val now: () -> Long = System::currentTimeMillis,
) {

    fun sync(settings: AppSettings) {
        if (!settings.dailySummaryEnabled) {
            cancel()
            return
        }
        val current = now()
        val zone = runCatching { ZoneId.of(settings.timezone) }.getOrDefault(ZoneId.systemDefault())
        val delay = (nextSummaryMillis(current, settings.dailySummaryTime, zone) - current).coerceAtLeast(0)
        val request = PeriodicWorkRequestBuilder<DailySummaryWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .addTag(DailySummaryWorker.WORK_NAME)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            DailySummaryWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE,
            request,
        )
    }

    fun cancel() {
        WorkManager.getInstance(context).cancelUniqueWork(DailySummaryWorker.WORK_NAME)
    }
}
