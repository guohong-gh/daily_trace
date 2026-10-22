package com.gh.daily_trace.di

import android.content.Context
import com.gh.daily_trace.backup.AutoBackup
import com.gh.daily_trace.backup.BackupManager
import com.gh.daily_trace.data.DailyTraceDatabase
import com.gh.daily_trace.data.repository.DailyTraceRepository
import com.gh.daily_trace.diagnostics.CrashLogManager
import com.gh.daily_trace.diagnostics.DataRepairManager
import com.gh.daily_trace.reminder.DailySummaryScheduler
import com.gh.daily_trace.reminder.ReminderScheduler

/** 手动依赖注入容器，随 Application 生命周期存活 */
class Container(context: Context) {
    private val database = DailyTraceDatabase.get(context)
    private val appContext = context.applicationContext

    val repository: DailyTraceRepository = DailyTraceRepository(
        checkItemDao = database.checkItemDao(),
        checkRecordDao = database.checkRecordDao(),
        dayNoteDao = database.dayNoteDao(),
        groupDao = database.groupDao(),
        reminderDao = database.reminderDao(),
        filterViewDao = database.filterViewDao(),
        frequencyRuleDao = database.frequencyRuleDao(),
        appSettingsDao = database.appSettingsDao()
    )

    val reminderScheduler = ReminderScheduler(appContext, repository)
    val dailySummaryScheduler = DailySummaryScheduler(appContext)

    val backupManager = BackupManager(database)
    val autoBackup = AutoBackup(appContext, backupManager, repository)
    val dataRepair = DataRepairManager(database, backupManager, autoBackup)
    val crashLogs = CrashLogManager(appContext)
}
