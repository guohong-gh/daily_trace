package com.gh.daily_trace.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.gh.daily_trace.DailyTraceApp
import com.gh.daily_trace.domain.reminder.ReminderTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 提醒触发接收器：
 * - 校验选项仍有效（未删除/未停用/未归档）后发出通知；
 * - 非「稍后」路径续排下一次（先续排再发通知，避免异常中断系列）。
 */
class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as? DailyTraceApp ?: return
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val repository = app.container.repository
                val itemId = intent.getLongExtra(EXTRA_ITEM_ID, -1L)
                val isSnooze = intent.getBooleanExtra(EXTRA_SNOOZE, false)
                val item = repository.getItem(itemId)
                val reminder = repository.getReminderOf(itemId)

                if (item == null || item.deletedAt != null || !item.enabled || item.archived || reminder == null) {
                    return@launch
                }
                if (!isSnooze) {
                    if (!reminder.enabled) return@launch
                    app.container.reminderScheduler.scheduleNext(reminder)
                }
                val quiet = ReminderTime.isInDndWindow(reminder.time, reminder.dndStart, reminder.dndEnd)
                ReminderNotifier.notifyReminder(context, item, reminder, quiet)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_FIRE = "com.gh.daily_trace.action.REMINDER_FIRE"
        const val ACTION_FIRE_SNOOZE = "com.gh.daily_trace.action.REMINDER_FIRE_SNOOZE"
        const val EXTRA_ITEM_ID = "itemId"
        const val EXTRA_REMINDER_ID = "reminderId"
        const val EXTRA_SNOOZE = "snooze"
    }
}
