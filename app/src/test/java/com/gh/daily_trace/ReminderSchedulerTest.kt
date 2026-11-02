package com.gh.daily_trace

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.gh.daily_trace.data.DailyTraceDatabase
import com.gh.daily_trace.data.model.CheckItem
import com.gh.daily_trace.data.model.CheckType
import com.gh.daily_trace.data.model.FrequencyRule
import com.gh.daily_trace.data.model.FrequencyType
import com.gh.daily_trace.data.model.Reminder
import com.gh.daily_trace.data.model.RepeatType
import com.gh.daily_trace.data.repository.DailyTraceRepository
import com.gh.daily_trace.reminder.ReminderReceiver
import com.gh.daily_trace.reminder.ReminderScheduler
import com.gh.daily_trace.reminder.SnoozeStore
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import java.time.Instant

/**
 * 提醒调度集成测试：内存数据库 + 真实 Repository + Robolectric 的 AlarmManager 影子。
 * now 固定为北京时间 2026-10-17 00:00，验证排定时刻精确到分钟。
 */
@RunWith(AndroidJUnit4::class)
class ReminderSchedulerTest {

    private lateinit var db: DailyTraceDatabase
    private lateinit var repo: DailyTraceRepository
    private lateinit var context: Context
    private lateinit var alarmManager: AlarmManager

