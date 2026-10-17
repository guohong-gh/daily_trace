package com.gh.daily_trace

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.gh.daily_trace.data.DailyTraceDatabase
import com.gh.daily_trace.data.model.CheckItem
import com.gh.daily_trace.data.model.CheckType
import com.gh.daily_trace.data.model.DayFilter
import com.gh.daily_trace.data.model.DayNote
import com.gh.daily_trace.data.model.FilterLogic
import com.gh.daily_trace.data.model.FilterMode
import com.gh.daily_trace.data.model.FilterView
import com.gh.daily_trace.data.model.Group
import com.gh.daily_trace.data.repository.DailyTraceRepository
import com.gh.daily_trace.domain.checkin.BackfillPolicy
import com.gh.daily_trace.domain.checkin.CheckInPlanner
import com.gh.daily_trace.domain.checkin.applyPlan
import com.gh.daily_trace.domain.filter.FilterMatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * M2 端到端流程：补卡（BackfillPolicy + Planner）→ 日期备注 → 日历区间标记 → 搜索 → 筛选视图。
 * 内存数据库 + 真实 Repository/DAO，等价于设备上的数据链路。
 */
@RunWith(AndroidJUnit4::class)
class M2FlowTest {

    private lateinit var db: DailyTraceDatabase
    private lateinit var repo: DailyTraceRepository

    private val today = 20700L
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

    private suspend fun insertItem(
        name: String,
        type: CheckType,
        unit: String = "",
        groupId: Long? = null,
    ): CheckItem {
        val id = repo.insertItem(
            CheckItem(name = name, color = 1, icon = "✅", type = type, unit = unit, groupId = groupId)
        )
        return repo.getItem(id)!!
    }

    @Test
    fun backfillWritesToPastDateAndMarksFlag() = runBlocking {
        val item = insertItem("跑步", CheckType.BOOLEAN)
        val backfillDate = today - 3

        assertTrue(BackfillPolicy.canCheckIn(backfillDate, today, maxBackfillDays = 7))
        val isBackfill = BackfillPolicy.isBackfill(backfillDate, today)
        assertTrue(isBackfill)
        applyPlan(
            repo,
            CheckInPlanner.toggleBoolean(item, backfillDate, emptyList(), tz, isBackfill, now = 1_000L),
        )

        val records = repo.getRecordsOfItemOn(item.id, backfillDate)
        assertEquals(1, records.size)
        assertTrue(records[0].isBackfill)
        assertEquals(backfillDate, records[0].date)
        // 今天不受影响
        assertTrue(repo.getRecordsOfItemOn(item.id, today).isEmpty())
    }

    @Test
    fun backfillBeyondLimitIsRejectedByPolicy() {
        assertFalse(BackfillPolicy.canCheckIn(today - 8, today, maxBackfillDays = 7))
        assertFalse(BackfillPolicy.canCheckIn(today - 1, today, maxBackfillDays = 0))
        assertTrue(BackfillPolicy.canCheckIn(today, today, maxBackfillDays = 0))
    }

    @Test
    fun dayNoteSaveOverwriteAndClear() = runBlocking {
        repo.saveDayNote(DayNote(date = today, note = "第一天"))
        assertEquals("第一天", repo.getDayNote(today)!!.note)

        val created = repo.getDayNote(today)!!.createdAt
        repo.saveDayNote(
            DayNote(date = today, note = "改", createdAt = created, updatedAt = created + 1)
        )
        val updated = repo.getDayNote(today)!!
        assertEquals("改", updated.note)
        assertEquals(created, updated.createdAt)

        repo.deleteDayNote(today)
        assertNull(repo.getDayNote(today))
    }

    @Test
    fun calendarRangeReturnsMarksData() = runBlocking {
        val item = insertItem("喝水", CheckType.COUNT, unit = "杯")
        applyPlan(
            repo,
            CheckInPlanner.addValue(item, today - 1, emptyList(), 2.0, "", tz, isBackfill = true, now = 1_000L),
        )
        repo.saveDayNote(DayNote(date = today, note = "备注"))

        val datesWithRecords = repo.getRecordsBetween(today - 30, today).map { it.date }.toSet()
        assertEquals(setOf(today - 1), datesWithRecords)
        val datesWithNotes = repo.observeDayNotesBetween(today - 30, today).first().map { it.date }.toSet()
        assertEquals(setOf(today), datesWithNotes)
    }

    @Test
    fun searchHitsRecordNoteAndDayNote() = runBlocking {
        val item = insertItem("阅读", CheckType.DURATION, unit = "分钟")
        applyPlan(
            repo,
            CheckInPlanner.addValue(item, today, emptyList(), 30.0, "三体第 2 章", tz, false, now = 1_000L),
        )
        repo.saveDayNote(DayNote(date = today - 1, note = "今天读了三体"))

        val recordHits = repo.searchRecords("三体")
        assertEquals(1, recordHits.size)
        assertEquals(item.id, recordHits[0].itemId)

        val noteHits = repo.searchDayNotes("三体")
        assertEquals(1, noteHits.size)
        assertEquals(today - 1, noteHits[0].date)
    }

    @Test
    fun filterViewSavedAndMatchesByGroup() = runBlocking {
        val groupId = repo.insertGroup(Group(name = "健康", color = 1))
        val inGroup = insertItem("跑步", CheckType.BOOLEAN, groupId = groupId)
        val other = insertItem("看剧", CheckType.BOOLEAN)

        repo.saveFilterView(
            FilterView(
                name = "健康组",
                mode = FilterMode.SELECTED,
                groupIds = listOf(groupId),
                logic = FilterLogic.ANY,
                dayFilter = DayFilter.ALL,
            )
        )
        val view = repo.filterViews.first().single()

        assertTrue(FilterMatcher.itemMatches(inGroup, view))
        assertFalse(FilterMatcher.itemMatches(other, view))

        repo.deleteFilterView(view.id)
        assertTrue(repo.filterViews.first().isEmpty())
    }
}
