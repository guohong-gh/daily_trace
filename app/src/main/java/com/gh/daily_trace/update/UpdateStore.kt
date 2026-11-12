package com.gh.daily_trace.update

import android.content.Context
import androidx.core.content.edit

/**
 * 更新检查的本地状态。
 *
 * 属于设备本地的 UI 状态（已提示版本、最近检查时间与结果），不进 Room、不进备份，
 * 因此不会影响数据库版本与备份格式。
 */
class UpdateStore(context: Context) {

    private val prefs = context.getSharedPreferences("update_check", Context.MODE_PRIVATE)

    var lastPromptedVersion: String?
        get() = prefs.getString(KEY_PROMPTED, null)
        set(value) = prefs.edit { putString(KEY_PROMPTED, value) }

    var lastCheckAt: Long?
        get() = prefs.getLong(KEY_CHECKED_AT, 0L).takeIf { it > 0L }
        set(value) = prefs.edit { putLong(KEY_CHECKED_AT, value ?: 0L) }

    var lastResult: String?
        get() = prefs.getString(KEY_RESULT, null)
        set(value) = prefs.edit { putString(KEY_RESULT, value) }

    private companion object {
        const val KEY_PROMPTED = "last_prompted_version"
        const val KEY_CHECKED_AT = "last_check_at"
        const val KEY_RESULT = "last_result"
    }
}
