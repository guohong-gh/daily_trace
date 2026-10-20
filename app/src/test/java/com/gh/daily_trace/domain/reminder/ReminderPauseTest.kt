package com.gh.daily_trace.domain.reminder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** M9-2 提醒暂停口径：严格晚于当前时刻才算暂停中。 */
class ReminderPauseTest {

    private val now = 1_000_000L

    @Test
    fun nullIsNotPaused() {
        assertFalse(ReminderPause.isPaused(null, now))
        assertNull(ReminderPause.activeUntil(null, now))
    }

    @Test
    fun equalAndPastAreNotPaused() {
        assertFalse(ReminderPause.isPaused(now, now))
        assertFalse(ReminderPause.isPaused(now - 1, now))
        assertNull(ReminderPause.activeUntil(now, now))
        assertNull(ReminderPause.activeUntil(now - 1, now))
    }

    @Test
    fun futureIsPaused() {
        assertTrue(ReminderPause.isPaused(now + 1, now))
        assertEquals(now + 1, ReminderPause.activeUntil(now + 1, now))
    }

    @Test
    fun validUntilRequiresFuture() {
        assertFalse(ReminderPause.isValidUntil(now, now))
        assertFalse(ReminderPause.isValidUntil(now - 1, now))
        assertTrue(ReminderPause.isValidUntil(now + 60_000, now))
    }
}
