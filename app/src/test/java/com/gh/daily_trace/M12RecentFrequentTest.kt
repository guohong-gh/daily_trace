package com.gh.daily_trace

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.gh.daily_trace.data.DailyTraceDatabase
import com.gh.daily_trace.data.model.AppSettings
import com.gh.daily_trace.data.model.CheckItem
import com.gh.daily_trace.data.model.CheckRecord
import com.gh.daily_trace.data.model.CheckType
import com.gh.daily_trace.data.model.RecordStatus
import com.gh.daily_trace.data.repository.DailyTraceRepository
import com.gh.daily_trace.domain.time.DayClock
import com.gh.daily_trace.ui.today.computeRecentFrequent
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** M12-2 B19「近期常用」：DAO 按 30 天窗口计数排序截断；纯计算 top 3 与门槛显隐。 */
@RunWith(AndroidJUnit4::class)
class M12RecentFrequentTest {

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

    private suspend fun insertItem(name: String, sort: Int): Long = repo.insertItem(
        CheckItem(name = name, color = 1, icon = "✅", type = CheckType.BOOLEAN, sort = sort),
    )

    private fun record(itemId: Long, date: Long, status: RecordStatus = RecordStatus.DONE) =
        CheckRecord(
            itemId = itemId, date = date, value = 1.0, unit = "次", note = "", status = status,
            itemNameSnapshot = "选项", itemColorSnapshot = 1, itemIconSnapshot = "✅",
        )

    private fun item(id: Long, name: String, sort: Int) = CheckItem(
        id = id, name = name, color = 1, icon = "✅", type = CheckType.BOOLEAN, sort = sort,
    )

    private fun usage(vararg pairs: Pair<Long, Int>) =
        pairs.map { (itemId, count) -> com.gh.daily_trace.data.model.ItemUsageCount(itemId, count) }

    @Test
    fun recentUsageCountsWindowOrderingAndLimit() = runBlocking {
        val a = insertItem("A", sort = 0)
        val b = insertItem("B", sort = 1)
        val c = insertItem("C", sort = 2)

        // A：窗口内 3 条；B：窗口内 2 条；C：窗口外全部（不计）
        repo.insertRecords(
            (1..3).map { record(a, today - 5) } + record(b, today) + record(b, today - 29) +
                (1..99).map { record(c, today - 60) },
        )

        val counts = repo.observeRecentUsage(today - 29, today, limit = 2).first()
            .associate { it.itemId to it.useCount }

        assertEquals(3, counts[a])
        assertEquals(2, counts[b])
        assertTrue(c !in counts)
    }

    @Test
    fun computeTop3OrderedByCountThenSort() {
        val items = (1..8).map { item(it.toLong(), "I$it", sort = it) }
        val todayRecords = listOf(
            record(1, today), // I1：1 次
            record(2, today), record(2, today - 1), record(2, today - 2), // I2：3 次
            record(3, today - 3), record(3, today - 4), // I3：2 次
            record(4, today - 1), record(4, today - 2), record(4, today - 3), // I4：3 次
        )
        val u = usage(2L to 3, 4L to 3, 3L to 2, 1L to 1) // DAO 已按次数降序

        val recent = computeRecentFrequent(items, filter = null, u, todayRecords)

        assertEquals(listOf("I2", "I4", "I3"), recent.map { it.name })
    }

    @Test
    fun computeExcludesFilteredOutAndUnused() {
        val items = (1..8).map { item(it.toLong(), "I$it", sort = it) }
        val disabled = items.first().copy(enabled = false)
        val candidate = items.filter { it.id != disabled.id }
        val u = usage(1L to 2, 9L to 5, 3L to 1) // 1=停用、9=不在册，都应剔除

        val recent = computeRecentFrequent(candidate, filter = null, u, emptyList())

        assertEquals(listOf("I3"), recent.map { it.name })
    }

    @Test
    fun computeTakesFewerThanLimitWhenAvailable() {
        val items = (1..8).map { item(it.toLong(), "I$it", sort = it) }
        val u = usage(5L to 1)

        val recent = computeRecentFrequent(items, filter = null, u, emptyList())

        assertEquals(1, recent.size)
        assertEquals("I5", recent.single().name)
    }
}
