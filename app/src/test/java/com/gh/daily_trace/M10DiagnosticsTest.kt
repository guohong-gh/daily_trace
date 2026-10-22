package com.gh.daily_trace

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.gh.daily_trace.backup.AutoBackup
import com.gh.daily_trace.backup.BackupManager
import com.gh.daily_trace.data.DailyTraceDatabase
import com.gh.daily_trace.data.model.AppSettings
import com.gh.daily_trace.data.model.CheckItem
import com.gh.daily_trace.data.model.CheckRecord
import com.gh.daily_trace.data.model.CheckType
import com.gh.daily_trace.data.model.DayNote
import com.gh.daily_trace.data.model.FilterMode
import com.gh.daily_trace.data.model.FilterView
import com.gh.daily_trace.data.model.FrequencyRule
import com.gh.daily_trace.data.model.FrequencyType
import com.gh.daily_trace.data.model.Group
import com.gh.daily_trace.data.model.RecordSource
import com.gh.daily_trace.data.model.RecordStatus
import com.gh.daily_trace.data.model.Reminder
import com.gh.daily_trace.data.repository.DailyTraceRepository
import com.gh.daily_trace.diagnostics.CrashLogManager
import com.gh.daily_trace.diagnostics.DataRepairManager
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * M10-7：崩溃日志写入 / 导出 / 保留上限，以及数据检查与安全修复、最近备份恢复。
 */
@RunWith(AndroidJUnit4::class)
class M10DiagnosticsTest {

    private lateinit var context: Context
    private lateinit var db: DailyTraceDatabase
    private lateinit var repo: DailyTraceRepository
    private lateinit var backupManager: BackupManager
    private lateinit var autoBackup: AutoBackup
    private lateinit var repair: DataRepairManager

    private val fixedNow = 1_800_000_000_000L

