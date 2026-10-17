package com.gh.daily_trace.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.gh.daily_trace.DailyTraceApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** 开机/改时间/改时区/应用更新后重排全部启用提醒（闹钟在这些场景会被系统清空） */
class ReminderBootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        // 仅处理声明的系统广播，拒绝其他来源（含伪造 action 的外部广播）
        if (intent.action !in HANDLED_ACTIONS) return
        val app = context.applicationContext as? DailyTraceApp ?: return
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                app.container.reminderScheduler.rescheduleAll()
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        private val HANDLED_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
        )
    }
}
