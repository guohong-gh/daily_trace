package com.gh.daily_trace.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.gh.daily_trace.data.model.Group
import kotlinx.coroutines.flow.Flow

@Dao
interface GroupDao {

    @Query("SELECT * FROM `groups` ORDER BY sort ASC, id ASC")
    fun observeAll(): Flow<List<Group>>

    @Query("SELECT * FROM `groups` ORDER BY sort ASC, id ASC")
    suspend fun getAll(): List<Group>

    @Query("SELECT * FROM `groups` WHERE id = :id")
    suspend fun getById(id: Long): Group?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(group: Group): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(groups: List<Group>)

    @Update
    suspend fun update(group: Group)

    @Query("DELETE FROM `groups` WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM `groups`")
    suspend fun deleteAll()
}
