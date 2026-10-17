package com.gh.daily_trace.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
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
    version = 1,
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

        private fun build(context: Context): DailyTraceDatabase {
            // onCreate 回调在数据库首次打开（build 返回之后）才触发，届时 db 已初始化
            lateinit var db: DailyTraceDatabase
            db = Room.databaseBuilder(
                context.applicationContext,
                DailyTraceDatabase::class.java,
                "daily_trace.db"
            )
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
