package com.gh.daily_trace.domain.checkin

import com.gh.daily_trace.data.model.CheckItem
import com.gh.daily_trace.data.model.CheckRecord
import com.gh.daily_trace.data.model.CheckType
import com.gh.daily_trace.data.model.MultiRecordRule
import com.gh.daily_trace.data.model.RecordStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CheckInPlannerTest {

    private val date = 20700L
    private val tz = "Asia/Shanghai"
    private val now = 1_700_000_000_000L

    private fun item(
        type: CheckType = CheckType.COUNT,
        rule: MultiRecordRule = MultiRecordRule.KEEP_MULTIPLE,
        unit: String = "杯",
    ) = CheckItem(id = 7, name = "喝水", color = 0x11, icon = "💧", type = type, unit = unit, multiRecordRule = rule)

    private fun record(
        id: Long,
        value: Double?,
        createdAt: Long = now,
        note: String = "",
        status: RecordStatus = RecordStatus.DONE,
    ) = CheckRecord(
        id = id, itemId = 7, date = date, value = value, unit = "杯", note = note, status = status,
        createdAt = createdAt, updatedAt = createdAt,
        itemNameSnapshot = "喝水", itemColorSnapshot = 0x11, itemIconSnapshot = "💧",
    )

    // ---------- 布尔切换 ----------

    @Test
    fun `布尔型无记录时插入`() {
        val plan = CheckInPlanner.toggleBoolean(item(CheckType.BOOLEAN), date, emptyList(), tz, false, now)
        assertEquals(1, plan.inserts.size)
        assertTrue(plan.updates.isEmpty() && plan.deletes.isEmpty())
        assertEquals(1.0, plan.inserts[0].value!!, 0.0)
        assertEquals("喝水", plan.inserts[0].itemNameSnapshot)
    }

    @Test
    fun `布尔型已有记录时全部取消`() {
        val existing = listOf(record(1, 1.0), record(2, 1.0, createdAt = now + 1))
        val plan = CheckInPlanner.toggleBoolean(item(CheckType.BOOLEAN), date, existing, tz, false, now)
        assertEquals(listOf(1L, 2L), plan.deletes)
        assertTrue(plan.inserts.isEmpty())
    }

    // ---------- KEEP_MULTIPLE ----------

    @Test
    fun `保留多条时始终追加`() {
        val existing = listOf(record(1, 1.0))
        val plan = CheckInPlanner.addValue(item(rule = MultiRecordRule.KEEP_MULTIPLE), date, existing, 1.0, "第二杯", tz, false, now + 10)
        assertEquals(1, plan.inserts.size)
        assertEquals("第二杯", plan.inserts[0].note)
        assertTrue(plan.updates.isEmpty())
    }

    // ---------- MERGE ----------

    @Test
    fun `合并规则更新最新一条并覆盖数值`() {
        val existing = listOf(record(1, 1.0), record(2, 2.0, createdAt = now + 1))
        val plan = CheckInPlanner.addValue(item(rule = MultiRecordRule.MERGE), date, existing, 5.0, "改成5", tz, false, now + 10)
        assertEquals(1, plan.updates.size)
        assertEquals(2L, plan.updates[0].id) // 最新一条
        assertEquals(5.0, plan.updates[0].value!!, 0.0)
        assertEquals("改成5", plan.updates[0].note)
        assertTrue(plan.inserts.isEmpty())
    }

    @Test
    fun `合并规则空值保留原值与原备注`() {
        val existing = listOf(record(1, 3.0, note = "旧备注"))
        val plan = CheckInPlanner.addValue(item(rule = MultiRecordRule.MERGE), date, existing, null, "", tz, false, now + 10)
        assertEquals(1, plan.updates.size)
        assertEquals(3.0, plan.updates[0].value!!, 0.0)
        assertEquals("旧备注", plan.updates[0].note)
    }

    // ---------- ACCUMULATE ----------

    @Test
    fun `累加规则在最新一条上相加`() {
        val existing = listOf(record(1, 1.0), record(2, 2.0, createdAt = now + 1))
        val plan = CheckInPlanner.addValue(item(rule = MultiRecordRule.ACCUMULATE), date, existing, 1.0, "", tz, false, now + 10)
        assertEquals(1, plan.updates.size)
        assertEquals(2L, plan.updates[0].id)
        assertEquals(3.0, plan.updates[0].value!!, 0.0)
    }

    @Test
    fun `累加规则输入为空时数值不变`() {
        val existing = listOf(record(1, 2.0))
        val plan = CheckInPlanner.addValue(item(rule = MultiRecordRule.ACCUMULATE), date, existing, null, "仅备注", tz, false, now + 10)
        assertEquals(2.0, plan.updates[0].value!!, 0.0)
        assertEquals("仅备注", plan.updates[0].note)
    }

    @Test
    fun `累加规则无记录时插入`() {
        val plan = CheckInPlanner.addValue(item(rule = MultiRecordRule.ACCUMULATE), date, emptyList(), 2.0, "", tz, false, now)
        assertEquals(1, plan.inserts.size)
        assertEquals(2.0, plan.inserts[0].value!!, 0.0)
    }

    // ---------- 布尔型 addValue（带备注打卡） ----------

    @Test
    fun `布尔型带备注已有记录仅更新备注`() {
        val existing = listOf(record(1, 1.0, note = "旧"))
        val plan = CheckInPlanner.addValue(item(CheckType.BOOLEAN), date, existing, null, "新备注", tz, false, now + 10)
        assertEquals(1, plan.updates.size)
        assertEquals("新备注", plan.updates[0].note)
        assertEquals(1.0, plan.updates[0].value!!, 0.0)
    }

    // ---------- 状态：部分完成 ----------

    @Test
    fun `部分完成始终追加且状态为PARTIAL`() {
        val existing = listOf(record(1, 1.0))
        val plan = CheckInPlanner.addPartial(item(), date, 0.5, "只做了一半", tz, false, now + 10)
        assertEquals(1, plan.inserts.size)
        assertTrue(plan.updates.isEmpty() && plan.deletes.isEmpty())
        assertEquals(RecordStatus.PARTIAL, plan.inserts[0].status)
        assertEquals(0.5, plan.inserts[0].value!!, 0.0)
        assertEquals("只做了一半", plan.inserts[0].note)
    }

    @Test
    fun `合并规则不覆盖部分完成记录`() {
        val existing = listOf(
            record(1, 1.0),
            record(2, 0.5, createdAt = now + 5, status = RecordStatus.PARTIAL),
        )
        val plan = CheckInPlanner.addValue(item(rule = MultiRecordRule.MERGE), date, existing, 5.0, "", tz, false, now + 10)
        assertEquals(1, plan.updates.size)
        assertEquals(1L, plan.updates[0].id) // 目标是最新一条「完成」，而非更新的部分完成记录
        assertEquals(RecordStatus.DONE, plan.updates[0].status)
    }

    @Test
    fun `累加规则跳过部分完成记录`() {
        val existing = listOf(
            record(1, 1.5),
            record(2, 1.0, createdAt = now + 5, status = RecordStatus.PARTIAL),
        )
        val plan = CheckInPlanner.addValue(item(rule = MultiRecordRule.ACCUMULATE), date, existing, 2.0, "", tz, false, now + 10)
        assertEquals(1L, plan.updates[0].id)
        assertEquals(3.5, plan.updates[0].value!!, 0.0)
    }

    @Test
    fun `布尔切换忽略计划记录且只取消完成记录`() {
        // 仅有计划 → 新增完成记录（不删除计划）
        val planOnly = listOf(record(5, 1.0, status = RecordStatus.PLANNED))
        val insertPlan = CheckInPlanner.toggleBoolean(item(CheckType.BOOLEAN), date, planOnly, tz, false, now)
        assertEquals(1, insertPlan.inserts.size)
        assertTrue(insertPlan.deletes.isEmpty())

        // 完成 + 计划 → 只删除完成记录
        val mixed = listOf(record(1, 1.0), record(5, 1.0, createdAt = now + 5, status = RecordStatus.PLANNED))
        val deletePlan = CheckInPlanner.toggleBoolean(item(CheckType.BOOLEAN), date, mixed, tz, false, now)
        assertEquals(listOf(1L), deletePlan.deletes)
        assertTrue(deletePlan.inserts.isEmpty())
    }

    // ---------- 状态：计划中 ----------

    @Test
    fun `计划无既有计划时插入PLANNED且非补卡`() {
        val existing = listOf(record(1, 1.0))
        val plan = CheckInPlanner.planAhead(item(), date + 2, existing, 30.0, "明天跑", tz, now)
        assertEquals(1, plan.inserts.size)
        assertTrue(plan.updates.isEmpty())
        assertEquals(RecordStatus.PLANNED, plan.inserts[0].status)
        assertEquals(false, plan.inserts[0].isBackfill)
        assertEquals(30.0, plan.inserts[0].value!!, 0.0)
        assertEquals("明天跑", plan.inserts[0].note)
    }

    @Test
    fun `计划同日更新最近一条计划且保留原值`() {
        val existing = listOf(
            record(1, 20.0, createdAt = now, status = RecordStatus.PLANNED),
            record(2, 30.0, createdAt = now + 5, status = RecordStatus.PLANNED),
        )
        val plan = CheckInPlanner.planAhead(item(), date + 2, existing, null, "加量", tz, now + 10)
        assertEquals(1, plan.updates.size)
        assertEquals(2L, plan.updates[0].id)
        assertEquals(30.0, plan.updates[0].value!!, 0.0)
        assertEquals("加量", plan.updates[0].note)
        assertEquals(RecordStatus.PLANNED, plan.updates[0].status)
        assertTrue(plan.inserts.isEmpty())
    }

    // ---------- 删除与快照 ----------

    @Test
    fun `撤销最近一笔按时间取最新`() {
        val existing = listOf(record(1, 1.0), record(2, 2.0, createdAt = now + 5))
        assertEquals(listOf(2L), CheckInPlanner.deleteLatest(existing).deletes)
        assertTrue(CheckInPlanner.deleteLatest(emptyList()).isEmpty)
    }

    @Test
    fun `插入记录携带快照与补卡标记`() {
        val it = item(type = CheckType.DURATION, unit = "分钟")
        val plan = CheckInPlanner.addValue(it, date, emptyList(), 30.0, "", tz, isBackfill = true, now)
        val r = plan.inserts[0]
        assertEquals("喝水", r.itemNameSnapshot)
        assertEquals("💧", r.itemIconSnapshot)
        assertEquals(0x11, r.itemColorSnapshot)
        assertEquals(tz, r.timezone)
        assertTrue(r.isBackfill)
        assertEquals("分钟", r.unit)
    }
}
