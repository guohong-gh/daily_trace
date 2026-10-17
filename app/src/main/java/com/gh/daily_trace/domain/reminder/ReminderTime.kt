package com.gh.daily_trace.domain.reminder

import com.gh.daily_trace.data.model.RepeatType
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * 提醒时刻计算（纯函数）。
 *
 * 约定：
 * - 提醒时刻为当天 0 点起分钟数（0..1439），按 AppSettings.timezone 解释；
 * - DAILY 每天；WEEKDAYS 周一~周五；WEEKLY 仅 FrequencyRule.daysOfWeek（ISO 1=周一..7=周日，空=不触发）；
 * - [nextTriggerAfter] 严格晚于给定时刻，最多向后找 8 天（覆盖周重复的极端情况）。
 * - 夏令时跳变导致目标本地时刻不存在时，java.time 会顺延到最近的合法时刻（可接受）。
 */
object ReminderTime {

    fun shouldFireOn(epochDay: Long, repeat: RepeatType, daysOfWeek: List<Int>): Boolean {
        val dow = LocalDate.ofEpochDay(epochDay).dayOfWeek.value
        return when (repeat) {
            RepeatType.DAILY -> true
            RepeatType.WEEKDAYS -> dow in 1..5
            RepeatType.WEEKLY -> dow in daysOfWeek
        }
    }

    /** 下一次触发时刻（严格晚于 [after]） */
    fun nextTriggerAfter(
        after: Instant,
        zone: ZoneId,
        timeMinutes: Int,
        repeat: RepeatType,
        daysOfWeek: List<Int>,
    ): Instant {
        val minutes = timeMinutes.coerceIn(0, 1439)
        val time = LocalTime.of(minutes / 60, minutes % 60)
        var date = LocalDate.ofInstant(after, zone)
        repeat(8) {
            val candidate = date.atTime(time).atZone(zone).toInstant()
            if (candidate.isAfter(after) && shouldFireOn(date.toEpochDay(), repeat, daysOfWeek)) {
                return candidate
            }
            date = date.plusDays(1)
        }
        // WEEKLY 未选日等兜底：返回 8 天后同一时刻（调度层会先拦截避免这种提醒入库）
        return date.atTime(time).atZone(zone).toInstant()
    }

    /**
     * 时刻（距 0 点分钟）是否落在免打扰窗口内。
     * 支持跨午夜窗口（如 22:00~07:00）；start==end 视为未启用。
     */
    fun isInDndWindow(timeMinutes: Int, dndStart: Int?, dndEnd: Int?): Boolean {
        if (dndStart == null || dndEnd == null || dndStart == dndEnd) return false
        val t = timeMinutes.coerceIn(0, 1439)
        return if (dndStart < dndEnd) {
            t >= dndStart && t < dndEnd
        } else {
            t >= dndStart || t < dndEnd
        }
    }
}
