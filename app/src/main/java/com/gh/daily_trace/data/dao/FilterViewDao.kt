package com.gh.daily_trace.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.gh.daily_trace.data.model.FilterView
import kotlinx.coroutines.flow.Flow

@Dao
interface FilterViewDao {

    @Query("SELECT * FROM filter_views ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<FilterView>>

    @Query("SELECT * FROM filter_views")
    suspend fun getAll(): List<FilterView>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(view: FilterView): Long

    @Update
    suspend fun update(view: FilterView)

    @Query("DELETE FROM filter_views WHERE id = :id")
    suspend fun delete(id: Long)

    /** 备份导入：批量写入（先清空后插入，保留原 id） */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(views: List<FilterView>)

    @Query("DELETE FROM filter_views")
    suspend fun deleteAll()
}
