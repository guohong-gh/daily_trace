package com.gh.daily_trace.domain.time

import com.gh.daily_trace.data.model.AppSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.util.Locale

class DayClockTest {

    private val shanghai = ZoneId.of("Asia/Shanghai")

    private fun day(y: Int, m: Int, d: Int): Long = LocalDate.of(y, m, d).toEpochDay()

    private fun clock(zone: ZoneId = shanghai, startOfDay: Int = 4, weekStart: DayOfWeek = DayOfWeek.MONDAY) =
        DayClock(zone = zone, startOfDayHour = startOfDay, weekStart = weekStart)

    // ---------- startOfDay 边界 ----------

    @Test
    fun `凌晨4点前属于前一天`() {
        val c = clock()
        // 上海 2026-10-17 03:59:59 = UTC 2026-10-16 19:59:59
        assertEquals(day(2026, 10, 16), c.toLogicalDay(Instant.parse("2026-10-16T19:59:59Z")))
    }

    @Test
    fun `凌晨4点整属于当天`() {
        val c = clock()
        // 上海 2026-10-17 04:00:00 = UTC 2026-10-16 20:00:00
        assertEquals(day(2026, 10, 17), c.toLogicalDay(Instant.parse("2026-10-16T20:00:00Z")))
    }

    @Test
    fun `午夜零点半属于前一天`() {
        val c = clock()
        // 上海 2026-10-17 00:30 = UTC 2026-10-16 16:30
        assertEquals(day(2026, 10, 16), c.toLogicalDay(Instant.parse("2026-10-16T16:30:00Z")))
    }

    @Test
    fun `startOfDay为0时等价自然日`() {
        val c = clock(startOfDay = 0)
        assertEquals(day(2026, 10, 17), c.toLogicalDay(Instant.parse("2026-10-16T16:30:00Z")))
        assertEquals(day(2026, 10, 16), c.toLogicalDay(Instant.parse("2026-10-16T15:59:59Z")))
    }

    // ---------- 时区 ----------

    @Test
    fun `同一时刻在不同时区可属于不同逻辑日`() {
        val instant = Instant.parse("2026-10-16T20:30:00Z") // 上海 10-17 04:30；UTC 10-16 20:30
        assertEquals(day(2026, 10, 17), clock(shanghai).toLogicalDay(instant))
        assertEquals(day(2026, 10, 16), clock(ZoneId.of("UTC")).toLogicalDay(instant))
    }

    // ---------- start/end 与往返一致性 ----------

    @Test
    fun `逻辑日区间往返一致`() {
        val c = clock()
        for (d in listOf(day(2020, 1, 1), day(2026, 2, 28), day(2026, 10, 17), day(2030, 12, 31))) {
            assertEquals(d, c.toLogicalDay(c.startOfDay(d)))
            assertEquals(d, c.toLogicalDay(c.endOfDay(d).minusMillis(1)))
            assertEquals(d, c.toLogicalDay(c.startOfDay(d).plusMillis(1)))
        }
    }

    @Test
    fun `夏令时切换日长度不为24小时`() {
        val ny = clock(ZoneId.of("America/New_York"))
        // 美国夏令时在凌晨 2 点切换；对「04:00 至次日 04:00」的逻辑日而言，
        // 切换发生在 2026-03-07 的区间内（23 小时）、2026-10-31 的区间内（25 小时）。
        assertEquals(23 * 3600_000L, ny.dayLengthMillis(day(2026, 3, 7)))
        assertEquals(25 * 3600_000L, ny.dayLengthMillis(day(2026, 10, 31)))
        // 切换当日与普通日均为 24 小时
        assertEquals(24 * 3600_000L, ny.dayLengthMillis(day(2026, 3, 8)))
        assertEquals(24 * 3600_000L, ny.dayLengthMillis(day(2026, 6, 1)))
    }

    // ---------- 周与差值 ----------

    @Test
    fun `周起始遵循weekStart设置`() {
        val saturday = day(2026, 10, 17) // 周六
        assertEquals(day(2026, 10, 12), clock(weekStart = DayOfWeek.MONDAY).weekStartOf(saturday))
        assertEquals(day(2026, 10, 11), clock(weekStart = DayOfWeek.SUNDAY).weekStartOf(saturday))
        // 周日当天：周日为周起始时即自身
        val sunday = day(2026, 10, 18)
        assertEquals(sunday, clock(weekStart = DayOfWeek.SUNDAY).weekStartOf(sunday))
        assertEquals(7, clock().weekDatesOf(saturday).size)
    }

