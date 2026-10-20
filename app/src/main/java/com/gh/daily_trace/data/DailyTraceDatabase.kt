package com.gh.daily_trace.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.gh.daily_trace.data.dao.AppSettingsDao
import com.gh.daily_trace.data.dao.CheckItemDao
import com.gh.daily_trace.data.dao.CheckRecordDao
import com.gh.daily_trace.data.dao.DayNoteDao
import com.gh.daily_trace.data.dao.FilterViewDao
import com.gh.daily_trace.data.dao.FrequencyRuleDao
import com.gh.daily_trace.data.dao.GroupDao
import com.gh.daily_trace.data.dao.ReminderDao
import com.gh.daily_trace.data.model.AppSettings
import com.gh.daily_trace.data.model.CheckItem
import com.gh.daily_trace.data.model.CheckRecord
import com.gh.daily_trace.data.model.DayNote
import com.gh.daily_trace.data.model.FilterView
import com.gh.daily_trace.data.model.FrequencyRule
import com.gh.daily_trace.data.model.Group
import com.gh.daily_trace.data.model.Reminder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@Database(
    entities = [
        CheckItem::class,
        CheckRecord::class,
        DayNote::class,
        Group::class,
        Reminder::class,
        FilterView::class,
        FrequencyRule::class,
        AppSettings::class
    ],
    version = 4,
    // 导出 schema 至 app/schemas，后续升级编写显式 Migration，不做隐式破坏性迁移
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class DailyTraceDatabase : RoomDatabase() {

    abstract fun checkItemDao(): CheckItemDao
    abstract fun checkRecordDao(): CheckRecordDao
    abstract fun dayNoteDao(): DayNoteDao
    abstract fun groupDao(): GroupDao
    abstract fun reminderDao(): ReminderDao
    abstract fun filterViewDao(): FilterViewDao
    abstract fun frequencyRuleDao(): FrequencyRuleDao
    abstract fun appSettingsDao(): AppSettingsDao

    companion object {
        @Volatile private var instance: DailyTraceDatabase? = null

        fun get(context: Context): DailyTraceDatabase =
            instance ?: synchronized(this) {
                instance ?: build(context).also { instance = it }
            }

        /**
         * v1 → v2（M7-1 频率规则编辑）：
         * - frequency_rules 增加 intervalDays / anchorEpochDay（CUSTOM「每隔 N 天」）；
         * - reminders 增加 daysOfWeek（每周提醒的星期几独立存储，不再借用频率规则的 daysOfWeek）。
         *   旧数据迁移：此前 WEEKLY 提醒的星期保存在对应频率规则里，直接复制过来。
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE frequency_rules ADD COLUMN intervalDays INTEGER")
                db.execSQL("ALTER TABLE frequency_rules ADD COLUMN anchorEpochDay INTEGER")
                db.execSQL("ALTER TABLE reminders ADD COLUMN daysOfWeek TEXT NOT NULL DEFAULT ''")
                db.execSQL(
                    "UPDATE reminders SET daysOfWeek = COALESCE((" +
                        "SELECT frequency_rules.daysOfWeek FROM frequency_rules " +
                        "WHERE frequency_rules.itemId = reminders.itemId), '') " +
                        "WHERE repeat = 'WEEKLY' AND daysOfWeek = ''"
                )
            }
        }

        /**
         * v2 → v3（M9-2 提醒暂停）：
         * app_settings 增加 pausedUntil（暂停截止毫秒时间戳；null = 未暂停）。
         * 旧数据默认 null，即未暂停。
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE app_settings ADD COLUMN pausedUntil INTEGER")
            }
        }

        /**
         * v3 → v4（M9-3 每日汇总提醒）：
         * app_settings 增加 dailySummaryEnabled（默认关闭）与 dailySummaryTime（默认 21:00 = 1260 分钟）。
         */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE app_settings ADD COLUMN dailySummaryEnabled INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE app_settings ADD COLUMN dailySummaryTime INTEGER NOT NULL DEFAULT 1260")
            }
        }

        private fun build(context: Context): DailyTraceDatabase {
            // onCreate 回调在数据库首次打开（build 返回之后）才触发，届时 db 已初始化
            lateinit var db: DailyTraceDatabase
            db = Room.databaseBuilder(
                context.applicationContext,
                DailyTraceDatabase::class.java,
                "daily_trace.db"
            )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                .addCallback(object : Callback() {
                    override fun onCreate(supportDb: SupportSQLiteDatabase) {
                        super.onCreate(supportDb)
                        // 异步写入，避免与 Room 打开过程互等；Flow 会随后续变更补发
                        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch { seed(db) }
                    }
                })
                .build()
            return db
        }

        /** 种子数据：默认分组（健康/学习/工作/运动）与默认设置；幂等，空库才写入 */
        suspend fun seed(db: DailyTraceDatabase) {
            if (db.groupDao().getAll().isEmpty()) {
                db.groupDao().insertAll(
                    listOf(
                        Group(name = "健康", color = 0xFF2E7D32.toInt(), sort = 0),
                        Group(name = "学习", color = 0xFF1565C0.toInt(), sort = 1),
                        Group(name = "工作", color = 0xFF6A1B9A.toInt(), sort = 2),
                        Group(name = "运动", color = 0xFFEF6C00.toInt(), sort = 3)
                    )
                )
            }
            if (db.appSettingsDao().get() == null) {
                db.appSettingsDao().upsert(AppSettings())
            }
        }
    }
}
