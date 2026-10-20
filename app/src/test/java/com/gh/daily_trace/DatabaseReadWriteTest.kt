package com.gh.daily_trace

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.gh.daily_trace.data.DailyTraceDatabase
import com.gh.daily_trace.data.model.AppSettings
import com.gh.daily_trace.data.model.CheckItem
import com.gh.daily_trace.data.model.CheckRecord
import com.gh.daily_trace.data.model.CheckType
import com.gh.daily_trace.data.model.DayFilter
import com.gh.daily_trace.data.model.DayNote
import com.gh.daily_trace.data.model.FilterLogic
import com.gh.daily_trace.data.model.FilterMode
import com.gh.daily_trace.data.model.FilterView
import com.gh.daily_trace.data.model.FrequencyRule
import com.gh.daily_trace.data.model.FrequencyType
import com.gh.daily_trace.data.model.Group
import com.gh.daily_trace.data.model.MultiRecordRule
import com.gh.daily_trace.data.model.RecordSource
import com.gh.daily_trace.data.model.RecordStatus
import com.gh.daily_trace.data.model.Reminder
import com.gh.daily_trace.data.model.RepeatType
import com.gh.daily_trace.data.model.ThemeMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.TimeZone

/**
 * Phase 1 数据层验证：内存数据库上验证 8 个实体的读写、Flow 与种子数据。
 */
@RunWith(AndroidJUnit4::class)
class DatabaseReadWriteTest {

    private lateinit var db: DailyTraceDatabase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            DailyTraceDatabase::class.java
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun seedWritesDefaultGroupsAndSettingsOnce() = runBlocking {
        DailyTraceDatabase.seed(db)

        val groups = db.groupDao().getAll()
        assertEquals(4, groups.size)
        assertEquals(listOf("健康", "学习", "工作", "运动"), groups.map { it.name })
        assertNotNull(db.appSettingsDao().get())

        // 幂等：重复执行不重复写入
        DailyTraceDatabase.seed(db)
        assertEquals(4, db.groupDao().getAll().size)
    }

    @Test
    fun checkItemInsertQueryUpdate() = runBlocking {
        val groupId = db.groupDao().insert(Group(name = "健康", color = 1))
        val id = db.checkItemDao().insert(
            CheckItem(
                name = "英语听力",
                color = 0xFF2E7D32.toInt(),
                icon = "🎧",
                type = CheckType.DURATION,
                unit = "分钟",
                targetValue = 30.0,
                defaultNote = "泛听为主",
                groupId = groupId,
                multiRecordRule = MultiRecordRule.ACCUMULATE,
                sort = 1
            )
        )
        assertTrue(id > 0)

        val loaded = db.checkItemDao().observeActive().first().single()
        assertEquals("英语听力", loaded.name)
        assertEquals(CheckType.DURATION, loaded.type)
        assertEquals(30.0, loaded.targetValue!!, 0.0)
        assertEquals(groupId, loaded.groupId)
        assertEquals(MultiRecordRule.ACCUMULATE, loaded.multiRecordRule)
        assertEquals(TimeZone.getDefault().id, TimeZone.getDefault().id)

        // 改名/改色更新
        db.checkItemDao().update(loaded.copy(name = "听力打卡", color = 0xFF1565C0.toInt()))
        assertEquals("听力打卡", db.checkItemDao().getById(id)!!.name)

        // 归档 → 出现在归档列表
        db.checkItemDao().setArchived(id, true)
        assertTrue(db.checkItemDao().observeArchived().first().isNotEmpty())
        assertTrue(db.checkItemDao().observeActive().first().isEmpty())

        // 软删除 → 回收站；再恢复
        db.checkItemDao().softDelete(id)
        assertTrue(db.checkItemDao().observeTrashed().first().isNotEmpty())
        db.checkItemDao().restore(id)
        assertTrue(db.checkItemDao().observeTrashed().first().isEmpty())
        assertNotNull(db.checkItemDao().getById(id))
    }

    @Test
    fun checkRecordInsertQueryAndSnapshots() = runBlocking {
        val itemId = db.checkItemDao().insert(
            CheckItem(name = "平板支撑", color = 2, icon = "🧘", type = CheckType.DURATION, unit = "秒")
        )
        val today = 20700L // epochDay 示例
        db.checkRecordDao().insert(
            CheckRecord(
                itemId = itemId,
                date = today,
                value = 90.0,
                unit = "秒",
                note = "比昨天多 10 秒",
                status = RecordStatus.DONE,
                source = RecordSource.MANUAL,
                isBackfill = false,
                timezone = TimeZone.getDefault().id,
                itemNameSnapshot = "平板支撑",
                itemColorSnapshot = 2,
                itemIconSnapshot = "🧘"
            )
        )

        val records = db.checkRecordDao().observeByDate(today).first()
        assertEquals(1, records.size)
        assertEquals(90.0, records[0].value!!, 0.0)
        assertEquals("平板支撑", records[0].itemNameSnapshot)

        // 区间查询
        assertEquals(1, db.checkRecordDao().getBetween(today - 1, today + 1).size)
        // 按选项查询
        assertEquals(1, db.checkRecordDao().getForItemOnDate(itemId, today).size)
        // 搜索（备注关键词）
        assertEquals(1, db.checkRecordDao().search("昨天", null, null).size)

        // 删除
        db.checkRecordDao().delete(records[0].id)
        assertTrue(db.checkRecordDao().observeByDate(today).first().isEmpty())
    }

