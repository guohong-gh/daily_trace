package com.gh.daily_trace.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.gh.daily_trace.data.model.Reminder
import com.gh.daily_trace.data.model.RepeatType
import com.gh.daily_trace.data.repository.DailyTraceRepository
import com.gh.daily_trace.domain.reminder.ReminderTime
import java.time.Instant
import java.time.ZoneId

/**
 * 提醒调度（AlarmManager）：
 * - 精确闹钟可用时用 setExactAndAllowWhileIdle（准点触发）；
 * - 否则回退 setAndAllowWhileIdle（系统可能延迟数分钟），UI 引导授权。
 *
 * 每次触发由 ReminderReceiver 续排下一次；设备重启/时间变更由 ReminderBootReceiver 重排。
 * [now] 可注入以便测试。
 */
class ReminderScheduler(
    private val context: Context,
    private val repository: DailyTraceRepository,
    private val now: () -> Instant = Instant::now,
) {
    private val alarmManager: AlarmManager =
        context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun canScheduleExact(): Boolean = ReminderPermissions.canScheduleExactAlarms(context)

    /** 续排单个提醒的下一次触发；已禁用、选项不可用或 WEEKLY 未选日时取消当前闹钟 */
    suspend fun scheduleNext(reminder: Reminder) {
        if (!reminder.enabled) {
            cancel(reminder.id)
            return
        }
        val item = repository.getItem(reminder.itemId)
        if (item == null || item.deletedAt != null || item.archived || !item.enabled) {
            cancel(reminder.id)
            return
        }
        if (reminder.repeat == RepeatType.WEEKLY && reminder.daysOfWeek.isEmpty()) {
            cancel(reminder.id)
            return
        }
        val settings = repository.getSettings()
        val zone = runCatching { ZoneId.of(settings.timezone) }.getOrDefault(ZoneId.systemDefault())
        val at = ReminderTime.nextTriggerAfter(
            after = now(),
            zone = zone,
            timeMinutes = reminder.time,
            repeat = reminder.repeat,
            daysOfWeek = reminder.daysOfWeek,
        )
        setAlarm(at.toEpochMilli(), firePendingIntent(reminder.id, reminder.itemId))
    }

    /** 稍后提醒：一次性触发，不推进系列 */
    suspend fun scheduleSnooze(reminder: Reminder) {
        val at = now().toEpochMilli() + reminder.snooze.coerceIn(1, 1440) * 60_000L
        setAlarm(at, snoozePendingIntent(reminder.id, reminder.itemId))
    }

    /** 取消某提醒的系列闹钟与稍后闹钟 */
    fun cancel(reminderId: Long) {
        alarmManager.cancel(firePendingIntent(reminderId, itemId = 0L))
        alarmManager.cancel(snoozePendingIntent(reminderId, itemId = 0L))
    }

    /** 重排全部启用提醒（启动/重启/时间变更后） */
    suspend fun rescheduleAll() {
        repository.getEnabledReminders().forEach { scheduleNext(it) }
    }

    private fun setAlarm(atMillis: Long, pi: PendingIntent) {
        if (canScheduleExact()) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pi)
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pi)
        }
    }

    // PendingIntent 相等性只看 action/class/requestCode，extras 可后补
    private fun firePendingIntent(reminderId: Long, itemId: Long): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            reminderId.toInt(),
            Intent(context, ReminderReceiver::class.java)
                .setAction(ReminderReceiver.ACTION_FIRE)
                .putExtra(ReminderReceiver.EXTRA_ITEM_ID, itemId)
                .putExtra(ReminderReceiver.EXTRA_REMINDER_ID, reminderId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun snoozePendingIntent(reminderId: Long, itemId: Long): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            reminderId.toInt(),
            Intent(context, ReminderReceiver::class.java)
                .setAction(ReminderReceiver.ACTION_FIRE_SNOOZE)
                .putExtra(ReminderReceiver.EXTRA_ITEM_ID, itemId)
                .putExtra(ReminderReceiver.EXTRA_REMINDER_ID, reminderId)
                .putExtra(ReminderReceiver.EXTRA_SNOOZE, true),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
}
