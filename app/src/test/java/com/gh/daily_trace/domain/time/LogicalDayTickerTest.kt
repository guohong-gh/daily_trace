package com.gh.daily_trace.domain.time

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/** 跨逻辑日自动刷新（M13-1）的睡眠时长计算 */
class LogicalDayTickerTest {

    private val zone = ZoneId.of("Asia/Shanghai")

    private fun instantOf(year: Int, month: Int, day: Int, hour: Int, minute: Int, second: Int = 0): Instant =
        LocalDateTime.of(year, month, day, hour, minute, second).atZone(zone).toInstant()

    @Test
    fun `边界前 30 秒返回 30 秒`() {
        val clock = DayClock(zone, startOfDayHour = 4)
        // 2026-11-02 03:59:30 +08 属于逻辑日 11-01，边界为 11-02 04:00
        val delay = logicalDayDelayMillis(
            today = clock.toLogicalDay(instantOf(2026, 11, 2, 3, 59, 30)),
            clock = clock,
            now = instantOf(2026, 11, 2, 3, 59, 30),
        )
        assertEquals(30_000L, delay)
    }

    @Test
    fun `整点跨界后一天的睡眠被上限截断`() {
        val clock = DayClock(zone, startOfDayHour = 4)
        val now = instantOf(2026, 11, 2, 4, 0, 0) // 距下一边界 24 小时
        val delay = logicalDayDelayMillis(today = clock.toLogicalDay(now), clock = clock, now = now)
        assertEquals(60_000L, delay)
    }

    @Test
    fun `自定义上限生效`() {
        val clock = DayClock(zone, startOfDayHour = 4)
        val now = instantOf(2026, 11, 2, 3, 59, 30)
        val delay = logicalDayDelayMillis(
            today = clock.toLogicalDay(now),
            clock = clock,
            now = now,
            capMillis = 5_000L,
        )
        assertEquals(5_000L, delay)
    }

    @Test
    fun `边界已过时按下限唤醒复查`() {
        val clock = DayClock(zone, startOfDayHour = 4)
        // 传入过期的 today（边界已在 now 之前）→ 不返回负数，按下限 1 秒醒来
        val now = instantOf(2026, 11, 2, 4, 0, 0)
        val delay = logicalDayDelayMillis(today = clock.toLogicalDay(now) - 1, clock = clock, now = now)
        assertEquals(1_000L, delay)
    }

    @Test
    fun `夏令时切换日按 endOfDay 计算得到 23 小时`() {
        val ny = ZoneId.of("America/New_York")
        val clock = DayClock(ny, startOfDayHour = 4)
        // 美东 2026-03-08 02:00 跳夏令时：逻辑日 03-07 只有 23 小时
        val now = LocalDateTime.of(2026, 3, 7, 4, 0).atZone(ny).toInstant()
        val delay = logicalDayDelayMillis(
            today = clock.toLogicalDay(now),
            clock = clock,
            now = now,
            capMillis = 25 * 3_600_000L,
        )
        assertEquals(23 * 3_600_000L, delay)
    }
}
