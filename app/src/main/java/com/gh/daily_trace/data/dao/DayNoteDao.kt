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

    /** 时间线「加载更早」：date 之前是否还有备注（存在性判断，不拉数据） */
    @Query("SELECT EXISTS(SELECT 1 FROM day_notes WHERE date < :date)")
    fun observeExistsBefore(date: Long): Flow<Boolean>

    @Query("SELECT * FROM day_notes WHERE date = :date")
    suspend fun getByDate(date: Long): DayNote?

    @Query("SELECT * FROM day_notes ORDER BY date ASC")
    suspend fun getAll(): List<DayNote>

    /** 空备注视为无备注，直接删除，避免日历出现空提示 */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(note: DayNote)

    @Query("DELETE FROM day_notes WHERE date = :date")
    suspend fun delete(date: Long)

    /** 供搜索使用：from/to 为闭区间（null = 不限） */
    @Query(
        "SELECT * FROM day_notes WHERE note LIKE '%' || :query || '%' " +
            "AND (:from IS NULL OR date >= :from) " +
            "AND (:to IS NULL OR date <= :to) " +
            "ORDER BY date DESC"
    )
    suspend fun search(query: String, from: Long?, to: Long?): List<DayNote>

    /** 备份导入：批量写入（先清空后插入） */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(notes: List<DayNote>)

    @Query("DELETE FROM day_notes")
    suspend fun deleteAll()
}