    private data class SeedIds(
        val groupId: Long,
        val itemValid: Long,
        val itemInvalidGroup: Long,
        val itemTrashed: Long,
    )

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        File(context.filesDir, "backups").deleteRecursively()
        File(context.filesDir, "crash_logs").deleteRecursively()
        db = Room.inMemoryDatabaseBuilder(context, DailyTraceDatabase::class.java)
            .allowMainThreadQueries()
            .build()
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
        backupManager = BackupManager(db)
        autoBackup = AutoBackup(context, backupManager, repo)
        repair = DataRepairManager(db, backupManager, autoBackup) { fixedNow }
    }

    @After
    fun tearDown() {
        db.close()
    }

    private suspend fun seedIssues(): SeedIds {
        val groupId = db.groupDao().insert(Group(name = "健康", color = 0xFF2E7D32.toInt(), sort = 0))
        val itemValid = db.checkItemDao().insert(
            CheckItem(name = "跑步", color = 0xFF1565C0.toInt(), type = CheckType.BOOLEAN, groupId = groupId)
        )
        val itemInvalidGroup = db.checkItemDao().insert(
            CheckItem(name = "喝水", color = 0xFF00838F.toInt(), type = CheckType.COUNT, groupId = 999L)
        )
        val itemTrashed = db.checkItemDao().insert(
            CheckItem(
                name = "旧项",
                color = 0xFF8D6E63.toInt(),
                type = CheckType.BOOLEAN,
                groupId = 888L,
                deletedAt = 12345L,
            )
        )
        db.checkRecordDao().insert(
            CheckRecord(
                itemId = itemValid, date = 20743, status = RecordStatus.DONE, source = RecordSource.MANUAL,
                itemNameSnapshot = "跑步", itemColorSnapshot = 0xFF1565C0.toInt(), itemIconSnapshot = "🏃",
            )
        )
        db.checkRecordDao().insert(
            CheckRecord(
                itemId = 9999L, date = 20743, status = RecordStatus.DONE, source = RecordSource.MANUAL,
                itemNameSnapshot = "已删选项", itemColorSnapshot = 0, itemIconSnapshot = "",
            )
        )
        db.frequencyRuleDao().upsert(FrequencyRule(itemId = itemValid, type = FrequencyType.DAILY))
        db.frequencyRuleDao().upsert(FrequencyRule(itemId = 9999L, type = FrequencyType.DAILY))
        db.reminderDao().upsert(Reminder(itemId = itemValid, time = 510, snooze = 10))
        db.reminderDao().upsert(Reminder(itemId = 9999L, time = 600, snooze = 10))
        db.dayNoteDao().upsert(DayNote(date = 20742, note = "   "))
        db.dayNoteDao().upsert(DayNote(date = 20743, note = "正常备注"))
        db.filterViewDao().upsert(
            FilterView(
                name = "运动",
                mode = FilterMode.SELECTED,
                itemIds = listOf(itemValid, 9999L),
                groupIds = listOf(groupId, 9999L),
            )
        )
        db.appSettingsDao().upsert(
            AppSettings(
                weekStart = 9,
                startOfDay = 99,
                timezone = "Bad/Zone",
                maxBackfillDays = -5,
                autoBackupIntervalDays = 0,
                dailySummaryTime = 2000,
            )
        )
        return SeedIds(groupId = groupId, itemValid = itemValid, itemInvalidGroup = itemInvalidGroup, itemTrashed = itemTrashed)
    }

    @Test
    fun scanReportsIssuesWithoutChangingData() = runBlocking {
        seedIssues()

        val health = repair.scan()

        assertEquals(3, health.items)
        assertEquals(2, health.activeItems)
        assertEquals(2, health.records)
        assertEquals(2, health.notes)
        assertEquals(1, health.groups)
        assertEquals(2, health.rules)
        assertEquals(2, health.reminders)
        assertEquals(1, health.filterViews)
        assertEquals(1, health.orphanRecords)
        assertEquals(1, health.orphanRules)
        assertEquals(1, health.orphanReminders)
        assertEquals(2, health.invalidGroupRefs)
        assertEquals(1, health.emptyNotes)
        assertEquals(1, health.staleFilterViews)
        assertFalse(health.settingsMissing)
        assertEquals(1, health.settingsIssues)
        assertTrue(health.integrityOk)
        assertEquals(7, health.fixableIssues)
        assertEquals(8, health.totalIssues)

        // 只读扫描：数据保持原样
        assertEquals(2, db.checkRecordDao().getAll().size)
        assertEquals(2, db.frequencyRuleDao().getAll().size)
        assertEquals("Bad/Zone", db.appSettingsDao().get()?.timezone)
    }

    @Test
    fun repairFixesReferencesAndSettingsButKeepsHistory() = runBlocking {
        val ids = seedIssues()

        val result = repair.repair()

        assertEquals(1, result.deletedRules)
        assertEquals(1, result.deletedReminders)
        assertEquals(2, result.clearedGroupRefs)
        assertEquals(1, result.deletedNotes)
        assertEquals(1, result.cleanedFilters)
        assertFalse(result.settingsReset)
        assertTrue(result.settingsNormalized)
        assertEquals(1, result.orphanRecordsKept)

        val items = db.checkItemDao().getAllIncludingTrashed().associateBy { it.id }
        assertNull(items.getValue(ids.itemInvalidGroup).groupId)
        assertNull(items.getValue(ids.itemTrashed).groupId)
        assertEquals(ids.groupId, items.getValue(ids.itemValid).groupId)

        assertEquals(listOf(ids.itemValid), db.frequencyRuleDao().getAll().map { it.itemId })
        assertEquals(listOf(ids.itemValid), db.reminderDao().getAll().map { it.itemId })

        assertNull(db.dayNoteDao().getByDate(20742))
        assertNotNull(db.dayNoteDao().getByDate(20743))

        val view = db.filterViewDao().getAll().single()
        assertEquals(listOf(ids.itemValid), view.itemIds)
        assertEquals(listOf(ids.groupId), view.groupIds)

        val settings = db.appSettingsDao().get()!!
        assertEquals(7, settings.weekStart)
        assertEquals(23, settings.startOfDay)
        assertEquals("Asia/Shanghai", settings.timezone)
        assertEquals(0, settings.maxBackfillDays)
        assertEquals(1, settings.autoBackupIntervalDays)
        assertEquals(24 * 60 - 1, settings.dailySummaryTime)

        // 选项彻底删除后的历史记录保留
        assertEquals(2, db.checkRecordDao().getAll().size)

        val after = repair.scan()
        assertEquals(0, after.fixableIssues)
        assertEquals(1, after.orphanRecords)
        assertTrue(after.integrityOk)
    }

    @Test
    fun missingSettingsAreRebuilt() = runBlocking {
        val health = repair.scan()
        assertTrue(health.settingsMissing)
        assertEquals(1, health.fixableIssues)

        val result = repair.repair()
        assertTrue(result.settingsReset)
        assertEquals(AppSettings(), db.appSettingsDao().get())
    }

    @Test
    fun restoreLatestBackupReplacesCurrentData() = runBlocking {
        val itemId = db.checkItemDao().insert(
            CheckItem(name = "跑步", color = 1, type = CheckType.BOOLEAN)
        )
        db.checkRecordDao().insert(
            CheckRecord(
                itemId = itemId, date = 20743, status = RecordStatus.DONE, source = RecordSource.MANUAL,
                itemNameSnapshot = "跑步", itemColorSnapshot = 1, itemIconSnapshot = "",
            )
        )
        autoBackup.writeBackup(now = fixedNow)

        // 备份之后新增数据：恢复应回到备份时刻
        db.checkItemDao().insert(CheckItem(name = "临时", color = 2, type = CheckType.BOOLEAN))
        db.checkRecordDao().deleteAll()

        val counts = repair.restoreLatestBackup()

        assertNotNull(counts)
        assertEquals(1, counts!!.items)
        assertEquals(1, counts.records)
        assertEquals(1, db.checkItemDao().getAllIncludingTrashed().size)
        assertEquals(1, db.checkRecordDao().getAll().size)
    }

    @Test
    fun restoreLatestBackupWithoutBackupReturnsNull() = runBlocking {
        assertNull(repair.restoreLatestBackup())
    }

    @Test
    fun crashLogRecordsExportsAndClears() {
        val manager = CrashLogManager(context)
        assertTrue(manager.list().isEmpty())
        assertEquals("", manager.exportText())

        val file = manager.record(Thread.currentThread(), IllegalStateException("测试崩溃"), now = fixedNow)

        assertNotNull(file)
        assertEquals(1, manager.list().size)
        val text = manager.exportText()
        assertTrue(text.contains("Daily Trace 崩溃日志"))
        assertTrue(text.contains("测试崩溃"))
        assertTrue(text.contains("线程: ${Thread.currentThread().name}"))

        assertEquals(1, manager.clear())
        assertTrue(manager.list().isEmpty())
    }

    @Test
    fun crashLogsArePrunedToMaxFiles() {
        val manager = CrashLogManager(context)
        repeat(CrashLogManager.MAX_FILES + 2) { index ->
            manager.record(Thread.currentThread(), IllegalStateException("崩溃 $index"), now = fixedNow + index)
        }

        assertEquals(CrashLogManager.MAX_FILES, manager.list().size)
    }
}
