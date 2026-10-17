package com.gh.daily_trace.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.gh.daily_trace.data.model.Reminder
import kotlinx.coroutines.flow.Flow

@Dao
interface ReminderDao {

    @Query("SELECT * FROM reminders ORDER BY time ASC")
    fun observeAll(): Flow<List<Reminder>>

    @Query("SELECT * FROM reminders WHERE itemId = :itemId")
    fun observeForItem(itemId: Long): Flow<Reminder?>

    @Query("SELECT * FROM reminders WHERE itemId = :itemId")
    suspend fun getForItem(itemId: Long): Reminder?

    @Query("SELECT * FROM reminders WHERE enabled = 1")
    suspend fun getEnabled(): List<Reminder>

    @Query("SELECT * FROM reminders")
    suspend fun getAll(): List<Reminder>

    /** 插入即覆盖同选项旧规则（itemId 唯一索引） */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(reminder: Reminder): Long

    @Update
    suspend fun update(reminder: Reminder)

    @Query("UPDATE reminders SET enabled = :enabled, updatedAt = :now WHERE itemId = :itemId")
    suspend fun setEnabled(itemId: Long, enabled: Boolean, now: Long = System.currentTimeMillis())

    @Query("DELETE FROM reminders WHERE itemId = :itemId")
    suspend fun deleteForItem(itemId: Long)

    /** 备份导入：批量写入（先清空后插入，保留原 id） */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(reminders: List<Reminder>)

    @Query("DELETE FROM reminders")
    suspend fun deleteAll()
}
