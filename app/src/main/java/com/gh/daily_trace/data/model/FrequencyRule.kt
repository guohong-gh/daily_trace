package com.gh.daily_trace.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** 频率与目标规则：每个打卡选项至多一条（itemId 唯一索引） */
@Entity(
    tableName = "frequency_rules",
    indices = [Index(value = ["itemId"], unique = true)]
)
data class FrequencyRule(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val itemId: Long,
    val type: FrequencyType = FrequencyType.DAILY,
    /** type=DAYS_OF_WEEK 时生效：1=周一 … 7=周日（ISO-8601） */
    val daysOfWeek: List<Int> = emptyList(),
    /** type=TIMES_PER_MONTH 时生效 */
    val timesPerMonth: Int? = null,
    /** 目标值，如每周 3 次 / 每天 30 分钟 */
    val targetValue: Double? = null,
    /** 允许跳过（请假/休息日），连续打卡不因跳过中断 */
    val allowSkip: Boolean = true,
    /** 已登记的休息日/请假日（epochDay） */
    val skipDates: List<Long> = emptyList(),
    val updatedAt: Long = System.currentTimeMillis()
)
