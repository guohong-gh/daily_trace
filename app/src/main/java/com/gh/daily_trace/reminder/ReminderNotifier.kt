package com.gh.daily_trace.reminder

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.gh.daily_trace.MainActivity
import com.gh.daily_trace.R
import com.gh.daily_trace.data.model.CheckItem
import com.gh.daily_trace.data.model.CheckType
import com.gh.daily_trace.data.model.Reminder

/**
 * 提醒通知：两个渠道（普通/安静）。
 * 免打扰时段内触发用安静渠道（低优先级，无声音），不获取系统 DND 豁免权限。
 */
object ReminderNotifier {

    const val CHANNEL_DEFAULT = "reminders_default"
    const val CHANNEL_QUIET = "reminders_quiet"

    fun ensureChannels(context: Context) {
        // 通知渠道 API 26 起存在；24/25 无渠道概念，直接用默认渠道
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_DEFAULT, "打卡提醒", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "按设定时间提醒打卡"
            }
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_QUIET, "打卡提醒（安静）", NotificationManager.IMPORTANCE_LOW).apply {
                description = "免打扰时段内的提醒，静默显示"
            }
        )
    }

    /** 权限已在首行显式检查（ReminderPermissions.notificationsGranted），lint 无法跨函数推断故抑制 */
    @SuppressLint("MissingPermission")
    fun notifyReminder(context: Context, item: CheckItem, reminder: Reminder, quiet: Boolean) {
        if (!ReminderPermissions.notificationsGranted(context)) return
        ensureChannels(context)

        val channel = if (quiet) CHANNEL_QUIET else CHANNEL_DEFAULT
        val builder = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("打卡提醒：${item.name}")
            .setContentText(textFor(item))
            .setContentIntent(openAppIntent(context, item.id))
            .setAutoCancel(true)
            .setPriority(
                if (quiet) NotificationCompat.PRIORITY_LOW else NotificationCompat.PRIORITY_HIGH
            )

        if (item.type == CheckType.BOOLEAN) {
            builder.addAction(0, "打卡", checkInIntent(context, item.id))
        }
        builder.addAction(0, "稍后 ${reminder.snooze} 分钟", snoozeIntent(context, item.id))

        NotificationManagerCompat.from(context).notify(item.id.toInt(), builder.build())
    }

    fun cancel(context: Context, itemId: Long) {
        NotificationManagerCompat.from(context).cancel(itemId.toInt())
    }

    /** 暂停提醒时撤下全部已发通知 */
    fun cancelAll(context: Context) {
        NotificationManagerCompat.from(context).cancelAll()
    }

    private fun textFor(item: CheckItem): String =
        if (item.type == CheckType.BOOLEAN) "点击「打卡」即可完成" else "去记录一笔吧"

    private fun openAppIntent(context: Context, itemId: Long): PendingIntent =
        PendingIntent.getActivity(
            context,
            itemId.toInt(),
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun checkInIntent(context: Context, itemId: Long): PendingIntent =
        actionIntent(context, itemId, ReminderActionReceiver.ACTION_CHECK_IN)

    private fun snoozeIntent(context: Context, itemId: Long): PendingIntent =
        actionIntent(context, itemId, ReminderActionReceiver.ACTION_SNOOZE)

    private fun actionIntent(context: Context, itemId: Long, action: String): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            itemId.toInt(),
            Intent(context, ReminderActionReceiver::class.java)
                .setAction(action)
                .putExtra(ReminderReceiver.EXTRA_ITEM_ID, itemId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
}