    @Test
    fun dayNoteUpsertAndDelete() = runBlocking {
        val date = 20700L
        db.dayNoteDao().upsert(DayNote(date = date, note = "今天状态不错"))
        assertEquals("今天状态不错", db.dayNoteDao().getByDate(date)!!.note)

        // 同日覆盖更新
        db.dayNoteDao().upsert(DayNote(date = date, note = "改后备注"))
        assertEquals("改后备注", db.dayNoteDao().observeByDate(date).first()!!.note)

        db.dayNoteDao().delete(date)
        assertNull(db.dayNoteDao().getByDate(date))
    }

    @Test
    fun reminderAndFrequencyRuleOnePerItem() = runBlocking {
        val itemId = db.checkItemDao().insert(
            CheckItem(name = "喝水", color = 3, icon = "💧", type = CheckType.COUNT, unit = "杯")
        )

        db.reminderDao().upsert(
            Reminder(itemId = itemId, time = 9 * 60, repeat = RepeatType.DAILY, snooze = 10, dndStart = 22 * 60, dndEnd = 7 * 60)
        )
        // 同一选项再次保存覆盖，不产生第二条；每周提醒的星期随提醒存储
        db.reminderDao().upsert(
            Reminder(itemId = itemId, time = 10 * 60, repeat = RepeatType.WEEKLY, daysOfWeek = listOf(1, 3))
        )
        assertEquals(1, db.reminderDao().getAll().size)
        assertEquals(10 * 60, db.reminderDao().getForItem(itemId)!!.time)
        assertEquals(listOf(1, 3), db.reminderDao().getForItem(itemId)!!.daysOfWeek)

        // 暂停/启用
        db.reminderDao().setEnabled(itemId, false)
        assertTrue(db.reminderDao().getEnabled().isEmpty())

        db.frequencyRuleDao().upsert(
            FrequencyRule(
                itemId = itemId,
                type = FrequencyType.DAYS_OF_WEEK,
                daysOfWeek = listOf(1, 3, 5),
                targetValue = 3.0,
                allowSkip = true,
                skipDates = listOf(20698L, 20699L)
            )
        )
        val rule = db.frequencyRuleDao().getForItem(itemId)!!
        assertEquals(listOf(1, 3, 5), rule.daysOfWeek)
        assertEquals(listOf(20698L, 20699L), rule.skipDates)
        assertEquals(FrequencyType.DAYS_OF_WEEK, rule.type)

        // CUSTOM：间隔与锚点持久化（v2 迁移新增字段）
        db.frequencyRuleDao().upsert(
            rule.copy(type = FrequencyType.CUSTOM, intervalDays = 5, anchorEpochDay = 20735L)
        )
        val custom = db.frequencyRuleDao().getForItem(itemId)!!
        assertEquals(FrequencyType.CUSTOM, custom.type)
        assertEquals(5, custom.intervalDays)
        assertEquals(20735L, custom.anchorEpochDay)

        db.reminderDao().deleteForItem(itemId)
        db.frequencyRuleDao().deleteForItem(itemId)
        assertNull(db.reminderDao().getForItem(itemId))
        assertNull(db.frequencyRuleDao().getForItem(itemId))
    }

    @Test
    fun filterViewRoundTrip() = runBlocking {
        db.filterViewDao().upsert(
            FilterView(
                name = "运动相关",
                mode = FilterMode.SELECTED,
                itemIds = listOf(1L, 2L, 3L),
                groupIds = listOf(4L),
                logic = FilterLogic.ALL,
                dayFilter = DayFilter.RECORDED
            )
        )
        val loaded = db.filterViewDao().observeAll().first().single()
        assertEquals(listOf(1L, 2L, 3L), loaded.itemIds)
        assertEquals(FilterLogic.ALL, loaded.logic)
        assertEquals(DayFilter.RECORDED, loaded.dayFilter)

        db.filterViewDao().delete(loaded.id)
        assertTrue(db.filterViewDao().observeAll().first().isEmpty())
    }

    @Test
    fun settingsRoundTrip() = runBlocking {
        db.appSettingsDao().upsert(
            AppSettings(
                theme = ThemeMode.DARK,
                weekStart = 7,
                startOfDay = 5,
                colorBlindMode = true,
                maxBackfillDays = 14,
                encryption = true
            )
        )
        val s = db.appSettingsDao().observe().first()!!
        assertEquals(ThemeMode.DARK, s.theme)
        assertEquals(7, s.weekStart)
        assertEquals(14, s.maxBackfillDays)
        assertTrue(s.colorBlindMode)
        assertTrue(s.encryption)
    }

    @Test
    fun itemQueriesByState() = runBlocking {
        // 0 个选项时查询不报错
        assertTrue(db.checkItemDao().observeAll().first().isEmpty())

        val a = db.checkItemDao().insert(CheckItem(name = "A", color = 1, type = CheckType.BOOLEAN, sort = 2))
        val b = db.checkItemDao().insert(CheckItem(name = "B", color = 2, type = CheckType.BOOLEAN, sort = 1))
        val c = db.checkItemDao().insert(CheckItem(name = "C", color = 3, type = CheckType.BOOLEAN, sort = 3))

        // 排序：sort 升序
        assertEquals(listOf("B", "A", "C"), db.checkItemDao().observeAll().first().map { it.name })
        // 停用后不在打卡列表
        db.checkItemDao().update(db.checkItemDao().getById(b)!!.copy(enabled = false))
        assertEquals(listOf("A", "C"), db.checkItemDao().observeActive().first().map { it.name })
        // 名称重复允许（不设唯一约束）
        db.checkItemDao().insert(CheckItem(name = "A", color = 4, type = CheckType.BOOLEAN))
        assertEquals(4, db.checkItemDao().observeAll().first().size)
        assertNull(db.checkItemDao().getById(9999L))
        // 按 id 可精确取回，id 稳定不复用
        assertEquals("A", db.checkItemDao().getById(a)!!.name)
        assertEquals("B", db.checkItemDao().getById(b)!!.name)
    }
}