    @Test
    fun `天数差与加减天数`() {
        val c = clock()
        assertEquals(1L, c.daysBetween(day(2026, 10, 16), day(2026, 10, 17)))
        assertEquals(-3L, c.daysBetween(day(2026, 10, 17), day(2026, 10, 14)))
        assertEquals(day(2026, 10, 20), c.addDays(day(2026, 10, 17), 3))
    }

    // ---------- 展示 ----------

    @Test
    fun `中文格式化星期`() {
        val c = clock()
        assertEquals("周六", c.format(day(2026, 10, 17), "EEE", Locale.CHINA))
    }

    // ---------- 月历网格 ----------

    @Test
    fun `周一起始的月历网格补齐空位`() {
        val c = clock() // weekStart=周一
        val rows = c.monthGridRowsOf(YearMonth.of(2026, 10))
        // 2026-10-01 为周四 → 首行前 3 位补 null
        assertEquals(listOf(null, null, null), rows.first().take(3))
        assertEquals(day(2026, 10, 1), rows.first()[3])
        assertTrue(rows.all { it.size == 7 })
        // 所有本月日期按序出现且无缺失
        val flat = rows.flatten().filterNotNull()
        assertEquals(c.monthDatesOf(YearMonth.of(2026, 10)), flat)
    }

    @Test
    fun `周日起始时网格偏移不同`() {
        val rows = clock(weekStart = DayOfWeek.SUNDAY).monthGridRowsOf(YearMonth.of(2026, 10))
        assertEquals(listOf(null, null, null, null), rows.first().take(4))
        assertEquals(day(2026, 10, 1), rows.first()[4])
    }

    @Test
    fun `月历网格纯函数版与实例版一致`() {
        for (ym in listOf(YearMonth.of(1970, 1), YearMonth.of(2026, 10), YearMonth.of(2100, 12))) {
            assertEquals(clock().monthGridRowsOf(ym), DayClock.monthGridRows(ym, DayOfWeek.MONDAY))
            assertEquals(
                clock(weekStart = DayOfWeek.SUNDAY).monthGridRowsOf(ym),
                DayClock.monthGridRows(ym, DayOfWeek.SUNDAY),
            )
        }
    }

    @Test
    fun `月历网格行数可为四或六行`() {
        // 2027-02：2 月 1 日为周一、28 天 → 恰好 4 行
        assertEquals(4, DayClock.monthGridRows(YearMonth.of(2027, 2), DayOfWeek.MONDAY).size)
        // 2026-08：8 月 1 日周六、31 日周一 → 首尾跨 6 周
        assertEquals(6, DayClock.monthGridRows(YearMonth.of(2026, 8), DayOfWeek.MONDAY).size)
    }

    @Test
    fun `闰年二月网格包含29日`() {
        val rows = DayClock.monthGridRows(YearMonth.of(2028, 2), DayOfWeek.MONDAY)
        val flat = rows.flatten().filterNotNull()
        assertEquals(29, flat.size)
        assertEquals(day(2028, 2, 29), flat.last())
    }

    @Test
    fun `网格每行七格非本月补null且日期连续`() {
        for (ym in listOf(YearMonth.of(1970, 1), YearMonth.of(2026, 2), YearMonth.of(2100, 12))) {
            val rows = DayClock.monthGridRows(ym, DayOfWeek.SUNDAY)
            assertTrue(rows.all { it.size == 7 })
            val flat = rows.flatten().filterNotNull()
            assertEquals(ym.lengthOfMonth().toLong(), flat.size.toLong())
            flat.forEachIndexed { index, d ->
                assertEquals(ym.atDay(1).toEpochDay() + index, d)
            }
        }
    }

    // ---------- 从设置构建 ----------

    @Test
    fun `from设置读取时区与起始小时`() {
        val c = DayClock.from(AppSettings(timezone = "Asia/Shanghai", startOfDay = 5, weekStart = 7))
        assertEquals(shanghai, c.zone)
        assertEquals(5, c.startOfDayHour)
        assertEquals(DayOfWeek.SUNDAY, c.weekStart)
    }

    @Test
    fun `非法设置回退到安全值`() {
        val c = DayClock.from(AppSettings(timezone = "Not/AZone", startOfDay = 99, weekStart = 0))
        assertEquals(ZoneId.systemDefault(), c.zone)
        assertEquals(23, c.startOfDayHour)
        assertEquals(DayOfWeek.MONDAY, c.weekStart)
    }

    @Test
    fun `today使用注入时钟`() {
        val fixed = Clock.fixed(Instant.parse("2026-10-16T19:00:00Z"), ZoneId.of("UTC")) // 上海 10-17 03:00
        val c = DayClock(zone = shanghai, startOfDayHour = 4, weekStart = DayOfWeek.MONDAY, clock = fixed)
        assertEquals(day(2026, 10, 16), c.today())
    }
}
