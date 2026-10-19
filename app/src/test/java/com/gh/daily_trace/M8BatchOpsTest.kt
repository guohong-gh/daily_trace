package com.gh.daily_trace

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.gh.daily_trace.data.DailyTraceDatabase
import com.gh.daily_trace.data.model.CheckItem
import com.gh.daily_trace.data.model.CheckRecord
import com.gh.daily_trace.data.model.CheckType
import com.gh.daily_trace.data.model.RecordStatus
import com.gh.daily_trace.data.repository.DailyTraceRepository
import com.gh.daily_trace.domain.checkin.BatchCheckIn
import com.gh.daily_trace.domain.checkin.CheckInPlanner
import com.gh.daily_trace.domain.checkin.applyPlan
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * M8-3 批量操作数据链路：批量改备注 / 批量删除 / 「截至日期最近一条记录」查询，
 * 以及批量取值口径在真实数据链（Repository + DAO + Planner）上的表现。
 */
@RunWith(AndroidJUnit4::class)
class M8BatchOpsTest {

    private lateinit var db: DailyTraceDatabase
    private lateinit var repo: DailyTraceRepository

    private val date = 20700L
    private val tz = "Asia/Shanghai"

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

    private suspend fun insertItem(type: CheckType = CheckType.BOOLEAN, target: Double? = null): Long =
        repo.insertItem(CheckItem(name = "选项", color = 1, icon = "✅", type = type, unit = "次", targetValue = target))

    private fun record(itemId: Long, value: Double?, createdAt: Long, date: Long = this.date) = CheckRecord(
        itemId = itemId, date = date, value = value, unit = "次", note = "", status = RecordStatus.DONE,
        createdAt = createdAt, updatedAt = createdAt,
        itemNameSnapshot = "选项", itemColorSnapshot = 1, itemIconSnapshot = "✅",
    )

    // ---------- 批量改备注 ----------

    @Test
    fun batchNoteUpdateOnlyTouchesSelectedRecords() = runBlocking {
        val itemId = insertItem()
        val now = System.currentTimeMillis()
        val id1 = repo.insertRecord(record(itemId, 1.0, now))
        val id2 = repo.insertRecord(record(itemId, 1.0, now + 1))
        val id3 = repo.insertRecord(record(itemId, 1.0, now + 2))

        repo.updateRecordsNote(listOf(id1, id3), "批量备注", now + 100)

        val records = repo.getRecordsOn(date).associateBy { it.id }
        assertEquals("批量备注", records.getValue(id1).note)
        assertEquals("", records.getValue(id2).note)
        assertEquals("批量备注", records.getValue(id3).note)
        assertEquals(now + 100, records.getValue(id1).updatedAt)
        assertEquals(now + 1, records.getValue(id2).updatedAt)

        // 空串 = 清除所选备注
        repo.updateRecordsNote(listOf(id1, id3), "", now + 200)
        val after = repo.getRecordsOn(date).associateBy { it.id }
        assertEquals("", after.getValue(id1).note)
        assertEquals("", after.getValue(id3).note)
    }

    // ---------- 批量删除 ----------

    @Test
    fun batchDeleteRemovesOnlySelectedRecords() = runBlocking {
        val itemId = insertItem()
        val id1 = repo.insertRecord(record(itemId, 1.0, 0))
        val id2 = repo.insertRecord(record(itemId, 1.0, 1))
        val id3 = repo.insertRecord(record(itemId, 1.0, 2))

        repo.deleteRecords(listOf(id1, id3))

        assertEquals(listOf(id2), repo.getRecordsOn(date).map { it.id })
    }

    // ---------- 截至日期的最近一条记录 ----------

    @Test
    fun latestRecordUpToDatePicksMostRecentWithinBoundary() = runBlocking {
        val itemId = insertItem(type = CheckType.DURATION)
        repo.insertRecord(record(itemId, 30.0, 0, date = date - 2))
        repo.insertRecord(record(itemId, 45.0, 1, date = date - 1))
        repo.insertRecord(record(itemId, 99.0, 2, date = date + 1))

        val latest = repo.getLatestRecordUpTo(itemId, date)
        assertEquals(45.0, latest!!.value!!, 0.0)

        assertNull(repo.getLatestRecordUpTo(itemId, date - 5))
    }

    // ---------- 批量取值口径在真实数据链上的表现 ----------

    @Test
    fun batchValueFallsBackToLatestRecordValue() = runBlocking {
        val itemId = insertItem(type = CheckType.DURATION)
        repo.insertRecord(record(itemId, 60.0, 0, date = date - 1))
        val item = repo.getItem(itemId)!!
        val latest = repo.getLatestRecordUpTo(item.id, date)
        assertEquals(60.0, BatchCheckIn.valueFor(item, latest)!!, 0.0)
    }

    @Test
    fun batchRecordWritesThroughSinglePlannerPlan() = runBlocking {
        val itemId = insertItem(type = CheckType.BOOLEAN)
        val item = repo.getItem(itemId)!!
        val plan = CheckInPlanner.addValue(item, date, emptyList(), 1.0, note = "", timezone = tz, isBackfill = false, now = 10L)
        applyPlan(repo, plan)

        val records = repo.getRecordsOn(date)
        assertEquals(1, records.size)
        assertEquals(RecordStatus.DONE, records[0].status)
        assertEquals(1.0, records[0].value!!, 0.0)
    }
}
