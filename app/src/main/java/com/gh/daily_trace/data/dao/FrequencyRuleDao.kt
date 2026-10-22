package com.gh.daily_trace.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.gh.daily_trace.data.model.FrequencyRule
import kotlinx.coroutines.flow.Flow

@Dao
interface FrequencyRuleDao {

    @Query("SELECT * FROM frequency_rules")
    fun observeAll(): Flow<List<FrequencyRule>>

    @Query("SELECT * FROM frequency_rules WHERE itemId = :itemId")
    fun observeForItem(itemId: Long): Flow<FrequencyRule?>

    @Query("SELECT * FROM frequency_rules WHERE itemId = :itemId")
    suspend fun getForItem(itemId: Long): FrequencyRule?

    @Query("SELECT * FROM frequency_rules")
    suspend fun getAll(): List<FrequencyRule>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(rule: FrequencyRule): Long

    @Update
    suspend fun update(rule: FrequencyRule)

    @Query("DELETE FROM frequency_rules WHERE itemId = :itemId")
    suspend fun deleteForItem(itemId: Long)

    /** 数据修复：删除选项已不存在的孤立频率规则 */
    @Query("DELETE FROM frequency_rules WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    /** 备份导入：批量写入（先清空后插入，保留原 id） */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(rules: List<FrequencyRule>)

    @Query("DELETE FROM frequency_rules")
    suspend fun deleteAll()
}
