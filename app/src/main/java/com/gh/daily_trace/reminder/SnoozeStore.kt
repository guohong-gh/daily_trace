package com.gh.daily_trace.reminder

import android.content.Context
import androidx.core.content.edit

/**
 * 「稍后」闹钟的持久化（M13-2）：设备重启会清空一次性闹钟，重排时据此恢复。
 * 只存应用私有的瞬态排期（值为 "itemId:triggerAtMillis"），
 * 不进备份、不改 Room schema——过期即删，不属于用户数据。
 */
class SnoozeStore(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun put(reminderId: Long, itemId: Long, triggerAtMillis: Long) {
        prefs.edit { putString(reminderId.toString(), "$itemId:$triggerAtMillis") }
    }

    /** reminderId → (itemId, triggerAtMillis) */
    fun all(): Map<Long, Pair<Long, Long>> = prefs.all.mapNotNull { (key, value) ->
        val reminderId = key.toLongOrNull() ?: return@mapNotNull null
        val parts = (value as? String)?.split(':') ?: return@mapNotNull null
        val itemId = parts.getOrNull(0)?.toLongOrNull() ?: return@mapNotNull null
        val triggerAt = parts.getOrNull(1)?.toLongOrNull() ?: return@mapNotNull null
        reminderId to (itemId to triggerAt)
    }.toMap()

    fun remove(reminderId: Long) {
        prefs.edit { remove(reminderId.toString()) }
    }

    private companion object {
        const val PREFS_NAME = "snooze_prefs"
    }
}
