package com.gh.daily_trace.domain.checkin

/**
 * 补卡策略：
 * - date == today：允许（正常打卡，isBackfill=false）；
 * - date < today：需 maxBackfillDays > 0 且（today - date）<= maxBackfillDays；
 * - date > today：不允许打卡记录，但允许创建「计划中」（见 [canPlan]）。
 *
 * 说明：跳过/休息日登记（rule.skipDates）不产生打卡记录，不受本策略限制。
 */
object BackfillPolicy {

    fun canCheckIn(date: Long, today: Long, maxBackfillDays: Int): Boolean = when {
        date > today -> false
        date == today -> true
        else -> maxBackfillDays > 0 && (today - date) <= maxBackfillDays
    }

    /** 是否作为补卡记录写入（与今天不同的日期） */
    fun isBackfill(date: Long, today: Long): Boolean = date != today

    /** 「计划中」仅面向未来日期 */
    fun canPlan(date: Long, today: Long): Boolean = date > today
}
