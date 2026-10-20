package com.gh.daily_trace

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.gh.daily_trace.backup.AutoBackup
import com.gh.daily_trace.backup.BackupCodec
import com.gh.daily_trace.backup.BackupFormatException
import com.gh.daily_trace.backup.BackupManager
import com.gh.daily_trace.data.DailyTraceDatabase
import com.gh.daily_trace.data.model.AppSettings
import com.gh.daily_trace.data.model.CheckItem
import com.gh.daily_trace.data.model.CheckRecord
import com.gh.daily_trace.data.model.CheckType
import com.gh.daily_trace.data.model.DateFormat
import com.gh.daily_trace.data.model.DateLanguage
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
import com.gh.daily_trace.data.repository.DailyTraceRepository
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * M5 备份：真实数据库上的导出/导入/自动备份行为。
 * 覆盖：全表往返一致（含回收站、可空字段、枚举、列表）、导入替换、非法数据拒绝且不破坏现有数据、
 * 自动备份到期判定与保留份数。
 */
@RunWith(AndroidJUnit4::class)
class M5BackupTest {

    private lateinit var db: DailyTraceDatabase
    private lateinit var repo: DailyTraceRepository
    private lateinit var backupManager: BackupManager
    private lateinit var autoBackup: AutoBackup
    private lateinit var context: Context

