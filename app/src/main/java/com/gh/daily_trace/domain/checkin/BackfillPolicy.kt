package com.gh.daily_trace.domain.checkin

/**
 * 补卡策略：
 * - date == today：允许（正常打卡，isBackfill=false）；
 * - date < today：需 maxBackfillDays > 0 且（today - date）<= maxBackfillDays；
 * - date > today：不允许（M2 不支持补未来）。
 */
object BackfillPolicy {

    fun canCheckIn(date: Long, today: Long, maxBackfillDays: Int): Boolean = when {
        date > today -> false
        date == today -> true
        else -> maxBackfillDays > 0 && (today - date) <= maxBackfillDays
    }

    /** 是否作为补卡记录写入（与今天不同的日期） */
    fun isBackfill(date: Long, today: Long): Boolean = date != today
}
