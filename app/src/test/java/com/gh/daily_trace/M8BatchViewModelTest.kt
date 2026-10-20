package com.gh.daily_trace

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.gh.daily_trace.data.DailyTraceDatabase
import com.gh.daily_trace.data.model.AppSettings
import com.gh.daily_trace.data.model.CheckItem
import com.gh.daily_trace.data.model.CheckRecord
import com.gh.daily_trace.data.model.CheckType
import com.gh.daily_trace.data.model.MultiRecordRule
import com.gh.daily_trace.data.model.RecordStatus
import com.gh.daily_trace.data.repository.DailyTraceRepository
import com.gh.daily_trace.domain.time.DayClock
import com.gh.daily_trace.ui.datedetail.DateDetailViewModel
import com.gh.daily_trace.ui.today.TodayViewModel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * M8-3 批量操作 ViewModel 级编排：跳过规则（已完成布尔 / 不可批量）、
 * 计数追加、补卡标记与未来计划，直接驱动 TodayViewModel / DateDetailViewModel。
 */
@RunWith(AndroidJUnit4::class)
class M8BatchViewModelTest {

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

    private suspend fun insertItem(
        type: CheckType,
        target: Double? = null,
        rule: MultiRecordRule = MultiRecordRule.KEEP_MULTIPLE,
    ): Long = repo.insertItem(
        CheckItem(
            name = "选项", color = 1, icon = "✅", type = type, unit = "次",
            targetValue = target, multiRecordRule = rule,
        )
    )

    private fun record(
        itemId: Long,
        value: Double?,
        date: Long,
        status: RecordStatus = RecordStatus.DONE,
        createdAt: Long = 1L,
    ) = CheckRecord(
        itemId = itemId, date = date, value = value, unit = "次", note = "", status = status,
        createdAt = createdAt, updatedAt = createdAt,
        itemNameSnapshot = "选项", itemColorSnapshot = 1, itemIconSnapshot = "✅",
    )

    @Test
    fun todayBatchSkipsDoneBooleanAndUnbatchableValue() = runBlocking {
        val boolId = insertItem(CheckType.BOOLEAN)
        val countId = insertItem(CheckType.COUNT)
        val valueId = insertItem(CheckType.VALUE) // 无目标值、无历史 → 不可批量

        repo.insertRecord(record(boolId, 1.0, today))

        val result = TodayViewModel(repo).batchCheckIn(setOf(boolId, countId, valueId))

        assertEquals(1, result.recorded)
        assertEquals(2, result.skipped)

        val records = repo.getRecordsOn(today)
        assertEquals(1, records.count { it.itemId == boolId })  // 已完成布尔项不重复写入、不反选
        assertEquals(1, records.count { it.itemId == countId }) // 计数 +1 落一条
        assertEquals(0, records.count { it.itemId == valueId }) // 不可批量项跳过
    }

    @Test
    fun todayBatchCountAppendsWhileKeepMultiple() = runBlocking {
        val countId = insertItem(CheckType.COUNT)
        repo.insertRecord(record(countId, 2.0, today, createdAt = 1L))

        val result = TodayViewModel(repo).batchCheckIn(setOf(countId))

        assertEquals(1, result.recorded)
        assertEquals(0, result.skipped)
        val values = repo.getRecordsOn(today).filter { it.itemId == countId }.mapNotNull { it.value }.sorted()
        assertEquals(listOf(1.0, 2.0), values)
    }

    @Test
    fun dateDetailBatchSkipsDoneBooleanAndBackfillsValue() = runBlocking {
        val past = today - 1
        val boolId = insertItem(CheckType.BOOLEAN)
        val durId = insertItem(CheckType.DURATION, target = 30.0)

        repo.insertRecord(record(boolId, 1.0, past))

        val result = DateDetailViewModel(repo, past).batchAddRecords(setOf(boolId, durId))

        assertEquals(1, result.recorded)
        assertEquals(1, result.skipped)

        val records = repo.getRecordsOn(past)
        assertEquals(1, records.count { it.itemId == boolId })
        val dur = records.first { it.itemId == durId }
        assertEquals(30.0, dur.value!!, 0.0)
        assertEquals(RecordStatus.DONE, dur.status)
        assertTrue(dur.isBackfill)
    }

    @Test
    fun dateDetailBatchCreatesPlannedOnFutureDate() = runBlocking {
        val future = today + 1
        val valueId = insertItem(CheckType.VALUE, target = 8.0)

        val result = DateDetailViewModel(repo, future).batchAddRecords(setOf(valueId))

        assertEquals(1, result.recorded)
        assertEquals(0, result.skipped)
        val record = repo.getRecordsOn(future).first { it.itemId == valueId }
        assertEquals(8.0, record.value!!, 0.0)
        assertEquals(RecordStatus.PLANNED, record.status)
        assertFalse(record.isBackfill)
    }
}
