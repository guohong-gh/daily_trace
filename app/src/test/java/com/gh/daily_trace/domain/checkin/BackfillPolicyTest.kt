package com.gh.daily_trace.domain.checkin

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackfillPolicyTest {

    @Test
    fun `今天允许且非补卡`() {
        assertTrue(BackfillPolicy.canCheckIn(100, today = 100, maxBackfillDays = 0))
        assertFalse(BackfillPolicy.isBackfill(100, today = 100))
    }

    @Test
    fun `补卡上限内允许且标记补卡`() {
        assertTrue(BackfillPolicy.canCheckIn(93, today = 100, maxBackfillDays = 7)) // 恰好 7 天前
        assertTrue(BackfillPolicy.isBackfill(93, today = 100))
    }

    @Test
    fun `超出补卡上限不允许`() {
        assertFalse(BackfillPolicy.canCheckIn(92, today = 100, maxBackfillDays = 7))
    }

    @Test
    fun `上限为0时禁止补卡但今天可用`() {
        assertFalse(BackfillPolicy.canCheckIn(99, today = 100, maxBackfillDays = 0))
        assertTrue(BackfillPolicy.canCheckIn(100, today = 100, maxBackfillDays = 0))
    }

    @Test
    fun `未来日期不允许`() {
        assertFalse(BackfillPolicy.canCheckIn(101, today = 100, maxBackfillDays = 30))
    }

    @Test
    fun `计划仅面向未来日期`() {
        assertTrue(BackfillPolicy.canPlan(101, today = 100))
        assertFalse(BackfillPolicy.canPlan(100, today = 100))
        assertFalse(BackfillPolicy.canPlan(99, today = 100))
    }
}
