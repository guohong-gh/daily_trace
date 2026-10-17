package com.gh.daily_trace.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.gh.daily_trace.data.model.AppSettings
import kotlinx.coroutines.flow.Flow

@Dao
interface AppSettingsDao {

    /** 单行表，始终返回一条（缺省时为构造默认值） */
    @Query("SELECT * FROM app_settings WHERE id = 1")
    fun observe(): Flow<AppSettings?>

    @Query("SELECT * FROM app_settings WHERE id = 1")
    suspend fun get(): AppSettings?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(settings: AppSettings)
}
