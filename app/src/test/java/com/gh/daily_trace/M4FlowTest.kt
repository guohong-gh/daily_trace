package com.gh.daily_trace

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.gh.daily_trace.data.DailyTraceDatabase
import com.gh.daily_trace.data.model.CheckItem
import com.gh.daily_trace.data.model.CheckType
import com.gh.daily_trace.data.model.FrequencyRule
import com.gh.daily_trace.data.model.FrequencyType
import com.gh.daily_trace.data.repository.DailyTraceRepository
import com.gh.daily_trace.domain.checkin.CheckInPlanner
import com.gh.daily_trace.domain.checkin.applyPlan
import com.gh.daily_trace.domain.stats.StatsCalculator
import com.gh.daily_trace.domain.time.DayClock
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * M4 端到端：真实打卡（含补卡）落库后，统计口径与预期一致。
 * 内存数据库 + 真实 Repository/Planner，等价于设备上的数据链路。
 */
@RunWith(AndroidJUnit4::class)
class M4FlowTest {

    private lateinit var db: DailyTraceDatabase
    private lateinit var repo: DailyTraceRepository

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

    private suspend fun insertItem(name: String): CheckItem {
        val id = repo.insertItem(CheckItem(name = name, color = 1, icon = "跑", type = CheckType.BOOLEAN))
        return repo.getItem(id)!!
    }

    private suspend fun checkIn(item: CheckItem, date: Long, isBackfill: Boolean = false) {
        val settings = repo.getSettings()
        val existing = repo.getRecordsOfItemOn(item.id, date)
        applyPlan(
            repo,
            CheckInPlanner.toggleBoolean(
                item = item,
                date = date,
                existing = existing,
                timezone = settings.timezone,
                isBackfill = isBackfill,
                now = System.currentTimeMillis(),
            ),
        )
    }

    @Test
    fun checkInsAndBackfillsFeedStats() = runBlocking {
        val settings = repo.getSettings()
        val today = DayClock.from(settings).today()
        val item = insertItem("跑步")

        for (i in 1..5) checkIn(item, today - i, isBackfill = true)
        checkIn(item, today)

        val byDay = repo.getAllRecords().groupBy { it.date }
        val rate = StatsCalculator.rate(item.id, null, byDay, today - 5, today, settings.partialCountsAsDone)
        assertEquals(6, rate.doneDays)
        assertEquals(6, rate.expectedDays)

        val streaks = StatsCalculator.streaks(
            itemId = item.id,
            rule = null,
            recordsByDay = byDay,
            today = today,
            partialCountsAsDone = settings.partialCountsAsDone,
            sinceDay = today - 5,
        )
        assertEquals(6, streaks.current)
        assertEquals(6, streaks.best)
    }

    @Test
    fun skippedRestDaysBridgeStreak() = runBlocking {
        val settings = repo.getSettings()
        val today = DayClock.from(settings).today()
        val item = insertItem("冥想")

        checkIn(item, today - 4)
        checkIn(item, today - 3)
        // 第 3 天设为休息日（跳过）
        repo.saveFrequency(
            FrequencyRule(
                itemId = item.id,
                type = FrequencyType.DAILY,
                allowSkip = true,
                skipDates = listOf(today - 2),
            )
        )
        checkIn(item, today - 1)
        checkIn(item, today)

        val byDay = repo.getAllRecords().groupBy { it.date }
        val rule = repo.getFrequencyOf(item.id)
        val streaks = StatsCalculator.streaks(
            itemId = item.id,
            rule = rule,
            recordsByDay = byDay,
            today = today,
            partialCountsAsDone = settings.partialCountsAsDone,
            sinceDay = today - 4,
        )
        assertEquals(4, streaks.current) // 跳过日桥接，只计打卡日
        val rate = StatsCalculator.rate(item.id, rule, byDay, today - 4, today, settings.partialCountsAsDone)
        assertEquals(4, rate.doneDays)
        assertEquals(4, rate.expectedDays) // 跳过日不计入期望
    }
}
