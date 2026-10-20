package com.gh.daily_trace.domain.summary

import com.gh.daily_trace.data.model.CheckItem
import com.gh.daily_trace.data.model.CheckRecord
import com.gh.daily_trace.data.model.CheckType
import com.gh.daily_trace.data.model.RecordStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

/** M9-3 每日汇总口径：在册选项为分母，当天 DONE 去重为分子。 */
class DailySummaryTest {

    private val zone = ZoneId.of("Asia/Shanghai")

    private fun item(
        id: Long,
        enabled: Boolean = true,
        archived: Boolean = false,
        deleted: Boolean = false,
    ) = CheckItem(
        id = id,
        name = "选项$id",
        color = 0,
        type = CheckType.BOOLEAN,
        enabled = enabled,
        archived = archived,
        deletedAt = if (deleted) 1L else null,
    )

    private fun record(itemId: Long, status: RecordStatus = RecordStatus.DONE) = CheckRecord(
        itemId = itemId,
        date = 20_000L,
        status = status,
        itemNameSnapshot = "选项$itemId",
        itemColorSnapshot = 0,
        itemIconSnapshot = "",
    )

    @Test
    fun countsOnlyActiveItemsAndDistinctDoneRecords() {
        val items = listOf(
            item(1),
            item(2, enabled = false),
            item(3, archived = true),
            item(4, deleted = true),
            item(5),
        )
        val records = listOf(
            record(1),
            record(1), // 同一天多次只算一次
            record(1, RecordStatus.PARTIAL),
            record(2), // 已停用
            record(3), // 已归档
            record(4), // 已删除
            record(5, RecordStatus.PLANNED), // 计划中不算完成
        )

        val summary = buildDailySummary(items, records)

        assertEquals(1, summary.done)
        assertEquals(2, summary.total)
        assertEquals(1, summary.pending)
        assertFalse(summary.allDone)
    }

    @Test
    fun allDoneWhenEveryActiveItemHasDoneRecord() {
        val summary = buildDailySummary(
            listOf(item(1), item(2)),
            listOf(record(1), record(2), record(2)),
        )

        assertEquals(2, summary.done)
        assertEquals(2, summary.total)
        assertEquals(0, summary.pending)
        assertTrue(summary.allDone)
    }

    @Test
    fun messageCoversEmptyAllDoneAndPending() {
        assertEquals("今天还没有可打卡的选项", summaryMessage(DailySummary(done = 0, total = 0)))
        assertTrue(summaryMessage(DailySummary(done = 2, total = 2)).contains("全部完成"))
        assertEquals(
            "今天已打卡 2 / 6 项，还有 4 项未完成",
            summaryMessage(DailySummary(done = 2, total = 6)),
        )
    }

    @Test
    fun nextSummaryIsTodayWhenTimeNotPassed() {
        val now = Instant.parse("2026-10-20T04:00:00Z").toEpochMilli() // 12:00 +08
        assertEquals(
            Instant.parse("2026-10-20T13:00:00Z").toEpochMilli(), // 21:00 +08
            nextSummaryMillis(now, 21 * 60, zone),
        )
    }

    @Test
    fun nextSummaryMovesToTomorrowWhenTimePassedOrEqual() {
        val at2100 = Instant.parse("2026-10-20T13:00:00Z").toEpochMilli()
        assertEquals(
            Instant.parse("2026-10-21T13:00:00Z").toEpochMilli(),
            nextSummaryMillis(at2100, 21 * 60, zone),
        )

        val at2200 = Instant.parse("2026-10-20T14:00:00Z").toEpochMilli()
        assertEquals(
            Instant.parse("2026-10-21T13:00:00Z").toEpochMilli(),
            nextSummaryMillis(at2200, 21 * 60, zone),
        )
    }

    @Test
    fun nextSummaryHandlesMidnight() {
        val lateNight = Instant.parse("2026-10-20T15:59:00Z").toEpochMilli() // 23:59 +08
        assertEquals(
            Instant.parse("2026-10-20T16:00:00Z").toEpochMilli(), // 次日 00:00 +08
            nextSummaryMillis(lateNight, 0, zone),
        )
    }
}
