package com.gh.daily_trace

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.gh.daily_trace.data.DailyTraceDatabase
import com.gh.daily_trace.data.model.AppSettings
import com.gh.daily_trace.data.model.CheckRecord
import com.gh.daily_trace.data.model.DayNote
import com.gh.daily_trace.data.repository.DailyTraceRepository
import com.gh.daily_trace.domain.time.DayClock
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** M9-1 搜索日期范围（Robolectric）：DAO / Repository 的闭区间过滤与单边范围。 */
@RunWith(AndroidJUnit4::class)
class M9SearchRangeTest {

    private lateinit var db: DailyTraceDatabase
    private lateinit var repo: DailyTraceRepository

    private val today = DayClock.from(AppSettings()).today()

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            DailyTraceDatabase::class.java,
        ).allowMainThreadQueries().build()
        repo = DailyTraceRepository(
            checkItemDao = db.checkItemDao(),
            checkRecordDao = db.checkRecordDao(),
            dayNoteDao = db.dayNoteDao(),
            groupDao = db.groupDao(),
            reminderDao = db.reminderDao(),
            filterViewDao = db.filterViewDao(),
            frequencyRuleDao = db.frequencyRuleDao(),
            appSettingsDao = db.appSettingsDao(),
        )
    }

    @After
    fun tearDown() = db.close()

    private fun record(note: String, date: Long, name: String = "Bool1") = CheckRecord(
        itemId = 1,
        date = date,
        value = 1.0,
        note = note,
        itemNameSnapshot = name,
        itemColorSnapshot = 1,
        itemIconSnapshot = "✅",
    )

    @Test
    fun recordsFilterByClosedRange() = runBlocking {
        listOf(today - 10, today - 3, today).forEach { repo.insertRecord(record("batch", it)) }

        assertEquals(3, repo.searchRecords("batch").size)

        val mid = repo.searchRecords("batch", today - 5, today - 1)
        assertEquals(1, mid.size)
        assertEquals(today - 3, mid.single().date)

        // 两端含端点
        assertEquals(2, repo.searchRecords("batch", today - 3, today).size)
        // 单边范围
        assertEquals(1, repo.searchRecords("batch", today, null).size)
        assertEquals(1, repo.searchRecords("batch", null, today - 10).size)
        // 范围外
        assertEquals(0, repo.searchRecords("batch", today + 1, null).size)
    }

    @Test
    fun dayNotesFilterByClosedRange() = runBlocking {
        listOf(today - 10, today - 3, today).forEach {
            repo.saveDayNote(DayNote(date = it, note = "day note"))
        }

        assertEquals(3, repo.searchDayNotes("day").size)
        val mid = repo.searchDayNotes("day", today - 4, today - 2)
        assertEquals(1, mid.size)
        assertEquals(today - 3, mid.single().date)
        assertEquals(0, repo.searchDayNotes("day", today + 1, null).size)
    }

    @Test
    fun queryAndRangeCombine() = runBlocking {
        repo.insertRecord(record("batch", today - 3, name = "Count1"))
        repo.insertRecord(record("batch", today, name = "Dur1"))
        repo.insertRecord(record("other", today - 3, name = "Dur1"))

        // 名称命中但范围外（只查今天）
        assertEquals(0, repo.searchRecords("Count1", today, null).size)
        // 名称命中且范围含目标日
        assertEquals(1, repo.searchRecords("Count1", today - 3, today - 3).size)
    }
}
