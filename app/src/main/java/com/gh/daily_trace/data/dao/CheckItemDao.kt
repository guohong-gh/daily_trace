package com.gh.daily_trace.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.gh.daily_trace.data.model.CheckItem
import kotlinx.coroutines.flow.Flow

@Dao
interface CheckItemDao {

    /** 全量选项（含归档与回收站），供筛选器、导出使用 */
    @Query("SELECT * FROM check_items ORDER BY sort ASC, id ASC")
    fun observeAll(): Flow<List<CheckItem>>

    /** 打卡用列表：未删除、已启用、未归档 */
    @Query("SELECT * FROM check_items WHERE deletedAt IS NULL AND enabled = 1 AND archived = 0 ORDER BY sort ASC, id ASC")
    fun observeActive(): Flow<List<CheckItem>>

    /** 归档列表 */
    @Query("SELECT * FROM check_items WHERE deletedAt IS NULL AND archived = 1 ORDER BY sort ASC, id ASC")
    fun observeArchived(): Flow<List<CheckItem>>

    /** 回收站 */
    @Query("SELECT * FROM check_items WHERE deletedAt IS NOT NULL ORDER BY deletedAt DESC")
    fun observeTrashed(): Flow<List<CheckItem>>

    @Query("SELECT * FROM check_items WHERE id = :id")
    suspend fun getById(id: Long): CheckItem?

    @Query("SELECT * FROM check_items WHERE id = :id")
    fun observeById(id: Long): Flow<CheckItem?>

    @Query("SELECT * FROM check_items WHERE deletedAt IS NULL ORDER BY sort ASC, id ASC")
    suspend fun getAll(): List<CheckItem>

    /** 全部选项（含回收站），供备份导出使用 */
    @Query("SELECT * FROM check_items ORDER BY sort ASC, id ASC")
    suspend fun getAllIncludingTrashed(): List<CheckItem>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(item: CheckItem): Long

    /** 备份导入：批量写入（先清空后插入，保留原 id） */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<CheckItem>)

    @Query("DELETE FROM check_items")
    suspend fun deleteAll()

    @Update
    suspend fun update(item: CheckItem)

    @Query("DELETE FROM check_items WHERE id = :id")
    suspend fun hardDelete(id: Long)

    /** 归档 / 取消归档 */
    @Query("UPDATE check_items SET archived = :archived, updatedAt = :now WHERE id = :id")
    suspend fun setArchived(id: Long, archived: Boolean, now: Long = System.currentTimeMillis())

    /** 进入回收站（软删除），历史记录与快照保持不动 */
    @Query("UPDATE check_items SET deletedAt = :now, updatedAt = :now WHERE id = :id")
    suspend fun softDelete(id: Long, now: Long = System.currentTimeMillis())

    /** 从回收站恢复 */
    @Query("UPDATE check_items SET deletedAt = NULL, updatedAt = :now WHERE id = :id")
    suspend fun restore(id: Long, now: Long = System.currentTimeMillis())

    @Query("UPDATE check_items SET sort = :sort, updatedAt = :now WHERE id = :id")
    suspend fun setSort(id: Long, sort: Int, now: Long = System.currentTimeMillis())
}
