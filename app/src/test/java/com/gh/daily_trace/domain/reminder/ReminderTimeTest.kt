package com.gh.daily_trace.domain.reminder

import com.gh.daily_trace.data.model.RepeatType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class ReminderTimeTest {

    private val shanghai = ZoneId.of("Asia/Shanghai")

    private fun at(text: String): Instant = Instant.parse(text)

    // ---------- shouldFireOn ----------

    @Test
    fun `DAILY每天触发`() {
        // 2026-10-17 周六 / 2026-10-19 周一
        assertTrue(ReminderTime.shouldFireOn(20743L, RepeatType.DAILY, emptyList()))
    }

    @Test
    fun `WEEKDAYS跳过周末`() {
        val saturday = 20743L // 2026-10-17（周六）
        val sunday = saturday + 1
        val monday = saturday + 2
        assertFalse(ReminderTime.shouldFireOn(saturday, RepeatType.WEEKDAYS, emptyList()))
        assertFalse(ReminderTime.shouldFireOn(sunday, RepeatType.WEEKDAYS, emptyList()))
        assertTrue(ReminderTime.shouldFireOn(monday, RepeatType.WEEKDAYS, emptyList()))
    }

    @Test
    fun `WEEKLY只在与所选周几匹配时触发`() {
        val saturday = 20743L // 周六 = 6
        val monday = saturday + 2 // 周一 = 1
        assertTrue(ReminderTime.shouldFireOn(saturday, RepeatType.WEEKLY, listOf(1, 6)))
        assertFalse(ReminderTime.shouldFireOn(monday, RepeatType.WEEKLY, listOf(6)))
        assertFalse(ReminderTime.shouldFireOn(monday, RepeatType.WEEKLY, emptyList()))
    }

    // ---------- nextTriggerAfter ----------

    @Test
    fun `当天时刻未到取当天`() {
        // 上海 2026-10-17 00:00，提醒 08:00 → 当天 08:00
        val after = at("2026-10-16T16:00:00Z")
        val next = ReminderTime.nextTriggerAfter(after, shanghai, 8 * 60, RepeatType.DAILY, emptyList())
        assertEquals(at("2026-10-17T00:00:00Z"), next) // 上海 08:00
    }

    @Test
    fun `时刻已过取次日`() {
        // 上海 2026-10-17 10:00，提醒 08:00 → 次日 08:00
        val after = at("2026-10-17T02:00:00Z")
        val next = ReminderTime.nextTriggerAfter(after, shanghai, 8 * 60, RepeatType.DAILY, emptyList())
        assertEquals(at("2026-10-18T00:00:00Z"), next)
    }

    @Test
    fun `恰好等于触发时刻时严格取下一次`() {
        val after = at("2026-10-17T00:00:00Z") // 上海 08:00 整
        val next = ReminderTime.nextTriggerAfter(after, shanghai, 8 * 60, RepeatType.DAILY, emptyList())
        assertEquals(at("2026-10-18T00:00:00Z"), next)
    }

    @Test
    fun `WEEKDAYS周五晚上跳到周一`() {
        // 上海 2026-10-16（周五）20:00，提醒 08:00 → 周一 10-19 08:00
        val after = at("2026-10-16T12:00:00Z")
        val next = ReminderTime.nextTriggerAfter(after, shanghai, 8 * 60, RepeatType.WEEKDAYS, emptyList())
        assertEquals(at("2026-10-19T00:00:00Z"), next)
    }

    @Test
    fun `WEEKLY只选周一从周六跳到周一`() {
        // 上海 2026-10-17（周六）12:00，提醒 09:00，仅周一 → 10-19 09:00
        val after = at("2026-10-17T04:00:00Z")
        val next = ReminderTime.nextTriggerAfter(after, shanghai, 9 * 60, RepeatType.WEEKLY, listOf(1))
        assertEquals(at("2026-10-19T01:00:00Z"), next)
    }

    @Test
    fun `跨夏令时跳变不抛异常且仍在未来`() {
        // 美东 2026-03-08 02:30 不存在 → java.time 顺延
        val ny = ZoneId.of("America/New_York")
        val after = at("2026-03-07T12:00:00Z") // 美东 03-07 07:00
        val next = ReminderTime.nextTriggerAfter(after, ny, 2 * 60 + 30, RepeatType.DAILY, emptyList())
        assertTrue(next.isAfter(after))
    }

    // ---------- isInDndWindow ----------

    @Test
    fun `普通时段窗口判断`() {
        val start = 9 * 60
        val end = 17 * 60
        assertTrue(ReminderTime.isInDndWindow(10 * 60, start, end))
        assertFalse(ReminderTime.isInDndWindow(8 * 60, start, end))
        assertFalse(ReminderTime.isInDndWindow(end, start, end)) // 结束为开区间
        assertTrue(ReminderTime.isInDndWindow(start, start, end)) // 起始为闭区间
    }

    @Test
    fun `跨午夜窗口判断`() {
        val start = 22 * 60
        val end = 7 * 60
        assertTrue(ReminderTime.isInDndWindow(23 * 60, start, end))
        assertTrue(ReminderTime.isInDndWindow(100, start, end)) // 01:40
        assertFalse(ReminderTime.isInDndWindow(12 * 60, start, end))
        assertFalse(ReminderTime.isInDndWindow(end, start, end)) // 07:00 开区间
    }

    @Test
    fun `未设置或起止相同视为未启用`() {
        assertFalse(ReminderTime.isInDndWindow(600, null, 600))
        assertFalse(ReminderTime.isInDndWindow(600, 600, null))
        assertFalse(ReminderTime.isInDndWindow(600, 600, 600))
    }
}
