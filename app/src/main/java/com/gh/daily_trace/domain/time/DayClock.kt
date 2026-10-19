package com.gh.daily_trace.domain.time

import com.gh.daily_trace.data.model.AppSettings
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * 逻辑日计算器。
 *
 * 约定（与 CheckRecord.date / DayNote.date 的语义一致）：
 * - 日期用「本地日历日」表示，即 epochDay（1970-01-01 起的天数，Long）。
 * - 当日 startOfDay 小时（默认 4 点）之前算作前一天：10-17 03:59 属于 10-16，04:00 起属于 10-17。
 * - 所有换算以 AppSettings.timezone 为准，避免跨时区/夏令时导致跨天错乱。
 *
 * 纯函数式、无缓存；可注入 [Clock] 以便测试固定时间。
 */
class DayClock(
    val zone: ZoneId,
    val startOfDayHour: Int,
    val weekStart: DayOfWeek = DayOfWeek.MONDAY,
    private val clock: Clock = Clock.systemDefaultZone(),
) {

    /** 当前逻辑日（epochDay） */
    fun today(): Long = toLogicalDay(clock.instant())

    /** 任意时刻 → 逻辑日（epochDay） */
    fun toLogicalDay(instant: Instant): Long {
        val local = LocalDateTime.ofInstant(instant, zone)
        return local.minusHours(startOfDayHour.toLong()).toLocalDate().toEpochDay()
    }

    /** 逻辑日的开始时刻（含） */
    fun startOfDay(epochDay: Long): Instant =
        LocalDate.ofEpochDay(epochDay).atTime(startOfDayHour, 0).atZone(zone).toInstant()

    /** 逻辑日的结束时刻（不含，等于下一天的开始） */
    fun endOfDay(epochDay: Long): Instant = startOfDay(epochDay + 1)

    /** 逻辑日毫秒时长；夏令时切换日为 23h/25h，异常时可用于自检 */
    fun dayLengthMillis(epochDay: Long): Long =
        endOfDay(epochDay).toEpochMilli() - startOfDay(epochDay).toEpochMilli()

    fun addDays(epochDay: Long, days: Long): Long = epochDay + days

    /** to - from，正数表示 to 在后 */
    fun daysBetween(from: Long, to: Long): Long = to - from

    fun weekdayOf(epochDay: Long): DayOfWeek = LocalDate.ofEpochDay(epochDay).dayOfWeek

    /** 所在周的起始逻辑日（依据 weekStart，1=周一 … 7=周日） */
    fun weekStartOf(epochDay: Long): Long = weekStartOf(epochDay, weekStart)

    /** 所在周的 7 个逻辑日（从周起始日算起） */
    fun weekDatesOf(epochDay: Long): List<Long> {
        val start = weekStartOf(epochDay)
        return (0L until 7L).map { start + it }
    }

    /** 按模式格式化逻辑日，默认中文（如 "EEE" → 周六） */
    fun format(epochDay: Long, pattern: String, locale: Locale = Locale.CHINA): String =
        LocalDate.ofEpochDay(epochDay).format(DateTimeFormatter.ofPattern(pattern, locale))

    /** 某月的全部逻辑日 */
    fun monthDatesOf(yearMonth: YearMonth): List<Long> {
        val first = yearMonth.atDay(1).toEpochDay()
        return (0 until yearMonth.lengthOfMonth()).map { first + it }
    }

    /**
     * 月历网格：按周排布，首尾补 null；行数 4-6 视月份与 weekStart 而定。
     * 每行 7 个格子，null 表示非本月。
     */
    fun monthGridRowsOf(yearMonth: YearMonth): List<List<Long?>> =
        monthGridRows(yearMonth, weekStart)

    companion object {
        /** 所在周的起始逻辑日（纯函数版，供不持有 DayClock 的调用方使用） */
        fun weekStartOf(epochDay: Long, weekStart: DayOfWeek): Long {
            val dow = LocalDate.ofEpochDay(epochDay).dayOfWeek.value
            val diff = Math.floorMod(dow - weekStart.value, 7).toLong()
            return epochDay - diff
        }

        /**
         * 月历网格（纯函数版，供滑动翻月逐页渲染）。
         * 与 [monthGridRowsOf] 同口径：按周排布，首尾补 null；行数 4-6。
         */
        fun monthGridRows(yearMonth: YearMonth, weekStart: DayOfWeek): List<List<Long?>> {
            val first = yearMonth.atDay(1).toEpochDay()
            val last = yearMonth.atEndOfMonth().toEpochDay()
            val gridStart = weekStartOf(first, weekStart)
            val gridEnd = weekStartOf(last, weekStart) + 6
            return (gridStart..gridEnd).chunked(7) { week ->
                week.map { d -> if (d in first..last) d else null }
            }
        }

        /** 从应用设置构建；时区非法时回退系统默认 */
        fun from(settings: AppSettings, clock: Clock = Clock.systemDefaultZone()): DayClock = DayClock(
            zone = runCatching { ZoneId.of(settings.timezone) }.getOrDefault(ZoneId.systemDefault()),
            startOfDayHour = settings.startOfDay.coerceIn(0, 23),
            weekStart = DayOfWeek.of(settings.weekStart.coerceIn(1, 7)),
            clock = clock,
        )
    }
}