    private val now = Instant.parse("2026-10-16T16:00:00Z") // 北京时间 2026-10-17 00:00

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, DailyTraceDatabase::class.java)
            .allowMainThreadQueries().build()
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
        alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        // 清掉可能跨用例残留的「稍后」持久化记录，保证断言只看本用例写入
        SnoozeStore(context).let { store -> store.all().keys.forEach(store::remove) }
    }

    @After
    fun tearDown() = db.close()

    private suspend fun insertItem(): Long =
        repo.insertItem(CheckItem(name = "喝水", color = 1, icon = "水", type = CheckType.BOOLEAN))

    private fun scheduler() = ReminderScheduler(context, repo) { now }

    @Test
    fun `每日提醒排定下一次触发并可被查询`() = runBlocking {
        val itemId = insertItem()
        repo.saveReminder(Reminder(itemId = itemId, time = 8 * 60, repeat = RepeatType.DAILY))

        scheduler().scheduleNext(repo.getReminderOf(itemId)!!)

        val scheduled = shadowOf(alarmManager).peekNextScheduledAlarm()!!
        // 当天 08:00（北京时间）尚未到 → 2026-10-17T08:00+08:00 = 2026-10-17T00:00:00Z
        assertEquals(Instant.parse("2026-10-17T00:00:00Z").toEpochMilli(), scheduled.triggerAtTime)
    }

    @Test
    fun `取消提醒后闹钟被移除`() = runBlocking {
        val itemId = insertItem()
        val reminderId = repo.saveReminder(Reminder(itemId = itemId, time = 8 * 60))

        scheduler().scheduleNext(repo.getReminderOf(itemId)!!)
        scheduler().cancel(reminderId)

        assertNull(shadowOf(alarmManager).peekNextScheduledAlarm())
    }

    @Test
    fun `选项停用后重排会取消提醒`() = runBlocking {
        val itemId = insertItem()
        repo.saveReminder(Reminder(itemId = itemId, time = 8 * 60))
        val item = repo.getItem(itemId)!!

        scheduler().scheduleNext(repo.getReminderOf(itemId)!!)
        repo.updateItem(item.copy(enabled = false))
        scheduler().scheduleNext(repo.getReminderOf(itemId)!!)

        assertNull(shadowOf(alarmManager).peekNextScheduledAlarm())
    }

    @Test
    fun `每周提醒未选天时不排定`() = runBlocking {
        val itemId = insertItem()
        repo.saveReminder(Reminder(itemId = itemId, time = 8 * 60, repeat = RepeatType.WEEKLY))

        scheduler().scheduleNext(repo.getReminderOf(itemId)!!)

        assertNull(shadowOf(alarmManager).peekNextScheduledAlarm())
    }

    @Test
    fun `每周提醒按提醒自身的周几跳到下周一`() = runBlocking {
        val itemId = insertItem()
        repo.saveReminder(
            Reminder(itemId = itemId, time = 8 * 60, repeat = RepeatType.WEEKLY, daysOfWeek = listOf(1))
        )
        // 频率规则设为周五：验证提醒不再读取频率规则的星期（两字段相互独立）
        repo.saveFrequency(
            FrequencyRule(itemId = itemId, type = FrequencyType.DAYS_OF_WEEK, daysOfWeek = listOf(5))
        )

        scheduler().scheduleNext(repo.getReminderOf(itemId)!!)

        val scheduled = shadowOf(alarmManager).peekNextScheduledAlarm()!!
        // 今天（10-17 周六）与周日不匹配 → 2026-10-19（周一）08:00 +08:00 = 10-19T00:00:00Z
        assertEquals(Instant.parse("2026-10-19T00:00:00Z").toEpochMilli(), scheduled.triggerAtTime)
    }

    @Test
    fun `稍后提醒按 snooze 分钟排定一次性闹钟`() = runBlocking {
        val itemId = insertItem()
        repo.saveReminder(Reminder(itemId = itemId, time = 8 * 60, snooze = 15))

        scheduler().scheduleSnooze(repo.getReminderOf(itemId)!!)

        val scheduled = shadowOf(alarmManager).peekNextScheduledAlarm()!!
        assertEquals(now.toEpochMilli() + 15 * 60_000L, scheduled.triggerAtTime)
    }

    @Test
    fun `暂停期间重排不排定提醒`() = runBlocking {
        val itemId = insertItem()
        repo.saveReminder(Reminder(itemId = itemId, time = 8 * 60))
        repo.saveSettings(repo.getSettings().copy(pausedUntil = now.toEpochMilli() + 3_600_000L))

        scheduler().scheduleNext(repo.getReminderOf(itemId)!!)

        assertNull(shadowOf(alarmManager).peekNextScheduledAlarm())
    }

    @Test
    fun `暂停期间稍后提醒不排定`() = runBlocking {
        val itemId = insertItem()
        repo.saveReminder(Reminder(itemId = itemId, time = 8 * 60, snooze = 15))
        repo.saveSettings(repo.getSettings().copy(pausedUntil = now.toEpochMilli() + 3_600_000L))

        scheduler().scheduleSnooze(repo.getReminderOf(itemId)!!)

        assertNull(shadowOf(alarmManager).peekNextScheduledAlarm())
    }

    @Test
    fun `暂停撤下提醒闹钟并安排恢复闹钟`() = runBlocking {
        val itemId = insertItem()
        repo.saveReminder(Reminder(itemId = itemId, time = 8 * 60))
        val scheduler = scheduler()
        scheduler.scheduleNext(repo.getReminderOf(itemId)!!)

        val until = now.toEpochMilli() + 3_600_000L
        scheduler.pauseUntil(until)

        val paused = shadowOf(alarmManager).peekNextScheduledAlarm()!!
        assertEquals(until, paused.triggerAtTime)
    }

    @Test
    fun `恢复后重排下一次提醒`() = runBlocking {
        val itemId = insertItem()
        repo.saveReminder(Reminder(itemId = itemId, time = 8 * 60))
        val scheduler = scheduler()
        scheduler.scheduleNext(repo.getReminderOf(itemId)!!)
        scheduler.pauseUntil(now.toEpochMilli() + 3_600_000L)

        // 与 SettingsViewModel.resumeReminders 顺序一致：先清暂停值，再重排
        repo.saveSettings(repo.getSettings().copy(pausedUntil = null))
        scheduler.resume()

        val resumed = shadowOf(alarmManager).peekNextScheduledAlarm()!!
        assertEquals(Instant.parse("2026-10-17T00:00:00Z").toEpochMilli(), resumed.triggerAtTime)
    }

    @Test
    fun `稍后提醒写入持久化记录且取消后清除`() = runBlocking {
        val itemId = insertItem()
        val reminderId = repo.saveReminder(Reminder(itemId = itemId, time = 8 * 60, snooze = 15))
        val scheduler = scheduler()
        val store = SnoozeStore(context)

        scheduler.scheduleSnooze(repo.getReminderOf(itemId)!!)
        assertEquals(mapOf(reminderId to (itemId to now.toEpochMilli() + 15 * 60_000L)), store.all())

        scheduler.cancel(reminderId)
        assertEquals(emptyMap<Long, Pair<Long, Long>>(), store.all())
    }

    @Test
    fun `重启清空闹钟后未到期的稍后闹钟被恢复`() = runBlocking {
        val itemId = insertItem()
        val reminderId = repo.saveReminder(Reminder(itemId = itemId, time = 8 * 60, snooze = 15))
        val scheduler = scheduler()
        scheduler.scheduleSnooze(repo.getReminderOf(itemId)!!)
        val expected = now.toEpochMilli() + 15 * 60_000L

        // 模拟设备重启：一次性闹钟被系统清空（PendingIntent 相等性只看 action/class/requestCode）
        alarmManager.cancel(
            PendingIntent.getBroadcast(
                context,
                reminderId.toInt(),
                Intent(context, ReminderReceiver::class.java).setAction(ReminderReceiver.ACTION_FIRE_SNOOZE),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        )
        scheduler.rescheduleAll()

        assertEquals(expected, shadowOf(alarmManager).peekNextScheduledAlarm()!!.triggerAtTime)
    }

    @Test
    fun `过期的稍后记录在重排时被清除`() = runBlocking {
        val itemId = insertItem()
        val reminderId = repo.saveReminder(Reminder(itemId = itemId, time = 8 * 60, snooze = 15))
        SnoozeStore(context).put(reminderId, itemId, now.toEpochMilli() - 1_000L)

        scheduler().rescheduleAll()

        assertEquals(emptyMap<Long, Pair<Long, Long>>(), SnoozeStore(context).all())
    }

    @Test
    fun `暂停期间重排不恢复稍后闹钟`() = runBlocking {
        val itemId = insertItem()
        val reminderId = repo.saveReminder(Reminder(itemId = itemId, time = 8 * 60, snooze = 15))
        SnoozeStore(context).put(reminderId, itemId, now.toEpochMilli() + 15 * 60_000L)
        repo.saveSettings(repo.getSettings().copy(pausedUntil = now.toEpochMilli() + 3_600_000L))

        scheduler().rescheduleAll()

        assertNull(shadowOf(alarmManager).peekNextScheduledAlarm())
        assertEquals(emptyMap<Long, Pair<Long, Long>>(), SnoozeStore(context).all())
    }
}
