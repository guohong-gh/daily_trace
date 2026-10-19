package com.gh.daily_trace

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.gh.daily_trace.data.DailyTraceDatabase
import com.gh.daily_trace.data.model.CheckItem
import com.gh.daily_trace.data.model.CheckRecord
import com.gh.daily_trace.data.model.CheckType
import com.gh.daily_trace.data.model.FrequencyRule
import com.gh.daily_trace.data.model.MultiRecordRule
import com.gh.daily_trace.data.model.RecordStatus
import com.gh.daily_trace.data.repository.DailyTraceRepository
import com.gh.daily_trace.domain.checkin.CheckInPlanner
import com.gh.daily_trace.domain.checkin.applyPlan
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * M1 端到端流程：选项 → 打卡（各 MultiRecordRule）→ 记录落库 → 快照稳定性。
 * 内存数据库 + 真实 Repository/DAO/Planner，等价于设备上的数据链路。
 */
@RunWith(AndroidJUnit4::class)
class CheckInFlowTest {

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

    private suspend fun insertItem(
        name: String,
        type: CheckType,
        rule: MultiRecordRule = MultiRecordRule.KEEP_MULTIPLE,
        unit: String = "",
    ): CheckItem {
        val id = repo.insertItem(
            CheckItem(name = name, color = 1, icon = "✅", type = type, unit = unit, multiRecordRule = rule)
        )
        return repo.getItem(id)!!
    }

    @Test
    fun accumulateFlowMergesIntoOneRecord() = runBlocking {
        val item = insertItem("喝水", CheckType.COUNT, MultiRecordRule.ACCUMULATE, "杯")
        var now = 1_000L
        repeat(3) {
            val existing = repo.getRecordsOfItemOn(item.id, date)
            applyPlan(repo, CheckInPlanner.addValue(item, date, existing, 1.0, "", tz, false, now++))
        }
        val records = repo.getRecordsOfItemOn(item.id, date)
        assertEquals(1, records.size)
        assertEquals(3.0, records[0].value!!, 0.0)
        assertEquals("喝水", records[0].itemNameSnapshot)
    }

    @Test
    fun booleanToggleOnAndOff() = runBlocking {
        val item = insertItem("早起", CheckType.BOOLEAN)

        // 打卡
        var existing = repo.getRecordsOfItemOn(item.id, date)
        applyPlan(repo, CheckInPlanner.toggleBoolean(item, date, existing, tz, false, 1_000L))
        assertEquals(1, repo.getRecordsOfItemOn(item.id, date).size)

        // 取消
        existing = repo.getRecordsOfItemOn(item.id, date)
        applyPlan(repo, CheckInPlanner.toggleBoolean(item, date, existing, tz, false, 2_000L))
        assertTrue(repo.getRecordsOfItemOn(item.id, date).isEmpty())
    }

    @Test
    fun keepMultipleCreatesSeparateRecords() = runBlocking {
        val item = insertItem("深蹲", CheckType.COUNT, MultiRecordRule.KEEP_MULTIPLE, "个")
        var now = 1_000L
        repeat(2) {
            val existing = repo.getRecordsOfItemOn(item.id, date)
            applyPlan(repo, CheckInPlanner.addValue(item, date, existing, 30.0, "", tz, false, now++))
        }
        val records = repo.getRecordsOfItemOn(item.id, date)
        assertEquals(2, records.size)
        assertEquals(60.0, records.sumOf { it.value ?: 0.0 }, 0.0)
    }

    @Test
    fun mergeReplacesLatestValue() = runBlocking {
        val item = insertItem("俯卧撑", CheckType.COUNT, MultiRecordRule.MERGE, "个")
        var existing = repo.getRecordsOfItemOn(item.id, date)
        applyPlan(repo, CheckInPlanner.addValue(item, date, existing, 10.0, "", tz, false, 1_000L))

        existing = repo.getRecordsOfItemOn(item.id, date)
        applyPlan(repo, CheckInPlanner.addValue(item, date, existing, 25.0, "改目标", tz, false, 2_000L))

        val records = repo.getRecordsOfItemOn(item.id, date)
        assertEquals(1, records.size)
        assertEquals(25.0, records[0].value!!, 0.0)
        assertEquals("改目标", records[0].note)
    }

