package com.gh.daily_trace.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.gh.daily_trace.data.model.DayNote
import kotlinx.coroutines.flow.Flow

@Dao
interface DayNoteDao {

    @Query("SELECT * FROM day_notes WHERE date = :date")
    fun observeByDate(date: Long): Flow<DayNote?>

    @Query("SELECT * FROM day_notes WHERE date BETWEEN :start AND :end ORDER BY date ASC")
    fun observeBetween(start: Long, end: Long): Flow<List<DayNote>>

    @Query("SELECT * FROM day_notes WHERE date = :date")
    suspend fun getByDate(date: Long): DayNote?

    @Query("SELECT * FROM day_notes ORDER BY date ASC")
    suspend fun getAll(): List<DayNote>

    /** 空备注视为无备注，直接删除，避免日历出现空提示 */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(note: DayNote)

    @Query("DELETE FROM day_notes WHERE date = :date")
    suspend fun delete(date: Long)

    @Query("SELECT * FROM day_notes WHERE note LIKE '%' || :query || '%' ORDER BY date DESC")
    suspend fun search(query: String): List<DayNote>

    /** 备份导入：批量写入（先清空后插入） */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(notes: List<DayNote>)

    @Query("DELETE FROM day_notes")
    suspend fun deleteAll()
}
