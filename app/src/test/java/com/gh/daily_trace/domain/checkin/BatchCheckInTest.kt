package com.gh.daily_trace.domain.checkin

import com.gh.daily_trace.data.model.CheckItem
import com.gh.daily_trace.data.model.CheckRecord
import com.gh.daily_trace.data.model.CheckType
import com.gh.daily_trace.data.model.RecordStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * M8-3 批量记录取值口径（默认值直记）：
 * 布尔/计数 → 1；时长/数值 → 目标值 → 最近一次记录值；皆无 → 不可批量。
 */
class BatchCheckInTest {

    private val date = 20700L

    private fun item(type: CheckType, targetValue: Double? = null) = CheckItem(
        id = 7, name = "跑步", color = 0x11, icon = "🏃", type = type, unit = "分钟", targetValue = targetValue,
    )

    private fun record(value: Double?, status: RecordStatus = RecordStatus.DONE) = CheckRecord(
        id = 1, itemId = 7, date = date, value = value, unit = "分钟", note = "", status = status,
        createdAt = 1L, updatedAt = 1L,
        itemNameSnapshot = "跑步", itemColorSnapshot = 0x11, itemIconSnapshot = "🏃",
    )

    @Test
    fun `布尔型批量取值为 1 且可批量`() {
        assertEquals(1.0, BatchCheckIn.valueFor(item(CheckType.BOOLEAN), null)!!, 0.0)
        assertTrue(BatchCheckIn.canBatch(item(CheckType.BOOLEAN), null))
    }

    @Test
    fun `计数型批量取值为 1`() {
        assertEquals(1.0, BatchCheckIn.valueFor(item(CheckType.COUNT), record(2.0))!!, 0.0)
    }

    @Test
    fun `时长型优先目标值`() {
        assertEquals(30.0, BatchCheckIn.valueFor(item(CheckType.DURATION, targetValue = 30.0), record(60.0))!!, 0.0)
    }

    @Test
    fun `时长型无目标值时沿用最近一次记录值`() {
        assertEquals(60.0, BatchCheckIn.valueFor(item(CheckType.DURATION), record(60.0))!!, 0.0)
        // 部分完成记录同样可作为沿用来源
        assertEquals(20.0, BatchCheckIn.valueFor(item(CheckType.DURATION), record(20.0, RecordStatus.PARTIAL))!!, 0.0)
    }

    @Test
    fun `数值型无目标值且无历史时不可批量`() {
        assertNull(BatchCheckIn.valueFor(item(CheckType.VALUE), null))
        assertFalse(BatchCheckIn.canBatch(item(CheckType.VALUE), null))
    }

    @Test
    fun `最近记录无值时不可批量`() {
        assertNull(BatchCheckIn.valueFor(item(CheckType.VALUE), record(null)))
        assertFalse(BatchCheckIn.canBatch(item(CheckType.VALUE), record(null)))
    }
}