    @Test
    fun snapshotSurvivesItemRenameAndTrash() = runBlocking {
        val item = insertItem("英语听力", CheckType.DURATION, MultiRecordRule.KEEP_MULTIPLE, "分钟")
        applyPlan(repo, CheckInPlanner.addValue(item, date, emptyList(), 30.0, "第一遍", tz, false, 1_000L))

        // 改名 + 移入回收站
        repo.updateItem(item.copy(name = "听力打卡"))
        repo.softDeleteItem(item.id)

        val records = repo.observeRecordsOn(date).first()
        assertEquals(1, records.size)
        assertEquals("英语听力", records[0].itemNameSnapshot)
        assertEquals("✅", records[0].itemIconSnapshot)

        // 回收站中的选项不再出现在打卡列表，但记录仍在
        assertTrue(repo.activeItems.first().none { it.id == item.id })
        assertEquals(1, repo.observeRecordsOfItem(item.id).first().size)
    }

    @Test
    fun deleteLatestUndoesLastRecord() = runBlocking {
        val item = insertItem("单词", CheckType.COUNT, MultiRecordRule.KEEP_MULTIPLE, "个")
        var now = 1_000L
        repeat(2) {
            val existing = repo.getRecordsOfItemOn(item.id, date)
            applyPlan(repo, CheckInPlanner.addValue(item, date, existing, 10.0, "", tz, false, now++))
        }
        val existing = repo.getRecordsOfItemOn(item.id, date)
        applyPlan(repo, CheckInPlanner.deleteLatest(existing))

        val records = repo.getRecordsOfItemOn(item.id, date)
        assertEquals(1, records.size)
        assertEquals(1_000L, records[0].createdAt)
    }

    @Test
    fun recordsAreIsolatedPerDay() = runBlocking {
        val item = insertItem("跑步", CheckType.BOOLEAN)
        applyPlan(repo, CheckInPlanner.toggleBoolean(item, date, emptyList(), tz, false, 1_000L))
        applyPlan(repo, CheckInPlanner.toggleBoolean(item, date + 1, emptyList(), tz, false, 2_000L))

        assertEquals(1, repo.getRecordsOfItemOn(item.id, date).size)
        assertEquals(1, repo.getRecordsOfItemOn(item.id, date + 1).size)
        val all: List<CheckRecord> = repo.observeRecordsBetween(date, date + 1).first().let { it }
        assertEquals(2, all.size)
    }

    @Test
    fun partialPlanAndSkipPersistWithStatus() = runBlocking {
        val item = insertItem("阅读", CheckType.DURATION, MultiRecordRule.KEEP_MULTIPLE, "分钟")

        // 部分完成：追加 PARTIAL 记录（不合并、不覆盖）
        applyPlan(repo, CheckInPlanner.addPartial(item, date, 20.0, "只读了一会", tz, false, 1_000L))
        applyPlan(repo, CheckInPlanner.addPartial(item, date, 10.0, "", tz, false, 2_000L))
        val partials = repo.getRecordsOfItemOn(item.id, date)
        assertEquals(2, partials.size)
        assertTrue(partials.all { it.status == RecordStatus.PARTIAL })

        // 计划：未来日期，非补卡；同日重复创建只更新最近一条计划
        applyPlan(repo, CheckInPlanner.planAhead(item, date + 2, emptyList(), 30.0, "", tz, 3_000L))
        val plans1 = repo.getRecordsOfItemOn(item.id, date + 2)
        applyPlan(repo, CheckInPlanner.planAhead(item, date + 2, plans1, 45.0, "加量", tz, 4_000L))
        val plans2 = repo.getRecordsOfItemOn(item.id, date + 2)
        assertEquals(1, plans2.size)
        assertEquals(RecordStatus.PLANNED, plans2[0].status)
        assertEquals(45.0, plans2[0].value!!, 0.0)
        assertFalse(plans2[0].isBackfill)

        // 跳过：登记到频率规则 skipDates，不影响已落库记录
        repo.saveFrequency(FrequencyRule(itemId = item.id, skipDates = listOf(date + 1)))
        val rule = repo.getFrequencyOf(item.id)!!
        assertTrue(date + 1 in rule.skipDates)
        assertEquals(2, repo.getRecordsOfItemOn(item.id, date).size)
    }
}
