package com.gh.daily_trace.domain.reminder

/**
 * 提醒暂停口径（M9-2）：
 * - [pausedUntil] 为毫秒时间戳，严格大于当前时刻才算暂停中（等于即视为到期）；
 * - 暂停期间所有系列提醒与稍后提醒都不触发，恢复后按原规则重新排定。
 */
object ReminderPause {

    fun isPaused(pausedUntil: Long?, nowMillis: Long): Boolean =
        pausedUntil != null && pausedUntil > nowMillis

    /** 有效暂停截止时刻；未设置或已到期返回 null */
    fun activeUntil(pausedUntil: Long?, nowMillis: Long): Long? =
        pausedUntil?.takeIf { it > nowMillis }

    /** 自定义截止时间必须晚于当前时刻 */
    fun isValidUntil(untilMillis: Long, nowMillis: Long): Boolean =
        untilMillis > nowMillis
}