    private val dayMs = 24L * 60 * 60 * 1000

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        File(context.filesDir, "backups").deleteRecursively()
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
    }

    @After
    fun tearDown() = db.close()

    /** 全表种子数据：覆盖可空字段 null、枚举、列表、回收站、设置全字段 */
    private suspend fun seedAll() {
        val groupId = db.groupDao().insert(Group(name = "健康", color = 0xFF2E7D32.toInt(), sort = 0))
        val item1 = db.checkItemDao().insert(
            CheckItem(
                name = "跑步", color = 0xFF1565C0.toInt(), icon = "🏃", type = CheckType.BOOLEAN,
                defaultNote = "晨跑", groupId = groupId, multiRecordRule = MultiRecordRule.KEEP_MULTIPLE,
            )
        )
        val item2 = db.checkItemDao().insert(
            CheckItem(
                name = "喝水", color = 0xFF00838F.toInt(), type = CheckType.COUNT, unit = "杯",
                targetValue = 8.0, multiRecordRule = MultiRecordRule.ACCUMULATE, deletedAt = 12345L,
            )
        )
        db.checkRecordDao().insertAll(
            listOf(
                CheckRecord(
                    itemId = item1, date = 20743, value = 1.0, status = RecordStatus.DONE,
                    source = RecordSource.NOTIFICATION, isBackfill = true, timezone = "Asia/Shanghai",
                    itemNameSnapshot = "跑步", itemColorSnapshot = 0xFF1565C0.toInt(), itemIconSnapshot = "🏃",
                ),
                CheckRecord(
                    itemId = item2, date = 20742, value = null, unit = "杯", note = "半程",
                    status = RecordStatus.PARTIAL, source = RecordSource.MANUAL,
                    itemNameSnapshot = "喝水", itemColorSnapshot = 0xFF00838F.toInt(), itemIconSnapshot = "",
                ),
            )
        )
        db.dayNoteDao().upsert(DayNote(date = 20742, note = "累了"))
        db.reminderDao().upsert(
            Reminder(
                itemId = item1, time = 510, repeat = RepeatType.WEEKLY, daysOfWeek = listOf(1, 4),
                enabled = false, snooze = 15, dndStart = 1320, dndEnd = 420,
            )
        )
        db.filterViewDao().upsert(
            FilterView(
                name = "运动", mode = FilterMode.SELECTED, itemIds = listOf(item1), groupIds = listOf(groupId),
                logic = FilterLogic.ALL, dayFilter = DayFilter.RECORDED,
            )
        )
        db.frequencyRuleDao().upsert(
            FrequencyRule(
                itemId = item1, type = FrequencyType.CUSTOM, intervalDays = 3, anchorEpochDay = 20700L,
                targetValue = 3.0, allowSkip = false, skipDates = listOf(20700, 20701),
            )
        )
        db.appSettingsDao().upsert(
            AppSettings(
                theme = ThemeMode.DARK, dynamicColor = false, weekStart = 7, startOfDay = 6,
                dateLanguage = DateLanguage.EN, dateFormat = DateFormat.DMY,
                colorBlindMode = true, maxBackfillDays = 30, partialCountsAsDone = false,
                autoBackup = true, autoBackupIntervalDays = 3,
                pausedUntil = 1_700_003_600_000L,
                dailySummaryEnabled = true, dailySummaryTime = 20 * 60 + 30,
            )
        )
    }

    @Test
    fun exportImportRoundTripPreservesAllTables() = runBlocking {
        seedAll()
        val json = backupManager.exportJson(now = 1_700_000_000_000L)

        val itemsBefore = db.checkItemDao().getAllIncludingTrashed()
        val recordsBefore = db.checkRecordDao().getAll().sortedBy { it.id }
        val notesBefore = db.dayNoteDao().getAll()
        val groupsBefore = db.groupDao().getAll()
        val rulesBefore = db.frequencyRuleDao().getAll()
        val viewsBefore = db.filterViewDao().getAll()
        val settingsBefore = db.appSettingsDao().get()!!

        // 破坏当前数据：加一项、改设置
        db.checkItemDao().insert(CheckItem(name = "临时", color = 1, type = CheckType.BOOLEAN))
        db.appSettingsDao().upsert(AppSettings())

        val counts = backupManager.importJson(json)

        assertEquals(2, counts.items)
        assertEquals(2, counts.records)
        assertEquals(1, counts.dayNotes)
        assertEquals(itemsBefore, db.checkItemDao().getAllIncludingTrashed())
        assertEquals(recordsBefore, db.checkRecordDao().getAll().sortedBy { it.id })
        assertEquals(notesBefore, db.dayNoteDao().getAll())
        assertEquals(groupsBefore, db.groupDao().getAll())
        assertEquals(rulesBefore, db.frequencyRuleDao().getAll())
        assertEquals(viewsBefore, db.filterViewDao().getAll())
        assertEquals(settingsBefore, db.appSettingsDao().get())
        val restoredSettings = db.appSettingsDao().get()!!
        assertEquals(DateLanguage.EN, restoredSettings.dateLanguage)
        assertEquals(DateFormat.DMY, restoredSettings.dateFormat)
        val reminder = db.reminderDao().getForItem(itemsBefore.first { it.name == "跑步" }.id)
        assertNotNull(reminder)
        assertEquals(510, reminder!!.time)
        assertEquals(RepeatType.WEEKLY, reminder.repeat)
        assertEquals(listOf(1, 4), reminder.daysOfWeek)
        assertEquals(1320, reminder.dndStart)
        assertEquals(420, reminder.dndEnd)
        assertEquals(false, reminder.enabled)
    }

    @Test
    fun legacyBackupWithoutDateSettingsFallsBackToChineseAuto() = runBlocking {
        seedAll()
        val root = JSONObject(backupManager.exportJson())
        root.getJSONObject("settings").apply {
            remove("dateLanguage")
            remove("dateFormat")
        }

        backupManager.importJson(root.toString())

        val restored = db.appSettingsDao().get()!!
        assertEquals(DateLanguage.ZH, restored.dateLanguage)
        assertEquals(DateFormat.AUTO, restored.dateFormat)
    }

    @Test
    fun importReplacesExistingData() = runBlocking {
        seedAll()
        val json = backupManager.exportJson()
        val itemCountBefore = db.checkItemDao().getAllIncludingTrashed().size

        // 再插入一个新的（不在备份中）
        db.checkItemDao().insert(CheckItem(name = "多余项", color = 2, type = CheckType.BOOLEAN))
        assertEquals(itemCountBefore + 1, db.checkItemDao().getAllIncludingTrashed().size)

        backupManager.importJson(json)
        assertEquals(itemCountBefore, db.checkItemDao().getAllIncludingTrashed().size)
        assertTrue(db.checkItemDao().getAllIncludingTrashed().none { it.name == "多余项" })
    }

    @Test
    fun invalidBackupRejectedWithoutTouchingData() = runBlocking {
        seedAll()
        val itemCount = db.checkItemDao().getAllIncludingTrashed().size

        assertThrows(BackupFormatException::class.java) {
            runBlocking { backupManager.importJson("这不是 JSON") }
        }
        assertThrows(BackupFormatException::class.java) {
            runBlocking { backupManager.importJson("{\"format\":\"other.app\",\"version\":1}") }
        }
        assertThrows(BackupFormatException::class.java) {
            runBlocking { backupManager.importJson("{\"format\":\"${BackupCodec.FORMAT}\",\"version\":99}") }
        }
        assertThrows(BackupFormatException::class.java) {
            // 版本正确但 items 元素缺必需字段（name）
            runBlocking {
                backupManager.importJson(
                    "{\"format\":\"${BackupCodec.FORMAT}\",\"version\":1,\"items\":[{\"id\":1}]}"
                )
            }
        }

        assertEquals(itemCount, db.checkItemDao().getAllIncludingTrashed().size)
        assertEquals(2, db.checkRecordDao().getAll().size)
        assertNotNull(db.appSettingsDao().get())
    }

    @Test
    fun autoBackupIntervalPruneAndContent() = runBlocking {
        seedAll()
        val t0 = 1_700_000_000_000L

        // 种子设置里 autoBackup=true，先关掉以验证「开关关闭：不写」
        repo.saveSettings(repo.getSettings().copy(autoBackup = false))
        assertNull(autoBackup.runIfDue(t0))
        assertTrue(autoBackup.listBackups().isEmpty())

        // 开启（7 天间隔）：首写
        repo.saveSettings(repo.getSettings().copy(autoBackup = true, autoBackupIntervalDays = 7))
        val first = autoBackup.runIfDue(t0)
        assertNotNull(first)
        assertTrue(first!!.exists())

        // 未到期：不再写
        assertNull(autoBackup.runIfDue(t0 + 6 * dayMs))
        assertEquals(1, autoBackup.listBackups().size)

        // 到期：写第二份
        val second = autoBackup.runIfDue(t0 + 7 * dayMs)
        assertNotNull(second)
        assertEquals(2, autoBackup.listBackups().size)

        // 保留上限 5：再写 5 份（共 7）→ 只留最新 5 份
        (1..5).forEach { autoBackup.writeBackup(t0 + 7 * dayMs + it * 1000L) }
        assertEquals(AutoBackup.KEEP_FILES, autoBackup.listBackups().size)
        assertEquals(t0 + 7 * dayMs + 5000L, autoBackup.lastBackupAt())

        // 最新备份内容可解析且与库一致
        val newest = autoBackup.listBackups().first()
        val parsed = BackupCodec.parse(newest.readText())
        assertEquals(
            db.checkRecordDao().getAll().size,
            parsed.records.size,
        )
        assertNotNull(parsed.settings)

        // 手动备份忽略开关与间隔
        repo.saveSettings(repo.getSettings().copy(autoBackup = false))
        assertNotNull(autoBackup.runIfDue(t0, manual = true))
    }
}
