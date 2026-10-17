package com.gh.daily_trace.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.gh.daily_trace.DailyTraceApp
import com.gh.daily_trace.data.model.CheckType
import com.gh.daily_trace.data.model.RecordStatus
import com.gh.daily_trace.data.model.Reminder
import com.gh.daily_trace.domain.checkin.CheckInPlanner
import com.gh.daily_trace.domain.checkin.applyPlan
import com.gh.daily_trace.domain.time.DayClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 通知动作：
 * - 打卡：布尔型直接落一条今日记录（幂等：已有记录则跳过），随后撤下通知；
 * - 稍后：取消当前通知并按 snooze 分钟排一次一次性闹钟。
 */
class ReminderActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as? DailyTraceApp ?: return
        val itemId = intent.getLongExtra(ReminderReceiver.EXTRA_ITEM_ID, -1L)
        if (itemId <= 0) return

        when (intent.action) {
            ACTION_CHECK_IN -> checkIn(context, app, itemId)
            ACTION_SNOOZE -> snooze(context, app, itemId)
        }
    }

    private fun checkIn(context: Context, app: DailyTraceApp, itemId: Long) {
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val repository = app.container.repository
                val item = repository.getItem(itemId) ?: return@launch
                if (item.type != CheckType.BOOLEAN) return@launch
                val settings = repository.getSettings()
                val today = DayClock.from(settings).today()
                val existing = repository.getRecordsOfItemOn(itemId, today)
                if (existing.none { it.status == RecordStatus.DONE }) {
                    applyPlan(
                        repository,
                        CheckInPlanner.toggleBoolean(
                            item = item,
                            date = today,
                            existing = existing,
                            timezone = settings.timezone,
                            isBackfill = false,
                            now = System.currentTimeMillis(),
                        ),
                    )
                }
                ReminderNotifier.cancel(context, itemId)
            } finally {
                pending.finish()
            }
        }
    }

    private fun snooze(context: Context, app: DailyTraceApp, itemId: Long) {
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                ReminderNotifier.cancel(context, itemId)
                val reminder = app.container.repository.getReminderOf(itemId)
                    ?: Reminder(itemId = itemId, time = 0)
                app.container.reminderScheduler.scheduleSnooze(reminder)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_CHECK_IN = "com.gh.daily_trace.action.REMINDER_CHECK_IN"
        const val ACTION_SNOOZE = "com.gh.daily_trace.action.REMINDER_SNOOZE"
    }
}
