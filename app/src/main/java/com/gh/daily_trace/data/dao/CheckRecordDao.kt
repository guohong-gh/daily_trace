package com.gh.daily_trace.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.gh.daily_trace.data.model.CheckRecord
import kotlinx.coroutines.flow.Flow

@Dao
interface CheckRecordDao {

    @Query("SELECT * FROM check_records WHERE date = :date ORDER BY createdAt ASC")
    fun observeByDate(date: Long): Flow<List<CheckRecord>>

    @Query("SELECT * FROM check_records WHERE date = :date ORDER BY createdAt ASC")
    suspend fun getFromDate(date: Long): List<CheckRecord>

    @Query("SELECT * FROM check_records WHERE date BETWEEN :start AND :end ORDER BY date ASC, createdAt ASC")
    fun observeBetween(start: Long, end: Long): Flow<List<CheckRecord>>

    /** 时间线「加载更早」：date 之前是否还有记录（存在性判断，不拉数据） */
    @Query("SELECT EXISTS(SELECT 1 FROM check_records WHERE date < :date)")
    fun observeExistsBefore(date: Long): Flow<Boolean>

    @Query("SELECT * FROM check_records WHERE date BETWEEN :start AND :end ORDER BY date ASC, createdAt ASC")
    suspend fun getBetween(start: Long, end: Long): List<CheckRecord>

    @Query("SELECT * FROM check_records WHERE itemId = :itemId ORDER BY date DESC, createdAt DESC")
    fun observeByItem(itemId: Long): Flow<List<CheckRecord>>

    @Query("SELECT * FROM check_records WHERE itemId = :itemId AND date = :date ORDER BY createdAt ASC")
    suspend fun getForItemOnDate(itemId: Long, date: Long): List<CheckRecord>

    @Query("SELECT * FROM check_records ORDER BY date ASC, createdAt ASC")
    fun observeAll(): Flow<List<CheckRecord>>

    @Query("SELECT * FROM check_records ORDER BY date ASC, createdAt ASC")
    suspend fun getAll(): List<CheckRecord>

    @Query("SELECT * FROM check_records WHERE id = :id")
    suspend fun getById(id: Long): CheckRecord?

    /** 供搜索使用：命中备注或快照名称 */
    @Query(
        "SELECT * FROM check_records " +
            "WHERE note LIKE '%' || :query || '%' OR itemNameSnapshot LIKE '%' || :query || '%' " +
            "ORDER BY date DESC, createdAt DESC"
    )
    suspend fun search(query: String): List<CheckRecord>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(record: CheckRecord): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(records: List<CheckRecord>)

    @Update
    suspend fun update(record: CheckRecord)

    @Query("DELETE FROM check_records WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM check_records WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    @Query("DELETE FROM check_records")
    suspend fun deleteAll()
}
